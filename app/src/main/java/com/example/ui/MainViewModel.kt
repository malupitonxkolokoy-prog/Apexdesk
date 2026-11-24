package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.SecureTokenVault
import com.example.data.model.AiTradeIdea
import com.example.data.model.AuditLogDoc
import com.example.data.model.Candle
import com.example.data.model.InAppAlert
import com.example.data.model.IndicatorSettings
import com.example.data.model.JournalEntryDoc
import com.example.data.model.MarketSymbol
import com.example.data.model.Mt5AccountDoc
import com.example.data.model.OpenPosition
import com.example.data.model.OrderDraft
import com.example.data.model.OrderType
import com.example.data.model.PendingOrder
import com.example.data.model.RiskEvaluation
import com.example.data.model.RiskSettingsDoc
import com.example.data.model.TechnicalSnapshot
import com.example.data.model.Timeframe
import com.example.data.model.TradeAnalytics
import com.example.data.remote.GeminiChartAnalyzer
import com.example.data.remote.Mt5BridgeClientFactory
import com.example.data.repository.FirestoreTradingRepository
import com.example.data.repository.Mt5MarketRepository
import com.example.domain.RiskEngine
import com.example.domain.TechnicalAnalysisEngine
import com.example.security.TradingNotificationHelper
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

sealed interface CloudUiState<out T> {
    data object Loading : CloudUiState<Nothing>
    data class Success<T>(val data: T) : CloudUiState<T>
    data class Error(val message: String) : CloudUiState<Nothing>
}

sealed interface AiAnalysisUiState {
    data object Idle : AiAnalysisUiState
    data object Loading : AiAnalysisUiState
    data class Success(val idea: AiTradeIdea) : AiAnalysisUiState
    data class Error(val message: String) : AiAnalysisUiState
}

data class SensitiveVerificationRequirement(
    val required: Boolean,
    val reasons: List<String>
)

data class ChartWorkspaceState(
    val symbol: MarketSymbol = MarketSymbol.EURUSD,
    val timeframe: Timeframe = Timeframe.H1,
    val candles: List<Candle> = emptyList(),
    val snapshot: TechnicalSnapshot = TechnicalAnalysisEngine.analyze(emptyList()),
    val indicators: IndicatorSettings = IndicatorSettings(),
    val isLoading: Boolean = true,
    val fromOfflineCache: Boolean = false,
    val sourceLabel: String = "MT5 Demo Sandbox Feed"
)

