package com.example

import com.example.data.model.MarketSymbol
import com.example.data.model.Mt5AccountDoc
import com.example.data.model.OrderDraft
import com.example.data.model.OrderType
import com.example.data.model.RiskSettingsDoc
import com.example.domain.RiskEngine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `risk engine rejects missing stop loss when required`() {
        val account = Mt5AccountDoc(
            id = "acc1",
            userId = "u1",
            label = "LIVE MT5",
            brokerName = "Apex Broker",
            accountLogin = "100200",
            accountType = "LIVE",
            balance = 25000.0,
            equity = 25000.0
        )
        val settings = RiskSettingsDoc(userId = "u1", requireStopLoss = true)
        val draft = OrderDraft(
            symbol = MarketSymbol.EURUSD,
            orderType = OrderType.BUY_MARKET,
            lots = 0.25,
            entryPrice = 1.0850,
            stopLoss = 0.0,
            takeProfit = 1.0920,
            trailingStopPips = 0
        )

        val evaluation = RiskEngine.evaluateOrder(
            draft = draft,
            account = account,
            settings = settings,
            openPositionsCount = 0,
            todayRealizedPnl = 0.0,
            floatingPnl = 0.0
        )

        assertFalse(evaluation.approved)
        assertTrue(evaluation.blockers.any { it.contains("Stop-Loss", ignoreCase = true) })
    }

    @Test
    fun `risk engine approves properly sized trade within risk budget`() {
        val account = Mt5AccountDoc(
            id = "acc1",
            userId = "u1",
            label = "DEMO MT5",
            brokerName = "Apex Demo",
            accountLogin = "500100",
            accountType = "DEMO",
            balance = 25000.0,
            equity = 25000.0
        )
        val settings = RiskSettingsDoc(
            userId = "u1",
            maxRiskPerTradePct = 1.0,
            requireStopLoss = true,
            disclaimerAccepted = true
        )
        val draft = OrderDraft(
            symbol = MarketSymbol.EURUSD,
            orderType = OrderType.BUY_MARKET,
            lots = 0.25,
            entryPrice = 1.0850,
            stopLoss = 1.0800,
            takeProfit = 1.0950,
            trailingStopPips = 0
        )

        val evaluation = RiskEngine.evaluateOrder(
            draft = draft,
            account = account,
            settings = settings,
            openPositionsCount = 0,
            todayRealizedPnl = 0.0,
            floatingPnl = 0.0
        )

        assertTrue(evaluation.approved)
    }
}
