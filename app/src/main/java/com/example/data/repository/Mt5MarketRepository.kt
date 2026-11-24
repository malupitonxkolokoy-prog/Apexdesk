package com.example.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.data.local.ApexCacheDatabase
import com.example.data.local.CandleEntity
import com.example.data.local.SecureTokenVault
import com.example.data.model.Candle
import com.example.data.model.MarketSymbol
import com.example.data.model.Mt5AccountDoc
import com.example.data.model.OpenPosition
import com.example.data.model.OrderDraft
import com.example.data.model.OrderType
import com.example.data.model.PendingOrder
import com.example.data.model.Timeframe
import com.example.data.remote.BridgeTradeRequestDto
import com.example.data.remote.Mt5BridgeClientFactory
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

data class CandleLoadResult(
    val candles: List<Candle>,
    val fromOfflineCache: Boolean,
    val sourceLabel: String
)

class Mt5MarketRepository(
    context: Context,
    private val tokenVault: SecureTokenVault
) {
    private val appContext = context.applicationContext
    private val candleDao = ApexCacheDatabase.getInstance(appContext).candleDao()

    fun isNetworkConnected(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun loadCandles(
        symbol: MarketSymbol,
        timeframe: Timeframe,
        activeAccount: Mt5AccountDoc?
    ): CandleLoadResult {
        val online = isNetworkConnected()
        val bridgeUrl = activeAccount?.bridgeUrl?.takeIf { it.isNotBlank() }
        val token = activeAccount?.id?.let { tokenVault.readToken(it) }

        if (online && bridgeUrl != null && !token.isNullOrBlank()) {
            val api = Mt5BridgeClientFactory.create(bridgeUrl)
            if (api != null) {
                try {
                    val response = api.getHistoricalCandles(
                        token = token,
                        accountId = activeAccount.accountLogin,
                        symbol = symbol.ticker,
                        timeframe = timeframe.label,
                        limit = 120
                    )
                    if (response.isSuccessful) {
                        val body = response.body().orEmpty()
                        if (body.isNotEmpty()) {
                            val candles = body.map { dto ->
                                Candle(
                                    timestampMs = dto.time,
                                    open = dto.open,
                                    high = dto.high,
                                    low = dto.low,
                                    close = dto.close,
                                    volume = dto.tickVolume
                                )
                            }
                            cacheCandles(symbol, timeframe, candles)
                            return CandleLoadResult(
                                candles = candles,
                                fromOfflineCache = false,
                                sourceLabel = "MT5 Bridge Live Feed"
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Bridge candle fetch failed, falling back to cache/sandbox: ${e.message}")
                }
            }
        }

        val cached = candleDao.getCandles(symbol.ticker, timeframe.label)
        if (!online && cached.isNotEmpty()) {
            return CandleLoadResult(
                candles = cached.map { it.toDomain() },
                fromOfflineCache = true,
                sourceLabel = "Offline Room Cache"
            )
        }

        val series = if (cached.size >= 80) {
            cached.map { it.toDomain() }
        } else {
            val generated = generateStructuredOhlcv(symbol, timeframe, count = 110)
            cacheCandles(symbol, timeframe, generated)
            generated
        }

        return CandleLoadResult(
            candles = series,
            fromOfflineCache = !online,
            sourceLabel = if (online) "MT5 Demo Sandbox Feed" else "Offline Room Cache"
        )
    }

    suspend fun cacheCandles(symbol: MarketSymbol, timeframe: Timeframe, candles: List<Candle>) {
        val now = System.currentTimeMillis()
        candleDao.upsertCandles(
            candles.map { CandleEntity.fromDomain(symbol.ticker, timeframe.label, it, now) }
        )
    }

    suspend fun executeOrderViaBridgeOrSandbox(
        draft: OrderDraft,
        activeAccount: Mt5AccountDoc
    ): Result<Pair<OpenPosition?, PendingOrder?>> {
        val bridgeUrl = activeAccount.bridgeUrl?.takeIf { it.isNotBlank() }
        val token = tokenVault.readToken(activeAccount.id)

        if (bridgeUrl != null && !token.isNullOrBlank()) {
            val api = Mt5BridgeClientFactory.create(bridgeUrl)
                ?: return Result.failure(IllegalArgumentException("Invalid or insecure MT5 Bridge URL (HTTPS required)."))
            return try {
                val actionType = when (draft.orderType) {
                    OrderType.BUY_MARKET -> "ORDER_TYPE_BUY"
                    OrderType.SELL_MARKET -> "ORDER_TYPE_SELL"
                    OrderType.BUY_LIMIT -> "ORDER_TYPE_BUY_LIMIT"
                    OrderType.SELL_LIMIT -> "ORDER_TYPE_SELL_LIMIT"
                    OrderType.BUY_STOP -> "ORDER_TYPE_BUY_STOP"
                    OrderType.SELL_STOP -> "ORDER_TYPE_SELL_STOP"
                }
                val res = api.executeTrade(
                    token = token,
                    accountId = activeAccount.accountLogin,
                    request = BridgeTradeRequestDto(
                        actionType = actionType,
                        symbol = draft.symbol.ticker,
                        volume = draft.lots,
                        openPrice = if (draft.orderType.isPending) draft.entryPrice else null,
                        stopLoss = draft.stopLoss.takeIf { it > 0.0 },
                        takeProfit = draft.takeProfit.takeIf { it > 0.0 },
                        trailingStopPips = draft.trailingStopPips.takeIf { it > 0 }
                    )
                )
                if (!res.isSuccessful) {
                    return Result.failure(IllegalStateException("Bridge rejected order (${res.code()})."))
                }
                val body = res.body()
                val ticket = body?.positionId?.ifBlank { body.orderId } ?: "MT5-${System.currentTimeMillis() % 100000}"
                buildLocalOrderRecord(draft, activeAccount.accountType, ticket)
            } catch (e: Exception) {
                Result.failure(Exception("MT5 Bridge execution error: ${e.localizedMessage}"))
            }
        }

        // Demo Sandbox execution
        val ticket = "MT5-${(100000..999999).random()}"
        return buildLocalOrderRecord(draft, activeAccount.accountType, ticket)
    }

    private fun buildLocalOrderRecord(
        draft: OrderDraft,
        accountType: String,
        ticket: String
    ): Result<Pair<OpenPosition?, PendingOrder?>> {
        val now = System.currentTimeMillis()
        return if (!draft.orderType.isPending) {
            val direction = if (draft.orderType == OrderType.BUY_MARKET) "BUY" else "SELL"
            Result.success(
                OpenPosition(
                    ticketId = ticket,
                    symbol = draft.symbol.ticker,
                    direction = direction,
                    lots = draft.lots,
                    openPrice = draft.entryPrice,
                    currentPrice = draft.entryPrice,
                    stopLoss = draft.stopLoss,
                    takeProfit = draft.takeProfit,
                    trailingStopPips = draft.trailingStopPips,
                    floatingPnl = -draft.symbol.spreadPips * draft.symbol.pipSize * draft.symbol.contractSize * draft.lots * 0.2,
                    openedAtMs = now,
                    accountType = accountType
                ) to null
            )
        } else {
            Result.success(
                null to PendingOrder(
                    orderId = ticket,
                    symbol = draft.symbol.ticker,
                    orderType = draft.orderType,
                    lots = draft.lots,
                    triggerPrice = draft.entryPrice,
                    stopLoss = draft.stopLoss,
                    takeProfit = draft.takeProfit,
                    trailingStopPips = draft.trailingStopPips,
                    createdAtMs = now,
                    accountType = accountType
                )
            )
        }
    }

    fun stepLatestCandle(symbol: MarketSymbol, timeframe: Timeframe, current: List<Candle>): List<Candle> {
        if (current.isEmpty()) return current
        val last = current.last()
        val volScale = symbol.pipSize * when (timeframe) {
            Timeframe.M1 -> 3.0
            Timeframe.M5 -> 5.0
            Timeframe.M15 -> 8.0
            Timeframe.H1 -> 14.0
            Timeframe.H4 -> 24.0
            Timeframe.D1 -> 42.0
        }
        val delta = (Random.nextDouble(-1.0, 1.05)) * volScale * 0.35
        val newClose = roundDigits(max(symbol.pipSize, last.close + delta), symbol.digits)
        val newHigh = roundDigits(max(last.high, newClose + abs(delta) * 0.25), symbol.digits)
        val newLow = roundDigits(minOf(last.low, newClose - abs(delta) * 0.25), symbol.digits)
        val updatedLast = last.copy(
            high = newHigh,
            low = newLow,
            close = newClose,
            volume = last.volume + Random.nextInt(12, 85)
        )
        return current.dropLast(1) + updatedLast
    }

    private fun generateStructuredOhlcv(
        symbol: MarketSymbol,
        timeframe: Timeframe,
        count: Int
    ): List<Candle> {
        val tfMs = timeframe.minutes * 60_000L
        val now = System.currentTimeMillis()
        val stepSize = symbol.pipSize * when (timeframe) {
            Timeframe.M1 -> 4.0
            Timeframe.M5 -> 7.0
            Timeframe.M15 -> 12.0
            Timeframe.H1 -> 20.0
            Timeframe.H4 -> 34.0
            Timeframe.D1 -> 58.0
        }
        val rng = Random(symbol.ordinal * 997 + timeframe.minutes)
        var price = symbol.defaultPrice - stepSize * 8.0
        val candles = ArrayList<Candle>(count)

        for (i in 0 until count) {
            val ts = now - (count - i) * tfMs
            val wave = sin(i * 0.16) * stepSize * 0.55 + cos(i * 0.05) * stepSize * 0.45
            val drift = (rng.nextDouble(-0.9, 1.05)) * stepSize * 0.75 + wave * 0.25
            val open = roundDigits(price, symbol.digits)
            val close = roundDigits(max(symbol.pipSize, open + drift), symbol.digits)
            val wickUp = abs(rng.nextDouble(0.15, 0.85)) * stepSize * 0.65
            val wickDown = abs(rng.nextDouble(0.15, 0.85)) * stepSize * 0.65
            val high = roundDigits(max(open, close) + wickUp, symbol.digits)
            val low = roundDigits(max(symbol.pipSize, minOf(open, close) - wickDown), symbol.digits)
            val volume = (450 + rng.nextInt(120, 1400) + abs(close - open) / symbol.pipSize * 45).toDouble()
            candles.add(Candle(ts, open, high, low, close, volume))
            price = close
        }
        return candles
    }

    private fun roundDigits(value: Double, digits: Int): Double {
        var factor = 1.0
        repeat(digits) { factor *= 10.0 }
        return (value * factor).roundToInt() / factor
    }

    private companion object {
        const val TAG = "Mt5MarketRepository"
    }
}
