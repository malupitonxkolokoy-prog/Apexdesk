package com.example.data.model

enum class Timeframe(val label: String, val minutes: Int) {
    M1("M1", 1),
    M5("M5", 5),
    M15("M15", 15),
    H1("H1", 60),
    H4("H4", 240),
    D1("D1", 1440)
}

enum class MarketSymbol(
    val ticker: String,
    val displayName: String,
    val digits: Int,
    val pipSize: Double,
    val contractSize: Double,
    val defaultPrice: Double,
    val spreadPips: Double
) {
    EURUSD("EURUSD", "Euro / US Dollar", 5, 0.0001, 100_000.0, 1.08420, 0.8),
    GBPUSD("GBPUSD", "British Pound / USD", 5, 0.0001, 100_000.0, 1.26740, 1.1),
    XAUUSD("XAUUSD", "Gold / US Dollar", 2, 0.1, 100.0, 2648.40, 2.2),
    USDJPY("USDJPY", "US Dollar / Yen", 3, 0.01, 100_000.0, 154.320, 1.0),
    BTCUSD("BTCUSD", "Bitcoin / US Dollar", 2, 1.0, 1.0, 91_450.00, 18.0),
    US500("US500", "S&P 500 Index CFD", 2, 0.1, 1.0, 5_982.50, 0.6)
}

data class Candle(
    val timestampMs: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
) {
    val isBullish: Boolean get() = close >= open
}

data class IndicatorSettings(
    val showEma20: Boolean = true,
    val showEma50: Boolean = true,
    val showBollinger: Boolean = false,
    val showFibonacci: Boolean = false,
    val showSupportResistance: Boolean = true,
    val showRsi: Boolean = true,
    val showMacd: Boolean = false,
    val showVolume: Boolean = true
)

data class FibonacciLevels(
    val swingHigh: Double,
    val swingLow: Double,
    val isUptrend: Boolean,
    val levels: Map<String, Double>
)

data class PriceZone(
    val price: Double,
    val isResistance: Boolean,
    val touches: Int
)

data class CandlestickPattern(
    val name: String,
    val bias: String,
    val candleIndex: Int,
    val description: String
)

data class TechnicalSnapshot(
    val ema20: List<Double?>,
    val ema50: List<Double?>,
    val bbUpper: List<Double?>,
    val bbMiddle: List<Double?>,
    val bbLower: List<Double?>,
    val rsi14: List<Double?>,
    val macdLine: List<Double?>,
    val macdSignal: List<Double?>,
    val macdHist: List<Double?>,
    val atr14: Double,
    val fibonacci: FibonacciLevels?,
    val zones: List<PriceZone>,
    val patterns: List<CandlestickPattern>,
    val latestRsi: Double,
    val latestMacdHist: Double,
    val trendBias: String
)

data class AiTradeIdea(
    val symbol: String,
    val timeframe: String,
    val bias: String,
    val confidencePercent: Int,
    val riskRewardRatio: Double,
    val suggestedEntry: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val confluenceFactors: List<String>,
    val invalidationCondition: String,
    val reasoning: String,
    val riskNote: String,
    val generatedAtMs: Long
)

enum class OrderType(val label: String, val isPending: Boolean) {
    BUY_MARKET("Market Buy", false),
    SELL_MARKET("Market Sell", false),
    BUY_LIMIT("Buy Limit", true),
    SELL_LIMIT("Sell Limit", true),
    BUY_STOP("Buy Stop", true),
    SELL_STOP("Sell Stop", true)
}

data class OpenPosition(
    val ticketId: String,
    val symbol: String,
    val direction: String,
    val lots: Double,
    val openPrice: Double,
    val currentPrice: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val trailingStopPips: Int,
    val floatingPnl: Double,
    val openedAtMs: Long,
    val accountType: String
)

data class PendingOrder(
    val orderId: String,
    val symbol: String,
    val orderType: OrderType,
    val lots: Double,
    val triggerPrice: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val trailingStopPips: Int,
    val createdAtMs: Long,
    val accountType: String
)

data class OrderDraft(
    val symbol: MarketSymbol,
    val orderType: OrderType,
    val lots: Double,
    val entryPrice: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val trailingStopPips: Int,
    val setupTag: String = "Manual Setup"
)

data class RiskEvaluation(
    val approved: Boolean,
    val riskAmountUsd: Double,
    val riskPercent: Double,
    val rewardRiskRatio: Double,
    val estimatedMarginUsd: Double,
    val blockers: List<String>,
    val warnings: List<String>
)

data class InAppAlert(
    val id: String,
    val category: String,
    val title: String,
    val message: String,
    val timestampMs: Long,
    val isCritical: Boolean = false
)

data class TradeAnalytics(
    val totalTrades: Int,
    val winRatePct: Double,
    val profitFactor: Double,
    val netPnl: Double,
    val averageWin: Double,
    val averageLoss: Double,
    val equityCurvePoints: List<Double>
)
