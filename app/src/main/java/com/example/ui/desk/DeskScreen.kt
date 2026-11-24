package com.example.ui.desk

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.MarketSymbol
import com.example.data.model.Mt5AccountDoc
import com.example.data.model.OpenPosition
import com.example.data.model.PendingOrder
import com.example.data.model.RiskSettingsDoc
import com.example.data.model.Timeframe
import com.example.domain.ComplianceDisclosures
import com.example.ui.AiAnalysisUiState
import com.example.ui.ChartWorkspaceState
import com.example.ui.CloudUiState
import com.example.ui.MainViewModel
import com.example.ui.charts.InteractiveCandlestickChart
import com.example.ui.components.SensitiveActionRequest
import com.example.ui.components.SensitiveVerificationDialog
import com.example.ui.theme.AiCyan
import com.example.ui.theme.AmberGold
import com.example.ui.theme.BearCrimson
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.BullEmerald
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.MonoPriceLarge
import com.example.ui.theme.MonoPriceMedium
import com.example.ui.theme.MonoPriceSmall
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun DeskScreen(
    viewModel: MainViewModel,
    onNavigateToChart: (MarketSymbol) -> Unit,
    onNavigateToVault: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accountsState by viewModel.accountsState.collectAsStateWithLifecycle()
    val activeAccount by viewModel.activeAccount.collectAsStateWithLifecycle()
    val riskSettings by viewModel.currentRiskSettings.collectAsStateWithLifecycle()
    val chartState by viewModel.chartState.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val openPositions by viewModel.openPositions.collectAsStateWithLifecycle()
    val pendingOrders by viewModel.pendingOrders.collectAsStateWithLifecycle()
    val quotes by viewModel.symbolQuotes.collectAsStateWithLifecycle()
    val alerts by viewModel.alerts.collectAsStateWithLifecycle()
    val sensitiveTradeGuard by viewModel.sensitiveTradeBiometricEnabled.collectAsStateWithLifecycle()

    var pendingChallenge by remember { mutableStateOf<SensitiveActionRequest?>(null) }

    val floatingPnl = openPositions.sumOf { it.floatingPnl }
    val liveEquity = (activeAccount.balance + floatingPnl).coerceAtLeast(0.0)
    val freeMargin = (liveEquity - activeAccount.marginUsed).coerceAtLeast(0.0)
    val marginLevelPct = if (activeAccount.marginUsed > 0.0) (liveEquity / activeAccount.marginUsed) * 100.0 else 0.0
    val maxDailyLossUsd = activeAccount.balance * (riskSettings.maxDailyLossPct / 100.0)
    val drawdownUsedRatio = if (maxDailyLossUsd > 0.0 && floatingPnl < 0.0) {
        (abs(floatingPnl) / maxDailyLossUsd).toFloat().coerceIn(0f, 1f)
    } else 0f

    val accounts = (accountsState as? CloudUiState.Success)?.data.orEmpty()

    fun requestPositionClose(pos: OpenPosition) {
        val needsVerify = sensitiveTradeGuard || (activeAccount.accountType == "LIVE" && riskSettings.requireBiometricForLive)
        if (!needsVerify) {
            viewModel.closePosition(pos, biometricVerified = false)
            return
        }
        val pnlText = "${if (pos.floatingPnl >= 0) "+" else ""}$${String.format(Locale.US, "%.2f", pos.floatingPnl)}"
        pendingChallenge = SensitiveActionRequest(
            title = "Confirm Position Close",
            subtitle = "Close ${pos.direction} ${pos.lots} ${pos.symbol} (${activeAccount.accountType})",
            summary = "Open ${pos.openPrice} → Current ${pos.currentPrice} • Floating P&L $pnlText ${activeAccount.currency}",
            confirmLabel = "Close Position",
            isHighRisk = true,
            onAuthorized = { bioOk -> viewModel.closePosition(pos, biometricVerified = bioOk) }
        )
    }

    fun requestOrderCancel(order: PendingOrder) {
        val needsVerify = sensitiveTradeGuard || (activeAccount.accountType == "LIVE" && riskSettings.requireBiometricForLive)
        if (!needsVerify) {
            viewModel.cancelPendingOrder(order, biometricVerified = false)
            return
        }
        pendingChallenge = SensitiveActionRequest(
            title = "Confirm Cancel Pending Order",
            subtitle = "Cancel ${order.orderType.label} ${order.lots} ${order.symbol}",
            summary = "Trigger @ ${order.triggerPrice} • SL ${order.stopLoss} / TP ${order.takeProfit}",
            confirmLabel = "Cancel Order",
            isHighRisk = false,
            onAuthorized = { bioOk -> viewModel.cancelPendingOrder(order, biometricVerified = bioOk) }
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        val isWideDashboard = maxWidth >= 760.dp

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Mandatory Risk Disclosure Banner until acknowledged
            if (!riskSettings.disclaimerAccepted) {
                RiskDisclosureBanner(onAccept = { viewModel.acceptDisclaimer() })
            }

            // Account Switcher Strip
            AccountSwitcherBar(
                accounts = accounts,
                activeAccountId = activeAccount.id,
                onSelectAccount = { viewModel.switchActiveAccount(it) },
                onLinkClick = onNavigateToVault
            )

            if (isWideDashboard) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(0.46f),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Mt5AccountSummaryPanel(
                            account = activeAccount,
                            liveEquity = liveEquity,
                            floatingPnl = floatingPnl,
                            freeMargin = freeMargin,
                            marginLevelPct = marginLevelPct,
                            drawdownUsedRatio = drawdownUsedRatio,
                            riskSettings = riskSettings,
                            openPositionsCount = openPositions.size
                        )
                        OpenPositionsSection(
                            openPositions = openPositions,
                            pendingOrders = pendingOrders,
                            currency = activeAccount.currency,
                            onClosePosition = { requestPositionClose(it) },
                            onCancelOrder = { requestOrderCancel(it) },
                            onOpenCharts = { onNavigateToChart(chartState.symbol) }
                        )
                    }

                    Column(
                        modifier = Modifier.weight(0.54f),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        DashboardChartPanel(
                            chartState = chartState,
                            aiState = aiState,
                            onSelectSymbol = { viewModel.selectSymbol(it) },
                            onSelectTimeframe = { viewModel.selectTimeframe(it) },
                            onAnalyze = { viewModel.runAiAnalysis() },
                            onExpandChart = { onNavigateToChart(chartState.symbol) }
                        )
                        WatchlistCard(
                            quotes = quotes,
                            selectedSymbol = chartState.symbol,
                            onSelectSymbol = onNavigateToChart
                        )
                    }
                }
            } else {
                // Single-column one-handed phone layout
                Mt5AccountSummaryPanel(
                    account = activeAccount,
                    liveEquity = liveEquity,
                    floatingPnl = floatingPnl,
                    freeMargin = freeMargin,
                    marginLevelPct = marginLevelPct,
                    drawdownUsedRatio = drawdownUsedRatio,
                    riskSettings = riskSettings,
                    openPositionsCount = openPositions.size
                )

                DashboardChartPanel(
                    chartState = chartState,
                    aiState = aiState,
                    onSelectSymbol = { viewModel.selectSymbol(it) },
                    onSelectTimeframe = { viewModel.selectTimeframe(it) },
                    onAnalyze = { viewModel.runAiAnalysis() },
                    onExpandChart = { onNavigateToChart(chartState.symbol) }
                )

                OpenPositionsSection(
                    openPositions = openPositions,
                    pendingOrders = pendingOrders,
                    currency = activeAccount.currency,
                    onClosePosition = { requestPositionClose(it) },
                    onCancelOrder = { requestOrderCancel(it) },
                    onOpenCharts = { onNavigateToChart(chartState.symbol) }
                )

                WatchlistCard(
                    quotes = quotes,
                    selectedSymbol = chartState.symbol,
                    onSelectSymbol = onNavigateToChart
                )
            }

            // Real-Time Notifications & Alerts Feed
            AlertsSection(alerts = alerts)

            Spacer(Modifier.height(16.dp))
        }
    }

    pendingChallenge?.let { req ->
        SensitiveVerificationDialog(
            request = req,
            verifyTwoFactorPin = { viewModel.verifyTwoFactorCode(it) },
            twoFactorPinHint = viewModel.getTwoFactorPin(),
            onDismiss = { pendingChallenge = null }
        )
    }
}