class MainViewModel(
    private val currentUserId: String,
    private val firestoreRepo: FirestoreTradingRepository,
    private val marketRepo: Mt5MarketRepository,
    private val aiAnalyzer: GeminiChartAnalyzer,
    private val tokenVault: SecureTokenVault,
    private val notificationHelper: TradingNotificationHelper
) : ViewModel() {

    val accountsState: StateFlow<CloudUiState<List<Mt5AccountDoc>>> =
        firestoreRepo.observeMt5Accounts(currentUserId).toCloudState("MT5 accounts")

    val riskSettingsState: StateFlow<CloudUiState<RiskSettingsDoc>> =
        firestoreRepo.observeRiskSettings(currentUserId)
            .map<RiskSettingsDoc?, CloudUiState<RiskSettingsDoc>> {
                CloudUiState.Success(it ?: RiskSettingsDoc(userId = currentUserId))
            }
            .catch { error -> emit(unwrapFirestoreError(error, "risk settings")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), CloudUiState.Loading)

    val journalState: StateFlow<CloudUiState<List<JournalEntryDoc>>> =
        firestoreRepo.observeJournalEntries(currentUserId).toCloudState("journal entries")

    val auditLogsState: StateFlow<CloudUiState<List<AuditLogDoc>>> =
        firestoreRepo.observeAuditLogs(currentUserId).toCloudState("audit logs")

    private val _chartState = MutableStateFlow(ChartWorkspaceState())
    val chartState: StateFlow<ChartWorkspaceState> = _chartState.asStateFlow()

    private val _aiState = MutableStateFlow<AiAnalysisUiState>(AiAnalysisUiState.Idle)
    val aiState: StateFlow<AiAnalysisUiState> = _aiState.asStateFlow()

    private val _openPositions = MutableStateFlow<List<OpenPosition>>(emptyList())
    val openPositions: StateFlow<List<OpenPosition>> = _openPositions.asStateFlow()

    private val _pendingOrders = MutableStateFlow<List<PendingOrder>>(emptyList())
    val pendingOrders: StateFlow<List<PendingOrder>> = _pendingOrders.asStateFlow()

    private val _symbolQuotes = MutableStateFlow(
        MarketSymbol.entries.associateWith { it.defaultPrice }
    )
    val symbolQuotes: StateFlow<Map<MarketSymbol, Double>> = _symbolQuotes.asStateFlow()

    private val _alerts = MutableStateFlow<List<InAppAlert>>(emptyList())
    val alerts: StateFlow<List<InAppAlert>> = _alerts.asStateFlow()

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    private val _appEntryBiometricEnabled = MutableStateFlow(tokenVault.isAppEntryBiometricEnabled())
    val appEntryBiometricEnabled: StateFlow<Boolean> = _appEntryBiometricEnabled.asStateFlow()

    private val _autoLockOnBackgroundEnabled = MutableStateFlow(tokenVault.isAutoLockOnBackgroundEnabled())
    val autoLockOnBackgroundEnabled: StateFlow<Boolean> = _autoLockOnBackgroundEnabled.asStateFlow()

    private val _sensitiveTradeBiometricEnabled = MutableStateFlow(tokenVault.isSensitiveTradeBiometricEnabled())
    val sensitiveTradeBiometricEnabled: StateFlow<Boolean> = _sensitiveTradeBiometricEnabled.asStateFlow()

    private val _isSessionLocked = MutableStateFlow(tokenVault.isAppEntryBiometricEnabled())
    val isSessionLocked: StateFlow<Boolean> = _isSessionLocked.asStateFlow()

    private var backgroundedAtMs: Long = 0L
    private var marginWarningSent = false

    val activeAccount: StateFlow<Mt5AccountDoc> = accountsState
        .map { state ->
            when (state) {
                is CloudUiState.Success -> {
                    state.data.firstOrNull { it.active }
                        ?: state.data.firstOrNull()
                        ?: defaultDemoAccount()
                }
                else -> defaultDemoAccount()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), defaultDemoAccount())

    val currentRiskSettings: StateFlow<RiskSettingsDoc> = riskSettingsState
        .map { state ->
            when (state) {
                is CloudUiState.Success -> state.data
                else -> RiskSettingsDoc(userId = currentUserId)
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000L),
            RiskSettingsDoc(userId = currentUserId)
        )

    val analytics: StateFlow<TradeAnalytics> = journalState
        .map { state ->
            val entries = (state as? CloudUiState.Success)?.data.orEmpty()
                .filter { it.status == "CLOSED" }
            if (entries.isEmpty()) {
                TradeAnalytics(0, 0.0, 0.0, 0.0, 0.0, 0.0, listOf(25000.0))
            } else {
                val wins = entries.filter { it.pnlAmount > 0 }
                val losses = entries.filter { it.pnlAmount < 0 }
                val grossWin = wins.sumOf { it.pnlAmount }
                val grossLoss = abs(losses.sumOf { it.pnlAmount })
                val net = entries.sumOf { it.pnlAmount }
                val winRate = (wins.size.toDouble() / entries.size) * 100.0
                val pf = if (grossLoss > 0.0) grossWin / grossLoss else if (grossWin > 0.0) 9.99 else 0.0
                var running = activeAccount.value.balance - net
                val curve = mutableListOf(running)
                entries.reversed().forEach {
                    running += it.pnlAmount
                    curve.add(running)
                }
                TradeAnalytics(
                    totalTrades = entries.size,
                    winRatePct = winRate,
                    profitFactor = pf,
                    netPnl = net,
                    averageWin = if (wins.isNotEmpty()) grossWin / wins.size else 0.0,
                    averageLoss = if (losses.isNotEmpty()) grossLoss / losses.size else 0.0,
                    equityCurvePoints = curve
                )
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000L),
            TradeAnalytics(0, 0.0, 0.0, 0.0, 0.0, 0.0, listOf(25000.0))
        )

    init {
        viewModelScope.launch {
            firestoreRepo.ensureDefaultAccountAndSettings(currentUserId)
            loadChartSeries(MarketSymbol.EURUSD, Timeframe.H1)
            addAlert(
                category = "SYSTEM",
                title = "ApexDesk Terminal Ready",
                message = "Encrypted token vault & pre-trade risk engine initialized.",
                notifyOs = false
            )
        }
        startLiveTickEngine()
    }

    private fun startLiveTickEngine() {
        viewModelScope.launch {
            while (isActive) {
                delay(1800L)
                val currentChart = _chartState.value
                if (currentChart.candles.isNotEmpty() && !currentChart.fromOfflineCache) {
                    val stepped = marketRepo.stepLatestCandle(
                        currentChart.symbol,
                        currentChart.timeframe,
                        currentChart.candles
                    )
                    val latestPrice = stepped.last().close
                    _symbolQuotes.update { map ->
                        map.toMutableMap().apply {
                            this[currentChart.symbol] = latestPrice
                            MarketSymbol.entries.forEach { sym ->
                                if (sym != currentChart.symbol) {
                                    val prev = this[sym] ?: sym.defaultPrice
                                    val delta = (Math.random() - 0.495) * sym.pipSize * 1.4
                                    this[sym] = (prev + delta).coerceAtLeast(sym.pipSize)
                                }
                            }
                        }
                    }
                    _chartState.update {
                        it.copy(
                            candles = stepped,
                            snapshot = TechnicalAnalysisEngine.analyze(stepped)
                        )
                    }
                    updateOpenPositionsOnTick()
                }
            }
        }
    }

    private suspend fun updateOpenPositionsOnTick() {
        val quotes = _symbolQuotes.value
        val positions = _openPositions.value
        if (positions.isEmpty()) return

        val toClose = mutableListOf<Pair<OpenPosition, String>>()
        val updated = positions.map { pos ->
            val sym = MarketSymbol.entries.firstOrNull { it.ticker == pos.symbol } ?: MarketSymbol.EURUSD
            val price = quotes[sym] ?: pos.currentPrice
            val priceDiff = if (pos.direction == "BUY") price - pos.openPrice else pos.openPrice - price
            val pnl = when (sym) {
                MarketSymbol.USDJPY -> (priceDiff / price) * sym.contractSize * pos.lots
                else -> priceDiff * sym.contractSize * pos.lots
            }
            var adjustedSl = pos.stopLoss
            if (pos.trailingStopPips > 0) {
                val trailDist = pos.trailingStopPips * sym.pipSize
                if (pos.direction == "BUY" && price - trailDist > pos.stopLoss && price > pos.openPrice) {
                    adjustedSl = price - trailDist
                } else if (pos.direction == "SELL" && (pos.stopLoss == 0.0 || price + trailDist < pos.stopLoss) && price < pos.openPrice) {
                    adjustedSl = price + trailDist
                }
            }

            if (adjustedSl > 0.0) {
                val hitSl = if (pos.direction == "BUY") price <= adjustedSl else price >= adjustedSl
                if (hitSl) toClose.add(pos.copy(currentPrice = price, floatingPnl = pnl) to "Stop-Loss Hit")
            }
            if (pos.takeProfit > 0.0) {
                val hitTp = if (pos.direction == "BUY") price >= pos.takeProfit else price <= pos.takeProfit
                if (hitTp) toClose.add(pos.copy(currentPrice = price, floatingPnl = pnl) to "Take-Profit Hit")
            }
            pos.copy(currentPrice = price, stopLoss = adjustedSl, floatingPnl = pnl)
        }

        val closingIds = toClose.map { it.first.ticketId }.toSet()
        _openPositions.value = updated.filterNot { it.ticketId in closingIds }

        toClose.forEach { (pos, reason) ->
            closePositionInternal(pos, reason)
        }

        val acc = activeAccount.value
        val floating = _openPositions.value.sumOf { it.floatingPnl }
        val equity = (acc.balance + floating).coerceAtLeast(0.0)
        val marginLevel = if (acc.marginUsed > 0.0) (equity / acc.marginUsed) * 100.0 else 999.0
        if (acc.marginUsed > 0.0 && marginLevel < 120.0 && !marginWarningSent) {
            marginWarningSent = true
            addAlert(
                category = "MARGIN",
                title = "Margin Call Warning",
                message = "Account margin level dropped to ${String.format(Locale.US, "%.1f", marginLevel)}%.",
                notifyOs = true,
                isCritical = true
            )
        } else if (marginLevel >= 150.0) {
            marginWarningSent = false
        }
    }

    fun selectSymbol(symbol: MarketSymbol) {
        viewModelScope.launch {
            _aiState.value = AiAnalysisUiState.Idle
            loadChartSeries(symbol, _chartState.value.timeframe)
        }
    }

    fun selectTimeframe(timeframe: Timeframe) {
        viewModelScope.launch {
            _aiState.value = AiAnalysisUiState.Idle
            loadChartSeries(_chartState.value.symbol, timeframe)
        }
    }

    fun toggleIndicator(transform: (IndicatorSettings) -> IndicatorSettings) {
        _chartState.update { it.copy(indicators = transform(it.indicators)) }
    }

    private suspend fun loadChartSeries(symbol: MarketSymbol, timeframe: Timeframe) {
        _chartState.update { it.copy(symbol = symbol, timeframe = timeframe, isLoading = true) }
        val result = marketRepo.loadCandles(symbol, timeframe, activeAccount.value)
        if (result.candles.isNotEmpty()) {
            _symbolQuotes.update { it + (symbol to result.candles.last().close) }
        }
        _chartState.update {
            it.copy(
                candles = result.candles,
                snapshot = TechnicalAnalysisEngine.analyze(result.candles),
                isLoading = false,
                fromOfflineCache = result.fromOfflineCache,
                sourceLabel = result.sourceLabel
            )
        }
    }

    fun refreshChart() {
        viewModelScope.launch {
            loadChartSeries(_chartState.value.symbol, _chartState.value.timeframe)
        }
    }

    fun runAiAnalysis() {
        val state = _chartState.value
        val minRr = currentRiskSettings.value.minRiskRewardRatio
        viewModelScope.launch {
            _aiState.value = AiAnalysisUiState.Loading
            val result = aiAnalyzer.analyzeChart(
                symbol = state.symbol,
                timeframe = state.timeframe,
                candles = state.candles,
                snapshot = state.snapshot,
                minRiskReward = minRr
            )
            result.onSuccess { idea ->
                _aiState.value = AiAnalysisUiState.Success(idea)
                addAlert(
                    category = "AI_SIGNAL",
                    title = "AI Confluence: ${idea.symbol} ${idea.timeframe}",
                    message = "${idea.bias} bias (${idea.confidencePercent}% confidence, ${String.format(Locale.US, "%.1f", idea.riskRewardRatio)}:1 R:R)",
                    notifyOs = true
                )
                firestoreRepo.appendAuditLog(
                    eventType = "AI_SIGNAL",
                    symbol = idea.symbol,
                    accountType = activeAccount.value.accountType,
                    summary = "Gemini analyzed ${idea.symbol} ${idea.timeframe}: ${idea.bias} (${idea.confidencePercent}%)",
                    metadataSummary = "Entry ${idea.suggestedEntry}, SL ${idea.stopLoss}, TP ${idea.takeProfit}, R:R ${idea.riskRewardRatio}"
                )
            }.onFailure { err ->
                _aiState.value = AiAnalysisUiState.Error(
                    err.localizedMessage ?: "AI analysis unavailable."
                )
            }
        }
    }

    fun evaluateOrderDraft(draft: OrderDraft): RiskEvaluation {
        val todayPnl = (journalState.value as? CloudUiState.Success)?.data
            ?.filter { it.status == "CLOSED" }
            ?.take(10)
            ?.sumOf { it.pnlAmount } ?: 0.0
        val floating = _openPositions.value.sumOf { it.floatingPnl }
        return RiskEngine.evaluateOrder(
            draft = draft,
            account = activeAccount.value,
            settings = currentRiskSettings.value,
            openPositionsCount = _openPositions.value.size,
            todayRealizedPnl = todayPnl,
            floatingPnl = floating
        )
    }

    fun recommendLotSize(symbol: MarketSymbol, entry: Double, stopLoss: Double): Double {
        return RiskEngine.calculateRecommendedLots(
            symbol = symbol,
            accountBalance = activeAccount.value.balance,
            riskPercent = currentRiskSettings.value.maxRiskPerTradePct,
            entryPrice = entry,
            stopLossPrice = stopLoss
        )
    }

    fun evaluateSensitiveTradeRequirement(
        draft: OrderDraft,
        eval: RiskEvaluation = evaluateOrderDraft(draft)
    ): SensitiveVerificationRequirement {
        val acc = activeAccount.value
        val settings = currentRiskSettings.value
        val reasons = mutableListOf<String>()

        if (acc.accountType == "LIVE" && settings.requireBiometricForLive) {
            reasons.add("LIVE Account Execution")
        }
        if (_sensitiveTradeBiometricEnabled.value) {
            reasons.add("Sensitive Trade Guard Active")
        }
        if (draft.lots >= 0.50) {
            reasons.add("High Volume (${String.format(Locale.US, "%.2f", draft.lots)} lots)")
        }
        if (eval.riskPercent >= 1.5) {
            reasons.add("Elevated Risk (${String.format(Locale.US, "%.1f", eval.riskPercent)}%)")
        }

        return SensitiveVerificationRequirement(
            required = reasons.isNotEmpty(),
            reasons = reasons
        )
    }

    fun executeOrder(
        draft: OrderDraft,
        biometricVerified: Boolean,
        twoFactorCode: String,
        onComplete: (Result<String>) -> Unit
    ) {
        viewModelScope.launch {
            val acc = activeAccount.value
            val settings = currentRiskSettings.value
            val eval = evaluateOrderDraft(draft)

            if (!eval.approved) {
                val reason = eval.blockers.firstOrNull() ?: "Blocked by pre-trade risk rules."
                firestoreRepo.appendAuditLog(
                    eventType = "RISK_CHECK_BLOCKED",
                    symbol = draft.symbol.ticker,
                    accountType = acc.accountType,
                    summary = "Order blocked: $reason",
                    metadataSummary = "${draft.orderType.label} ${draft.lots} lots @ ${draft.entryPrice}",
                    biometricVerified = biometricVerified
                )
                onComplete(Result.failure(IllegalStateException(reason)))
                return@launch
            }

            val sensitiveReq = evaluateSensitiveTradeRequirement(draft, eval)
            val twoFactorValid = tokenVault.verifyTwoFactorCode(twoFactorCode)

            if (sensitiveReq.required && !biometricVerified && !twoFactorValid) {
                val reasonSummary = sensitiveReq.reasons.joinToString(", ")
                firestoreRepo.appendAuditLog(
                    eventType = "RISK_CHECK_BLOCKED",
                    symbol = draft.symbol.ticker,
                    accountType = acc.accountType,
                    summary = "Execution halted: Biometric or 2FA PIN verification required",
                    metadataSummary = reasonSummary,
                    biometricVerified = false
                )
                onComplete(Result.failure(SecurityException("Verification required ($reasonSummary). Confirm via BiometricPrompt or 2FA PIN.")))
                return@launch
            }

            if (acc.accountType == "LIVE" && settings.requireTwoFactorForLive && !twoFactorValid) {
                onComplete(Result.failure(IllegalArgumentException("Invalid 6-digit 2FA verification code.")))
                return@launch
            }

            val execResult = marketRepo.executeOrderViaBridgeOrSandbox(draft, acc)
            execResult.onSuccess { (pos, pending) ->
                if (pos != null) {
                    _openPositions.update { listOf(pos) + it }
                    val newMargin = acc.marginUsed + eval.estimatedMarginUsd
                    firestoreRepo.updateAccountMetrics(acc.id, acc.balance, acc.equity, newMargin)
                    firestoreRepo.addJournalEntry(
                        JournalEntryDoc(
                            userId = currentUserId,
                            symbol = pos.symbol,
                            direction = pos.direction,
                            accountType = acc.accountType,
                            entryPrice = pos.openPrice,
                            exitPrice = 0.0,
                            stopLoss = pos.stopLoss,
                            takeProfit = pos.takeProfit,
                            lotSize = pos.lots,
                            pnlAmount = 0.0,
                            setupTag = draft.setupTag,
                            notes = "Opened via ApexDesk ${draft.orderType.label}",
                            emotion = "Disciplined",
                            status = "OPEN"
                        ),
                        customDocId = pos.ticketId
                    )
                    firestoreRepo.appendAuditLog(
                        eventType = "ORDER_EXECUTED",
                        symbol = pos.symbol,
                        accountType = acc.accountType,
                        summary = "${pos.direction} ${pos.lots} ${pos.symbol} @ ${pos.openPrice}",
                        metadataSummary = "SL ${pos.stopLoss}, TP ${pos.takeProfit}, Risk ${String.format(Locale.US, "%.2f", eval.riskPercent)}%",
                        biometricVerified = biometricVerified
                    )
                    addAlert(
                        category = "EXECUTION",
                        title = "Order Filled: ${pos.symbol}",
                        message = "${pos.direction} ${pos.lots} lots @ ${pos.openPrice} (${acc.accountType})",
                        notifyOs = true
                    )
                    onComplete(Result.success("Position ${pos.ticketId} filled."))
                } else if (pending != null) {
                    _pendingOrders.update { listOf(pending) + it }
                    firestoreRepo.appendAuditLog(
                        eventType = "ORDER_CONFIRMED",
                        symbol = pending.symbol,
                        accountType = acc.accountType,
                        summary = "Pending ${pending.orderType.label} ${pending.lots} ${pending.symbol} @ ${pending.triggerPrice}",
                        metadataSummary = "SL ${pending.stopLoss}, TP ${pending.takeProfit}",
                        biometricVerified = biometricVerified
                    )
                    addAlert(
                        category = "EXECUTION",
                        title = "Pending Order Placed",
                        message = "${pending.orderType.label} ${pending.lots} ${pending.symbol} @ ${pending.triggerPrice}",
                        notifyOs = true
                    )
                    onComplete(Result.success("Pending order ${pending.orderId} queued."))
                }
            }.onFailure { err ->
                onComplete(Result.failure(err))
            }
        }
    }

    fun closePosition(position: OpenPosition, biometricVerified: Boolean = false) {
        viewModelScope.launch {
            _openPositions.update { list -> list.filterNot { it.ticketId == position.ticketId } }
            closePositionInternal(position, "Manual Close", biometricVerified)
        }
    }

    private suspend fun closePositionInternal(
        position: OpenPosition,
        reason: String,
        biometricVerified: Boolean = false
    ) {
        val acc = activeAccount.value
        val newBalance = (acc.balance + position.floatingPnl).coerceAtLeast(0.0)
        val remainingMargin = (_openPositions.value.size * 120.0).coerceAtMost(acc.marginUsed)
        firestoreRepo.updateAccountMetrics(acc.id, newBalance, newBalance, remainingMargin)
        firestoreRepo.closeJournalEntry(position.ticketId, position.currentPrice, position.floatingPnl)
        firestoreRepo.appendAuditLog(
            eventType = "ORDER_CLOSED",
            symbol = position.symbol,
            accountType = acc.accountType,
            summary = "Closed ${position.direction} ${position.lots} ${position.symbol} ($reason)",
            metadataSummary = "Exit ${position.currentPrice}, PnL ${String.format(Locale.US, "%.2f", position.floatingPnl)}",
            biometricVerified = biometricVerified
        )
        addAlert(
            category = "EXECUTION",
            title = "Position Closed ($reason)",
            message = "${position.symbol} ${position.direction} P&L: ${String.format(Locale.US, "%.2f", position.floatingPnl)} ${acc.currency}",
            notifyOs = true,
            isCritical = position.floatingPnl < 0
        )
    }

    fun cancelPendingOrder(order: PendingOrder, biometricVerified: Boolean = false) {
        _pendingOrders.update { list -> list.filterNot { it.orderId == order.orderId } }
        viewModelScope.launch {
            firestoreRepo.appendAuditLog(
                eventType = "ORDER_CLOSED",
                symbol = order.symbol,
                accountType = order.accountType,
                summary = "Cancelled pending ${order.orderType.label} ${order.symbol}",
                metadataSummary = "Order ${order.orderId}",
                biometricVerified = biometricVerified
            )
        }
    }

    fun linkMt5Account(
        label: String,
        brokerName: String,
        accountLogin: String,
        accountType: String,
        bridgeUrl: String,
        apiToken: String,
        balance: Double,
        leverage: Long,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val cleanedBridge = bridgeUrl.trim()
            if (cleanedBridge.isNotEmpty() && !Mt5BridgeClientFactory.isSafeHttpsBridgeUrl(cleanedBridge)) {
                onResult(Result.failure(IllegalArgumentException("Bridge URL must be a public HTTPS endpoint.")))
                return@launch
            }
            val newDoc = Mt5AccountDoc(
                userId = currentUserId,
                label = label.ifBlank { "$brokerName $accountType" },
                brokerName = brokerName.ifBlank { "MetaTrader 5 Broker" },
                accountLogin = accountLogin.trim(),
                accountType = accountType,
                bridgeUrl = cleanedBridge.ifBlank { null },
                currency = "USD",
                balance = balance.coerceAtLeast(100.0),
                equity = balance.coerceAtLeast(100.0),
                marginUsed = 0.0,
                leverage = leverage.coerceIn(1L, 1000L),
                active = true
            )
            val saveRes = firestoreRepo.saveMt5Account(newDoc)
            saveRes.onSuccess { docId ->
                if (apiToken.isNotBlank()) {
                    tokenVault.storeToken(docId, apiToken.trim())
                }
                val currentAccounts = (accountsState.value as? CloudUiState.Success)?.data.orEmpty()
                firestoreRepo.setActiveMt5Account(docId, currentAccounts + newDoc.copy(id = docId))
                firestoreRepo.appendAuditLog(
                    eventType = "ACCOUNT_LINKED",
                    symbol = "MT5",
                    accountType = accountType,
                    summary = "Linked $accountType MT5 account ${newDoc.accountLogin} (${newDoc.brokerName})",
                    metadataSummary = if (apiToken.isNotBlank()) "Token encrypted in Keystore" else "Demo Sandbox",
                    biometricVerified = true
                )
                onResult(Result.success(Unit))
            }.onFailure { err ->
                onResult(Result.failure(err))
            }
        }
    }

    fun switchActiveAccount(account: Mt5AccountDoc) {
        viewModelScope.launch {
            val currentAccounts = (accountsState.value as? CloudUiState.Success)?.data.orEmpty()
            firestoreRepo.setActiveMt5Account(account.id, currentAccounts)
            _bannerMessage.value = "Switched to ${account.label} (${account.accountType})"
        }
    }

    fun updateRiskSettings(
        updated: RiskSettingsDoc,
        biometricVerified: Boolean = false,
        onSaved: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val res = firestoreRepo.saveRiskSettings(updated.copy(userId = currentUserId))
            res.onSuccess {
                firestoreRepo.appendAuditLog(
                    eventType = "RISK_UPDATED",
                    symbol = "SYSTEM",
                    accountType = activeAccount.value.accountType,
                    summary = "Updated risk limits: ${updated.maxRiskPerTradePct}%/trade, ${updated.maxDailyLossPct}% daily, max ${updated.maxOpenPositions} pos",
                    metadataSummary = "SL required=${updated.requireStopLoss}, Bio=${updated.requireBiometricForLive}",
                    biometricVerified = biometricVerified
                )
                _bannerMessage.value = "Risk & safety rules saved."
                onSaved(true)
            }.onFailure {
                _bannerMessage.value = it.localizedMessage ?: "Failed to save risk rules."
                onSaved(false)
            }
        }
    }

    fun acceptDisclaimer() {
        val current = currentRiskSettings.value
        if (current.disclaimerAccepted) return
        viewModelScope.launch {
            firestoreRepo.saveRiskSettings(current.copy(userId = currentUserId, disclaimerAccepted = true))
            firestoreRepo.appendAuditLog(
                eventType = "DISCLAIMER_ACCEPTED",
                symbol = "SYSTEM",
                accountType = "SYSTEM",
                summary = "User acknowledged substantial risk & non-advisory disclosures."
            )
        }
    }

    fun updateJournalNotes(entryId: String, setupTag: String, notes: String, emotion: String) {
        viewModelScope.launch {
            firestoreRepo.updateJournalNotes(entryId, setupTag, notes, emotion)
            _bannerMessage.value = "Journal entry updated."
        }
    }

    fun addManualJournalEntry(
        symbol: MarketSymbol,
        direction: String,
        entryPrice: Double,
        exitPrice: Double,
        lots: Double,
        pnl: Double,
        setupTag: String,
        notes: String,
        emotion: String
    ) {
        viewModelScope.launch {
            firestoreRepo.addJournalEntry(
                JournalEntryDoc(
                    userId = currentUserId,
                    symbol = symbol.ticker,
                    direction = direction,
                    accountType = activeAccount.value.accountType,
                    entryPrice = entryPrice,
                    exitPrice = exitPrice,
                    stopLoss = 0.0,
                    takeProfit = 0.0,
                    lotSize = lots,
                    pnlAmount = pnl,
                    setupTag = setupTag,
                    notes = notes,
                    emotion = emotion,
                    status = "CLOSED"
                )
            )
            _bannerMessage.value = "Trade logged to cloud journal."
        }
    }

    fun setSessionLocked(locked: Boolean) {
        _isSessionLocked.value = locked
        if (!locked) {
            backgroundedAtMs = 0L
        }
    }

    fun onAppBackgrounded() {
        if (_appEntryBiometricEnabled.value && _autoLockOnBackgroundEnabled.value && !_isSessionLocked.value) {
            backgroundedAtMs = System.currentTimeMillis()
        }
    }

    fun onAppForegrounded() {
        val bgAt = backgroundedAtMs
        backgroundedAtMs = 0L
        if (_appEntryBiometricEnabled.value &&
            _autoLockOnBackgroundEnabled.value &&
            bgAt > 0L &&
            System.currentTimeMillis() - bgAt >= BACKGROUND_LOCK_GRACE_MS
        ) {
            _isSessionLocked.value = true
        }
    }

    fun unlockSessionWithBiometric() {
        _isSessionLocked.value = false
        backgroundedAtMs = 0L
        addAlert(
            category = "SECURITY",
            title = "Terminal Entry Verified",
            message = "Session unlocked via BiometricPrompt.",
            notifyOs = false
        )
    }

    fun unlockSessionWithPin(pin: String): Boolean {
        val valid = tokenVault.verifyTwoFactorCode(pin)
        if (valid) {
            _isSessionLocked.value = false
            backgroundedAtMs = 0L
            addAlert(
                category = "SECURITY",
                title = "Terminal Entry Verified",
                message = "Session unlocked via 2FA Vault PIN fallback.",
                notifyOs = false
            )
        }
        return valid
    }

    fun setAppEntryBiometricEnabled(enabled: Boolean) {
        _appEntryBiometricEnabled.value = enabled
        tokenVault.setAppEntryBiometricEnabled(enabled)
        _bannerMessage.value = if (enabled) {
            "App entry BiometricPrompt lock enabled."
        } else {
            "App entry lock disabled."
        }
    }

    fun setAutoLockOnBackgroundEnabled(enabled: Boolean) {
        _autoLockOnBackgroundEnabled.value = enabled
        tokenVault.setAutoLockOnBackgroundEnabled(enabled)
    }

    fun setSensitiveTradeBiometricEnabled(enabled: Boolean) {
        _sensitiveTradeBiometricEnabled.value = enabled
        tokenVault.setSensitiveTradeBiometricEnabled(enabled)
    }

    fun verifyTwoFactorCode(code: String): Boolean = tokenVault.verifyTwoFactorCode(code)

    fun clearBanner() {
        _bannerMessage.value = null
    }

    fun getTwoFactorPin(): String = tokenVault.getOrCreateTwoFactorCodeSeed()

    fun updateTwoFactorPin(pin: String): Boolean = tokenVault.updateTwoFactorPin(pin)

    fun hasEncryptedToken(accountId: String): Boolean = tokenVault.hasToken(accountId)

    private fun addAlert(
        category: String,
        title: String,
        message: String,
        notifyOs: Boolean,
        isCritical: Boolean = false
    ) {
        val alert = InAppAlert(
            id = "${System.currentTimeMillis()}_${(100..999).random()}",
            category = category,
            title = title,
            message = message,
            timestampMs = System.currentTimeMillis(),
            isCritical = isCritical
        )
        _alerts.update { (listOf(alert) + it).take(25) }
        if (notifyOs) {
            notificationHelper.sendNotification(title, message, isCritical)
        }
    }

    private fun <T> Flow<List<T>>.toCloudState(label: String): StateFlow<CloudUiState<List<T>>> {
        return this
            .map<List<T>, CloudUiState<List<T>>> { CloudUiState.Success(it) }
            .catch { error -> emit(unwrapFirestoreError(error, label)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), CloudUiState.Loading)
    }

    private fun unwrapFirestoreError(error: Throwable, label: String): CloudUiState.Error {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing $label", rootError)
        return CloudUiState.Error(rootError.localizedMessage ?: "Failed to load $label")
    }

    private fun defaultDemoAccount() = Mt5AccountDoc(
        id = "demo_primary",
        userId = currentUserId,
        label = "MT5 Demo Sandbox",
        brokerName = "ApexDesk Demo Server",
        accountLogin = "50842910",
        accountType = "DEMO",
        currency = "USD",
        balance = 25000.0,
        equity = 25000.0,
        marginUsed = 0.0,
        leverage = 100L,
        active = true
    )

    private companion object {
        const val TAG = "MainViewModel"
        const val BACKGROUND_LOCK_GRACE_MS = 15_000L
    }
}
