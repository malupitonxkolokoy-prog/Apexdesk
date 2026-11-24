package com.example.domain

import com.example.data.model.MarketSymbol
import com.example.data.model.Mt5AccountDoc
import com.example.data.model.OrderDraft
import com.example.data.model.OrderType
import com.example.data.model.RiskEvaluation
import com.example.data.model.RiskSettingsDoc
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

object RiskEngine {

    fun calculateRecommendedLots(
        symbol: MarketSymbol,
        accountBalance: Double,
        riskPercent: Double,
        entryPrice: Double,
        stopLossPrice: Double
    ): Double {
        val stopDist = abs(entryPrice - stopLossPrice)
        if (stopDist <= 0.0 || accountBalance <= 0.0) return 0.01
        val riskUsd = accountBalance * (riskPercent / 100.0)
        val rawLots = when (symbol) {
            MarketSymbol.USDJPY -> riskUsd / ((stopDist / entryPrice) * symbol.contractSize)
            else -> riskUsd / (stopDist * symbol.contractSize)
        }
        val clamped = rawLots.coerceIn(0.01, 100.0)
        return (clamped * 100.0).roundToInt() / 100.0
    }

    fun evaluateOrder(
        draft: OrderDraft,
        account: Mt5AccountDoc,
        settings: RiskSettingsDoc,
        openPositionsCount: Int,
        todayRealizedPnl: Double,
        floatingPnl: Double
    ): RiskEvaluation {
        val blockers = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (!settings.disclaimerAccepted) {
            blockers.add("Risk disclosure must be acknowledged in Risk & Vault before placing orders.")
        }

        if (openPositionsCount >= settings.maxOpenPositions.toInt()) {
            blockers.add("Max open positions reached ($openPositionsCount / ${settings.maxOpenPositions}).")
        }

        val dailyTotalPnl = todayRealizedPnl + floatingPnl
        val maxDailyLossUsd = account.balance * (settings.maxDailyLossPct / 100.0)
        if (account.balance > 0 && dailyTotalPnl <= -maxDailyLossUsd) {
            blockers.add(
                "Daily loss limit breached (${formatUsd(dailyTotalPnl)} vs max -${formatUsd(maxDailyLossUsd)})."
            )
        }

        val isBuy = draft.orderType == OrderType.BUY_MARKET ||
            draft.orderType == OrderType.BUY_LIMIT ||
            draft.orderType == OrderType.BUY_STOP

        if (settings.requireStopLoss && draft.stopLoss <= 0.0) {
            blockers.add("Mandatory Stop-Loss rule is active. Specify a valid Stop-Loss price.")
        }

        if (draft.stopLoss > 0.0) {
            val validSlSide = if (isBuy) draft.stopLoss < draft.entryPrice else draft.stopLoss > draft.entryPrice
            if (!validSlSide) {
                blockers.add(
                    if (isBuy) "Buy Stop-Loss must be below entry price."
                    else "Sell Stop-Loss must be above entry price."
                )
            }
        }

        if (draft.takeProfit > 0.0) {
            val validTpSide = if (isBuy) draft.takeProfit > draft.entryPrice else draft.takeProfit < draft.entryPrice
            if (!validTpSide) {
                blockers.add(
                    if (isBuy) "Buy Take-Profit must be above entry price."
                    else "Sell Take-Profit must be below entry price."
                )
            }
        }

        val slDist = if (draft.stopLoss > 0.0) abs(draft.entryPrice - draft.stopLoss) else 0.0
        val tpDist = if (draft.takeProfit > 0.0) abs(draft.takeProfit - draft.entryPrice) else 0.0
        val riskAmountUsd = if (slDist > 0.0) {
            when (draft.symbol) {
                MarketSymbol.USDJPY -> ((slDist / max(draft.entryPrice, 1e-6)) * draft.symbol.contractSize) * draft.lots
                else -> slDist * draft.symbol.contractSize * draft.lots
            }
        } else {
            0.0
        }

        val riskPercent = if (account.balance > 0.0) (riskAmountUsd / account.balance) * 100.0 else 0.0
        if (riskPercent > settings.maxRiskPerTradePct) {
            blockers.add(
                "Trade risk (${String.format("%.2f", riskPercent)}%) exceeds max allowed (${String.format("%.2f", settings.maxRiskPerTradePct)}%). Reduce lot size."
            )
        }

        val rr = if (slDist > 0.0 && tpDist > 0.0) tpDist / slDist else 0.0
        if (draft.stopLoss > 0.0 && draft.takeProfit > 0.0 && rr < settings.minRiskRewardRatio) {
            warnings.add(
                "Risk/Reward (${String.format("%.2f", rr)}:1) is below target minimum (${String.format("%.2f", settings.minRiskRewardRatio)}:1)."
            )
        }

        val notional = draft.lots * draft.symbol.contractSize *
            if (draft.symbol == MarketSymbol.USDJPY) 1.0 else draft.entryPrice
        val estimatedMargin = notional / max(account.leverage.toDouble(), 1.0)
        val freeMargin = (account.equity - account.marginUsed).coerceAtLeast(0.0)
        if (estimatedMargin > freeMargin) {
            blockers.add(
                "Insufficient free margin (need ${formatUsd(estimatedMargin)}, available ${formatUsd(freeMargin)})."
            )
        }

        if (account.accountType == "LIVE") {
            warnings.add("LIVE MT5 account selected: real capital is at risk. Mandatory confirmation required.")
        }

        return RiskEvaluation(
            approved = blockers.isEmpty(),
            riskAmountUsd = riskAmountUsd,
            riskPercent = riskPercent,
            rewardRiskRatio = rr,
            estimatedMarginUsd = estimatedMargin,
            blockers = blockers,
            warnings = warnings
        )
    }

    private fun formatUsd(value: Double): String {
        val absVal = abs(value)
        val sign = if (value < 0) "-" else ""
        return "$sign$${String.format("%.2f", absVal)}"
    }
}