@Composable
private fun RiskDisclosureBanner(onAccept: () -> Unit) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = AmberGold.copy(alpha = 0.14f)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AmberGold.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = AmberGold)
                Text(
                    "Risk Disclosure Required",
                    style = MaterialTheme.typography.titleSmall,
                    color = AmberGold
                )
            }
            Text(
                ComplianceDisclosures.SHORT_RISK_BANNER,
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary
            )
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = ObsidianBg),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("accept_disclaimer_button")
            ) {
                Text("Acknowledge Risk Disclosure", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AccountSwitcherBar(
    accounts: List<Mt5AccountDoc>,
    activeAccountId: String,
    onSelectAccount: (Mt5AccountDoc) -> Unit,
    onLinkClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        accounts.forEach { acc ->
            val isSelected = acc.id == activeAccountId
            val badgeColor = if (acc.accountType == "LIVE") BearCrimson else BullEmerald
            FilterChip(
                selected = isSelected,
                onClick = { onSelectAccount(acc) },
                label = { Text("${acc.label} (${acc.accountType})") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = badgeColor.copy(alpha = 0.18f),
                    selectedLabelColor = badgeColor
                )
            )
        }
        AssistChip(
            onClick = onLinkClick,
            label = { Text("Link MT5") },
            leadingIcon = {
                Icon(Icons.Filled.AddLink, contentDescription = null, modifier = Modifier.size(16.dp))
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = SlateElevated,
                labelColor = AiCyan,
                leadingIconContentColor = AiCyan
            )
        )
    }
}

@Composable
private fun Mt5AccountSummaryPanel(
    account: Mt5AccountDoc,
    liveEquity: Double,
    floatingPnl: Double,
    freeMargin: Double,
    marginLevelPct: Double,
    drawdownUsedRatio: Float,
    riskSettings: RiskSettingsDoc,
    openPositionsCount: Int
) {
    val accountColor = if (account.accountType == "LIVE") BearCrimson else BullEmerald
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MidnightSurface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp))
            .testTag("mt5_summary_panel")
    ) {
        Column(
            modifier = Modifier
                .background(Brush.linearGradient(listOf(MidnightSurface, SlateElevated.copy(alpha = 0.7f))))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = AiCyan.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = AiCyan)
                        }
                    }
                    Column {
                        Text(account.label, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(
                            "${account.brokerName} • #${account.accountLogin} • 1:${account.leverage}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                Surface(
                    color = accountColor.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = account.accountType,
                        style = MaterialTheme.typography.labelLarge,
                        color = accountColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Primary Balance vs Equity Highlight Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = ObsidianBg.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("MT5 Balance", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        Text(
                            text = "${account.currency} ${String.format(Locale.US, "%,.2f", account.balance)}",
                            style = MonoPriceMedium,
                            color = TextPrimary
                        )
                    }
                }
                Surface(
                    color = ObsidianBg.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, AiCyan.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Live Equity", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        Text(
                            text = "${account.currency} ${String.format(Locale.US, "%,.2f", liveEquity)}",
                            style = MonoPriceMedium,
                            color = AiCyan
                        )
                    }
                }
            }

            HorizontalDivider(color = BorderSubtle.copy(alpha = 0.6f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricCol(
                    label = "Floating P&L",
                    value = "${if (floatingPnl >= 0) "+" else ""}$${String.format(Locale.US, "%.2f", floatingPnl)}",
                    valueColor = if (floatingPnl >= 0) BullEmerald else BearCrimson
                )
                MetricCol("Margin Used", String.format(Locale.US, "$%,.2f", account.marginUsed), TextPrimary)
                MetricCol("Free Margin", String.format(Locale.US, "$%,.2f", freeMargin), TextPrimary)
                MetricCol(
                    "Margin Lvl",
                    if (account.marginUsed > 0.0) "${String.format(Locale.US, "%.0f", marginLevelPct)}%" else "—",
                    TextPrimary
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Daily Loss Guard (Max ${String.format(Locale.US, "%.1f", riskSettings.maxDailyLossPct)}%)",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Text(
                        "$openPositionsCount/${riskSettings.maxOpenPositions} Open Slots",
                        style = MonoPriceSmall,
                        color = AiCyan
                    )
                }
                LinearProgressIndicator(
                    progress = { drawdownUsedRatio },
                    color = if (drawdownUsedRatio > 0.7f) BearCrimson else BullEmerald,
                    trackColor = ObsidianBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                )
            }
        }
    }
}

@Composable
private fun DashboardChartPanel(
    chartState: ChartWorkspaceState,
    aiState: AiAnalysisUiState,
    onSelectSymbol: (MarketSymbol) -> Unit,
    onSelectTimeframe: (Timeframe) -> Unit,
    onAnalyze: () -> Unit,
    onExpandChart: () -> Unit
) {
    val latestPrice = chartState.candles.lastOrNull()?.close ?: chartState.symbol.defaultPrice
    val prevPrice = chartState.candles.dropLast(1).lastOrNull()?.close ?: latestPrice
    val changePct = if (prevPrice > 0.0) ((latestPrice - prevPrice) / prevPrice) * 100.0 else 0.0

    OutlinedCard(
        colors = CardDefaults.outlinedCardColors(containerColor = MidnightSurface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_chart_panel")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.CandlestickChart, contentDescription = null, tint = AiCyan)
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(chartState.symbol.ticker, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Surface(
                                color = if (chartState.fromOfflineCache) AmberGold.copy(alpha = 0.16f)
                                else BullEmerald.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (chartState.fromOfflineCache) {
                                        Icon(Icons.Filled.CloudOff, contentDescription = null, tint = AmberGold, modifier = Modifier.size(11.dp))
                                    }
                                    Text(
                                        text = if (chartState.fromOfflineCache) "CACHED" else "LIVE STREAM",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (chartState.fromOfflineCache) AmberGold else BullEmerald
                                    )
                                }
                            }
                        }
                        Text(
                            text = "${chartState.timeframe.label} • ${chartState.snapshot.trendBias}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = String.format(Locale.US, "%.${chartState.symbol.digits}f", latestPrice),
                        style = MonoPriceLarge,
                        color = if (changePct >= 0) BullEmerald else BearCrimson
                    )
                    Text(
                        text = "${if (changePct >= 0) "+" else ""}${String.format(Locale.US, "%.2f", changePct)}%",
                        style = MonoPriceSmall,
                        color = if (changePct >= 0) BullEmerald else BearCrimson
                    )
                }
            }

            // Quick Symbol & Timeframe FilterChips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(MarketSymbol.EURUSD, MarketSymbol.XAUUSD, MarketSymbol.GBPUSD, MarketSymbol.BTCUSD).forEach { sym ->
                    FilterChip(
                        selected = sym == chartState.symbol,
                        onClick = { onSelectSymbol(sym) },
                        label = { Text(sym.ticker, style = MaterialTheme.typography.labelMedium) }
                    )
                }
                Spacer(Modifier.width(4.dp))
                listOf(Timeframe.M15, Timeframe.H1, Timeframe.H4, Timeframe.D1).forEach { tf ->
                    FilterChip(
                        selected = tf == chartState.timeframe,
                        onClick = { onSelectTimeframe(tf) },
                        label = { Text(tf.label, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AiCyan.copy(alpha = 0.16f),
                            selectedLabelColor = AiCyan
                        )
                    )
                }
            }

            // Real-Time Candlestick Chart or Placeholder Viewport
            if (chartState.isLoading || chartState.candles.isEmpty()) {
                CandlestickChartPlaceholder(
                    symbolTicker = chartState.symbol.ticker,
                    timeframeLabel = chartState.timeframe.label
                )
            } else {
                InteractiveCandlestickChart(
                    symbol = chartState.symbol,
                    candles = chartState.candles,
                    snapshot = chartState.snapshot,
                    indicators = chartState.indicators.copy(showRsi = false, showMacd = false),
                    activeAiIdea = (aiState as? AiAnalysisUiState.Success)?.idea
                )
            }

            // Dashboard Chart Actions (AI Confluence + Expand Full Terminal)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onAnalyze,
                    enabled = aiState !is AiAnalysisUiState.Loading && chartState.candles.isNotEmpty(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("dashboard_ai_analyze_button")
                ) {
                    if (aiState is AiAnalysisUiState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = AiCyan)
                        Spacer(Modifier.width(6.dp))
                        Text("Scoring...", color = AiCyan)
                    } else {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = AiCyan, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("AI Setup Score", color = AiCyan)
                    }
                }
                FilledTonalButton(
                    onClick = onExpandChart,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("dashboard_expand_chart_button")
                ) {
                    Icon(Icons.Filled.OpenInFull, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Trade & Indicators")
                }
            }
        }
    }
}

@Composable
private fun CandlestickChartPlaceholder(
    symbolTicker: String,
    timeframeLabel: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianBg)
            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
            .testTag("candlestick_chart_placeholder")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cols = 16
            val step = size.width / (cols + 1)
            val gridColor = BorderSubtle.copy(alpha = 0.4f)
            for (r in 1..4) {
                val y = size.height * (r / 5f)
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1f)
            }
            val heights = listOf(0.55f, 0.48f, 0.60f, 0.52f, 0.44f, 0.50f, 0.38f, 0.46f, 0.35f, 0.42f, 0.30f, 0.36f, 0.28f, 0.33f, 0.24f, 0.29f)
            heights.forEachIndexed { idx, frac ->
                val x = (idx + 1) * step
                val top = size.height * frac
                val barH = size.height * 0.14f
                val isBull = idx % 3 != 1
                val barColor = (if (isBull) BullEmerald else BearCrimson).copy(alpha = 0.22f)
                drawLine(barColor, Offset(x, top - 16f), Offset(x, top + barH + 16f), 2f)
                drawRect(barColor, Offset(x - 6f, top), Size(12f, barH))
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .background(MidnightSurface.copy(alpha = 0.88f), RoundedCornerShape(12.dp))
                .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp, color = AiCyan)
            Text(
                text = "Real-Time Candlestick Viewport ($symbolTicker • $timeframeLabel)",
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary
            )
            Text(
                text = "Awaiting MT5 WebAPI / Room cache feed...",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun OpenPositionsSection(
    openPositions: List<OpenPosition>,
    pendingOrders: List<PendingOrder>,
    currency: String,
    onClosePosition: (OpenPosition) -> Unit,
    onCancelOrder: (PendingOrder) -> Unit,
    onOpenCharts: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Open Positions (${openPositions.size})", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            TextButton(onClick = onOpenCharts) {
                Text("New Order", color = AiCyan)
            }
        }

        if (openPositions.isEmpty()) {
            OutlinedCard(
                colors = CardDefaults.outlinedCardColors(containerColor = MidnightSurface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "No open positions. Evaluate a chart setup or tap New Order to stage a risk-checked trade.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            openPositions.forEach { pos ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MidnightSurface),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(pos.symbol, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                Surface(
                                    color = (if (pos.direction == "BUY") BullEmerald else BearCrimson).copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        "${pos.direction} ${pos.lots}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (pos.direction == "BUY") BullEmerald else BearCrimson,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text("Open ${pos.openPrice} → Now ${pos.currentPrice}", style = MonoPriceSmall, color = TextSecondary)
                            Text(
                                "SL ${pos.stopLoss}  •  TP ${pos.takeProfit}${if (pos.trailingStopPips > 0) "  •  Trail ${pos.trailingStopPips}p" else ""}",
                                style = MonoPriceSmall,
                                color = TextMuted
                            )
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "${if (pos.floatingPnl >= 0) "+" else ""}$${String.format(Locale.US, "%.2f", pos.floatingPnl)} $currency",
                                style = MonoPriceMedium,
                                color = if (pos.floatingPnl >= 0) BullEmerald else BearCrimson
                            )
                            OutlinedButton(
                                onClick = { onClosePosition(pos) },
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text("Close", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                            }
                        }
                    }
                }
            }
        }

        if (pendingOrders.isNotEmpty()) {
            Text("Pending Orders (${pendingOrders.size})", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            pendingOrders.forEach { order ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MidnightSurface),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${order.symbol} • ${order.orderType.label}", style = MaterialTheme.typography.titleSmall, color = AmberGold)
                            Text(
                                "${order.lots} lots @ ${order.triggerPrice} (SL ${order.stopLoss} / TP ${order.takeProfit})",
                                style = MonoPriceSmall,
                                color = TextSecondary
                            )
                        }
                        TextButton(onClick = { onCancelOrder(order) }) {
                            Text("Cancel", color = BearCrimson)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchlistCard(
    quotes: Map<MarketSymbol, Double>,
    selectedSymbol: MarketSymbol,
    onSelectSymbol: (MarketSymbol) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MidnightSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                text = "MT5 Watchlist",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            MarketSymbol.entries.forEachIndexed { idx, sym ->
                val bid = quotes[sym] ?: sym.defaultPrice
                val ask = bid + sym.spreadPips * sym.pipSize
                ListItem(
                    colors = ListItemDefaults.colors(
                        containerColor = if (sym == selectedSymbol) SlateElevated.copy(alpha = 0.55f) else Color.Transparent
                    ),
                    leadingContent = {
                        Icon(
                            Icons.Filled.ShowChart,
                            contentDescription = null,
                            tint = if (sym == selectedSymbol) AiCyan else TextSecondary
                        )
                    },
                    headlineContent = {
                        Text(sym.ticker, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    },
                    supportingContent = {
                        Text("${sym.displayName} • Spread ${sym.spreadPips}p", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    },
                    trailingContent = {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(String.format(Locale.US, "%.${sym.digits}f", bid), style = MonoPriceMedium, color = TextPrimary)
                            Text("Ask ${String.format(Locale.US, "%.${sym.digits}f", ask)}", style = MonoPriceSmall, color = TextMuted)
                        }
                    },
                    modifier = Modifier
                        .clickable { onSelectSymbol(sym) }
                        .testTag("watchlist_${sym.ticker}")
                )
                if (idx < MarketSymbol.entries.lastIndex) {
                    HorizontalDivider(color = BorderSubtle.copy(alpha = 0.4f), modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun AlertsSection(alerts: List<com.example.data.model.InAppAlert>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = AiCyan, modifier = Modifier.size(18.dp))
            Text("Real-Time Terminal Alerts", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        }
        alerts.take(5).forEach { alert ->
            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(alert.timestampMs))
            Surface(
                color = if (alert.isCritical) BearCrimson.copy(alpha = 0.12f) else MidnightSurface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (alert.isCritical) BearCrimson.copy(alpha = 0.4f) else BorderSubtle, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            alert.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (alert.isCritical) BearCrimson else AiCyan
                        )
                        Text(timeStr, style = MonoPriceSmall, color = TextMuted)
                    }
                    Text(alert.message, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun MetricCol(label: String, value: String, valueColor: Color = TextPrimary) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Text(value, style = MonoPriceSmall, color = valueColor)
    }
}
