package com.example.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AiTradeIdea
import com.example.data.model.MarketSymbol
import com.example.data.model.OrderDraft
import com.example.data.model.OrderType
import com.example.data.model.Timeframe
import com.example.security.BiometricAuthHelper
import com.example.ui.AiAnalysisUiState
import com.example.ui.MainViewModel
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
import java.util.Locale
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChartsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val chartState by viewModel.chartState.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val activeAccount by viewModel.activeAccount.collectAsStateWithLifecycle()
    val riskSettings by viewModel.currentRiskSettings.collectAsStateWithLifecycle()

    var showOrderSheet by remember { mutableStateOf(false) }
    var presetOrderType by remember { mutableStateOf(OrderType.BUY_MARKET) }
    var presetAiIdea by remember { mutableStateOf<AiTradeIdea?>(null) }

    val latestPrice = chartState.candles.lastOrNull()?.close ?: chartState.symbol.defaultPrice
    val prevPrice = chartState.candles.dropLast(1).lastOrNull()?.close ?: latestPrice
    val changePct = if (prevPrice > 0.0) ((latestPrice - prevPrice) / prevPrice) * 100.0 else 0.0

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Instrument Selector Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MarketSymbol.entries.forEach { sym ->
                val selected = sym == chartState.symbol
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.selectSymbol(sym) },
                    label = { Text(sym.ticker, style = MaterialTheme.typography.labelLarge) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AiCyan.copy(alpha = 0.2f),
                        selectedLabelColor = AiCyan
                    ),
                    modifier = Modifier.testTag("symbol_chip_${sym.ticker}")
                )
            }
        }

        // Price Header + Offline/Live Source Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = chartState.symbol.ticker,
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Surface(
                        color = if (chartState.fromOfflineCache) AmberGold.copy(alpha = 0.16f)
                        else BullEmerald.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (chartState.fromOfflineCache) {
                                Icon(
                                    Icons.Filled.CloudOff,
                                    contentDescription = "Offline cached data",
                                    tint = AmberGold,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            Text(
                                text = chartState.sourceLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (chartState.fromOfflineCache) AmberGold else BullEmerald
                            )
                        }
                    }
                }
                Text(
                    text = "${chartState.symbol.displayName}  •  ${chartState.snapshot.trendBias}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
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

        // Timeframe Selector + Refresh
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Timeframe.entries.forEach { tf ->
                    val selected = tf == chartState.timeframe
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.selectTimeframe(tf) },
                        label = { Text(tf.label, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SlateElevated,
                            selectedLabelColor = TextPrimary
                        ),
                        modifier = Modifier.testTag("tf_chip_${tf.label}")
                    )
                }
            }
            IconButton(
                onClick = { viewModel.refreshChart() },
                modifier = Modifier.testTag("refresh_chart_button")
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh chart feed", tint = TextSecondary)
            }
        }

        // Candlestick Chart Canvas
        val ideaForOverlay = (aiState as? AiAnalysisUiState.Success)?.idea
        InteractiveCandlestickChart(
            symbol = chartState.symbol,
            candles = chartState.candles,
            snapshot = chartState.snapshot,
            indicators = chartState.indicators,
            activeAiIdea = ideaForOverlay
        )

        // Technical Indicator Toggles
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IndicatorChip("EMA 20", chartState.indicators.showEma20) {
                viewModel.toggleIndicator { it.copy(showEma20 = !it.showEma20) }
            }
            IndicatorChip("EMA 50", chartState.indicators.showEma50) {
                viewModel.toggleIndicator { it.copy(showEma50 = !it.showEma50) }
            }
            IndicatorChip("Bollinger", chartState.indicators.showBollinger) {
                viewModel.toggleIndicator { it.copy(showBollinger = !it.showBollinger) }
            }
            IndicatorChip("Fibonacci", chartState.indicators.showFibonacci) {
                viewModel.toggleIndicator { it.copy(showFibonacci = !it.showFibonacci) }
            }
            IndicatorChip("S/R Zones", chartState.indicators.showSupportResistance) {
                viewModel.toggleIndicator { it.copy(showSupportResistance = !it.showSupportResistance) }
            }
            IndicatorChip("RSI 14", chartState.indicators.showRsi) {
                viewModel.toggleIndicator { it.copy(showRsi = !it.showRsi) }
            }
            IndicatorChip("MACD", chartState.indicators.showMacd) {
                viewModel.toggleIndicator { it.copy(showMacd = !it.showMacd) }
            }
            IndicatorChip("Volume", chartState.indicators.showVolume) {
                viewModel.toggleIndicator { it.copy(showVolume = !it.showVolume) }
            }
        }

        // Detected Candlestick Patterns
        if (chartState.snapshot.patterns.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MidnightSurface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Detected Price Action Patterns",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                    chartState.snapshot.patterns.forEach { pat ->
                        val patColor = when (pat.bias) {
                            "Bullish" -> BullEmerald
                            "Bearish" -> BearCrimson
                            else -> AmberGold
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(pat.name, style = MaterialTheme.typography.labelLarge, color = patColor)
                                Text(pat.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            Surface(
                                color = patColor.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = pat.bias.uppercase(Locale.US),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = patColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // One-Handed Quick Execution Bar (BUY / SELL)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    presetAiIdea = null
                    presetOrderType = OrderType.SELL_MARKET
                    showOrderSheet = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = BearCrimson, contentColor = TextPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("sell_order_button")
            ) {
                Text("SELL / SHORT", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = {
                    presetAiIdea = null
                    presetOrderType = OrderType.BUY_MARKET
                    showOrderSheet = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = BullEmerald, contentColor = ObsidianBg),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("buy_order_button")
            ) {
                Text("BUY / LONG", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
        }

        // Gemini AI Confluence & Setup Scorer Card
        AiConfluenceCard(
            aiState = aiState,
            digits = chartState.symbol.digits,
            onAnalyzeClick = { viewModel.runAiAnalysis() },
            onApplyIdea = { idea ->
                presetAiIdea = idea
                presetOrderType = if (idea.bias == "BEARISH") OrderType.SELL_MARKET else OrderType.BUY_MARKET
                showOrderSheet = true
            }
        )

        Spacer(Modifier.height(16.dp))
    }

    if (showOrderSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showOrderSheet = false },
            sheetState = sheetState,
            containerColor = MidnightSurface
        ) {
            OrderExecutionSheetContent(
                viewModel = viewModel,
                symbol = chartState.symbol,
                livePrice = latestPrice,
                atr = chartState.snapshot.atr14,
                initialOrderType = presetOrderType,
                aiIdea = presetAiIdea,
                accountType = activeAccount.accountType,
                requireBiometric = riskSettings.requireBiometricForLive,
                requireTwoFactor = riskSettings.requireTwoFactorForLive,
                onDismiss = { showOrderSheet = false }
            )
        }
    }
}

@Composable
private fun IndicatorChip(label: String, enabled: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = enabled,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AiCyan.copy(alpha = 0.16f),
            selectedLabelColor = AiCyan
        )
    )
}

@Composable
private fun AiConfluenceCard(
    aiState: AiAnalysisUiState,
    digits: Int,
    onAnalyzeClick: () -> Unit,
    onApplyIdea: (AiTradeIdea) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MidnightSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AiCyan.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    Icon(Icons.Filled.AutoAwesome, contentDescription = "AI Analysis", tint = AiCyan)
                    Column {
                        Text(
                            text = "AI Chart Confluence Engine",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Probabilistic setup scoring • Not financial advice",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                OutlinedButton(
                    onClick = onAnalyzeClick,
                    enabled = aiState !is AiAnalysisUiState.Loading,
                    modifier = Modifier.testTag("run_ai_analysis_button")
                ) {
                    Text(
                        text = if (aiState is AiAnalysisUiState.Success) "Re-Analyze" else "Analyze",
                        color = AiCyan
                    )
                }
            }

            when (aiState) {
                is AiAnalysisUiState.Idle -> {
                    Text(
                        text = "Run Gemini structure analysis to evaluate EMA alignment, momentum, volatility, S/R zones, and candlestick confluence for this timeframe.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                is AiAnalysisUiState.Loading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = AiCyan,
                            strokeWidth = 2.5.dp
                        )
                        Text(
                            text = "Evaluating multi-indicator structure & risk/reward...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AiCyan
                        )
                    }
                }
                is AiAnalysisUiState.Error -> {
                    Surface(
                        color = BearCrimson.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = aiState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = BearCrimson,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                is AiAnalysisUiState.Success -> {
                    val idea = aiState.idea
                    val biasColor = when (idea.bias) {
                        "BULLISH" -> BullEmerald
                        "BEARISH" -> BearCrimson
                        else -> AmberGold
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = biasColor.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${idea.bias} BIAS",
                                style = MaterialTheme.typography.labelLarge,
                                color = biasColor,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Text(
                            text = "Confidence ${idea.confidencePercent}%  •  R:R ${String.format(Locale.US, "%.2f", idea.riskRewardRatio)}:1",
                            style = MonoPriceSmall,
                            color = TextPrimary
                        )
                    }

                    LinearProgressIndicator(
                        progress = { idea.confidencePercent / 100f },
                        color = biasColor,
                        trackColor = SlateElevated,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PricePill("Entry", String.format(Locale.US, "%.${digits}f", idea.suggestedEntry), AiCyan, Modifier.weight(1f))
                        PricePill("Stop Loss", String.format(Locale.US, "%.${digits}f", idea.stopLoss), BearCrimson, Modifier.weight(1f))
                        PricePill("Take Profit", String.format(Locale.US, "%.${digits}f", idea.takeProfit), BullEmerald, Modifier.weight(1f))
                    }

                    Text(
                        text = idea.reasoning,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )

                    if (idea.confluenceFactors.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Confluence Drivers:", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                            idea.confluenceFactors.forEach { factor ->
                                Text("• $factor", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
                    }

                    Surface(
                        color = SlateElevated,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Invalidation: ${idea.invalidationCondition}",
                                style = MaterialTheme.typography.bodySmall,
                                color = AmberGold
                            )
                            Text(
                                text = idea.riskNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                    }

                    if (idea.bias != "NEUTRAL") {
                        Button(
                            onClick = { onApplyIdea(idea) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AiCyan,
                                contentColor = ObsidianBg
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("apply_ai_setup_button")
                        ) {
                            Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Review & Stage Setup in Order Ticket", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PricePill(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        color = SlateElevated,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Text(value, style = MonoPriceMedium, color = color)
        }
    }
}

@Composable
private fun OrderExecutionSheetContent(
    viewModel: MainViewModel,
    symbol: MarketSymbol,
    livePrice: Double,
    atr: Double,
    initialOrderType: OrderType,
    aiIdea: AiTradeIdea?,
    accountType: String,
    requireBiometric: Boolean,
    requireTwoFactor: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val defaultDist = max(atr * 1.5, symbol.pipSize * 18)

    var orderType by remember { mutableStateOf(initialOrderType) }
    val isBuy = orderType == OrderType.BUY_MARKET || orderType == OrderType.BUY_LIMIT || orderType == OrderType.BUY_STOP

    var entryText by remember {
        mutableStateOf(
            String.format(
                Locale.US,
                "%.${symbol.digits}f",
                aiIdea?.suggestedEntry ?: livePrice
            )
        )
    }
    var slText by remember {
        val defaultSl = aiIdea?.stopLoss ?: if (isBuy) livePrice - defaultDist else livePrice + defaultDist
        mutableStateOf(String.format(Locale.US, "%.${symbol.digits}f", defaultSl))
    }
    var tpText by remember {
        val defaultTp = aiIdea?.takeProfit ?: if (isBuy) livePrice + defaultDist * 2.0 else livePrice - defaultDist * 2.0
        mutableStateOf(String.format(Locale.US, "%.${symbol.digits}f", defaultTp))
    }
    var lotsText by remember {
        val entryV = entryText.toDoubleOrNull() ?: livePrice
        val slV = slText.toDoubleOrNull() ?: (livePrice - defaultDist)
        val rec = viewModel.recommendLotSize(symbol, entryV, slV)
        mutableStateOf(String.format(Locale.US, "%.2f", rec))
    }
    var trailingPipsText by remember { mutableStateOf("0") }
    var twoFactorInput by remember { mutableStateOf("") }
    var showTwoFactorFallback by remember { mutableStateOf(false) }
    var executionFeedback by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    val parsedEntry = if (orderType.isPending) (entryText.toDoubleOrNull() ?: livePrice) else livePrice
    val parsedSl = slText.toDoubleOrNull() ?: 0.0
    val parsedTp = tpText.toDoubleOrNull() ?: 0.0
    val parsedLots = (lotsText.toDoubleOrNull() ?: 0.01).coerceIn(0.01, 500.0)
    val parsedTrailing = trailingPipsText.toIntOrNull()?.coerceAtLeast(0) ?: 0

    val draft = OrderDraft(
        symbol = symbol,
        orderType = orderType,
        lots = parsedLots,
        entryPrice = parsedEntry,
        stopLoss = parsedSl,
        takeProfit = parsedTp,
        trailingStopPips = parsedTrailing,
        setupTag = if (aiIdea != null) "AI Confluence (${aiIdea.confidencePercent}%)" else "Manual Setup"
    )
    val riskEval = viewModel.evaluateOrderDraft(draft)
    val sensitiveReq = viewModel.evaluateSensitiveTradeRequirement(draft, riskEval)
    val biometricReady = remember(context) { BiometricAuthHelper.isBiometricAvailable(context) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Order Ticket • ${symbol.ticker}", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                Text(
                    text = "Account Mode: $accountType  •  Live Price ${String.format(Locale.US, "%.${symbol.digits}f", livePrice)}",
                    style = MonoPriceSmall,
                    color = if (accountType == "LIVE") BearCrimson else BullEmerald
                )
            }
            Surface(
                color = if (accountType == "LIVE") BearCrimson.copy(alpha = 0.16f) else BullEmerald.copy(alpha = 0.16f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = accountType,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (accountType == "LIVE") BearCrimson else BullEmerald,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        // Order Type Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OrderType.entries.forEach { ot ->
                FilterChip(
                    selected = ot == orderType,
                    onClick = { orderType = ot },
                    label = { Text(ot.label, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = lotsText,
                onValueChange = { lotsText = it },
                label = { Text("Lots (Size)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("order_lots_input")
            )
            OutlinedTextField(
                value = if (orderType.isPending) entryText else String.format(Locale.US, "%.${symbol.digits}f", livePrice),
                onValueChange = { if (orderType.isPending) entryText = it },
                enabled = orderType.isPending,
                label = { Text(if (orderType.isPending) "Trigger Price" else "Market Price") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = slText,
                onValueChange = { slText = it },
                label = { Text("Stop-Loss") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("order_sl_input")
            )
            OutlinedTextField(
                value = tpText,
                onValueChange = { tpText = it },
                label = { Text("Take-Profit") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("order_tp_input")
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = trailingPipsText,
                onValueChange = { trailingPipsText = it },
                label = { Text("Trailing Stop (Pips)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = {
                    val rec = viewModel.recommendLotSize(symbol, parsedEntry, parsedSl)
                    lotsText = String.format(Locale.US, "%.2f", rec)
                },
                modifier = Modifier.height(54.dp)
            ) {
                Text("Auto-Size Risk")
            }
        }

        // Pre-Trade Risk Gate Summary
        Surface(
            color = if (riskEval.approved) SlateElevated else BearCrimson.copy(alpha = 0.14f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    if (riskEval.approved) BorderSubtle else BearCrimson.copy(alpha = 0.4f),
                    RoundedCornerShape(12.dp)
                )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Est. Risk: $${String.format(Locale.US, "%.2f", riskEval.riskAmountUsd)} (${String.format(Locale.US, "%.2f", riskEval.riskPercent)}%)",
                        style = MonoPriceSmall,
                        color = TextPrimary
                    )
                    Text(
                        text = "R:R ${String.format(Locale.US, "%.2f", riskEval.rewardRiskRatio)}:1  •  Margin $${String.format(Locale.US, "%.0f", riskEval.estimatedMarginUsd)}",
                        style = MonoPriceSmall,
                        color = AiCyan
                    )
                }
                riskEval.blockers.forEach { blocker ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = BearCrimson, modifier = Modifier.size(16.dp))
                        Text(blocker, style = MaterialTheme.typography.bodySmall, color = BearCrimson)
                    }
                }
                riskEval.warnings.forEach { warn ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Shield, contentDescription = null, tint = AmberGold, modifier = Modifier.size(16.dp))
                        Text(warn, style = MaterialTheme.typography.bodySmall, color = AmberGold)
                    }
                }
                if (riskEval.approved && riskEval.blockers.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = BullEmerald, modifier = Modifier.size(16.dp))
                        Text("All pre-trade risk limits satisfied.", style = MaterialTheme.typography.bodySmall, color = BullEmerald)
                    }
                }
            }
        }

        if (sensitiveReq.required) {
            Surface(
                color = AiCyan.copy(alpha = 0.1f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AiCyan.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .testTag("order_biometric_guard_banner")
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Fingerprint,
                        contentDescription = null,
                        tint = AiCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (accountType == "LIVE" && requireTwoFactor) {
                                "BiometricPrompt + 2FA PIN Verification Required"
                            } else {
                                "BiometricPrompt Verification Required"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = AiCyan
                        )
                        Text(
                            text = sensitiveReq.reasons.joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        val needsPinField = (accountType == "LIVE" && requireTwoFactor) ||
            showTwoFactorFallback ||
            (sensitiveReq.required && !biometricReady)

        if (needsPinField) {
            OutlinedTextField(
                value = twoFactorInput,
                onValueChange = {
                    if (it.length <= 6) {
                        twoFactorInput = it.filter { ch -> ch.isDigit() }
                    }
                },
                label = { Text("6-Digit 2FA Vault PIN (Vault: ${viewModel.getTwoFactorPin()})") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("order_2fa_pin_input")
            )
        }

        if (executionFeedback != null) {
            val (ok, msg) = executionFeedback!!
            Surface(
                color = (if (ok) BullEmerald else BearCrimson).copy(alpha = 0.16f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (ok) BullEmerald else BearCrimson,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Button(
            onClick = {
                executionFeedback = null
                val dispatchOrder: (Boolean) -> Unit = { bioOk ->
                    isSubmitting = true
                    viewModel.executeOrder(draft, bioOk, twoFactorInput) { res ->
                        isSubmitting = false
                        res.onSuccess { msg ->
                            executionFeedback = true to msg
                        }.onFailure { err ->
                            executionFeedback = false to (err.localizedMessage ?: "Order execution failed.")
                        }
                    }
                }

                if (accountType == "LIVE" && requireTwoFactor && !viewModel.verifyTwoFactorCode(twoFactorInput)) {
                    showTwoFactorFallback = true
                    executionFeedback = false to "Enter your valid 6-digit 2FA Vault PIN for LIVE execution."
                    return@Button
                }

                if (sensitiveReq.required) {
                    if (viewModel.verifyTwoFactorCode(twoFactorInput) && showTwoFactorFallback) {
                        dispatchOrder(false)
                    } else if (activity != null && biometricReady) {
                        BiometricAuthHelper.authenticate(
                            activity = activity,
                            title = "Confirm Sensitive Trade Execution",
                            subtitle = "${orderType.label} $parsedLots ${symbol.ticker} ($accountType)",
                            description = "Risk: $${String.format(Locale.US, "%.2f", riskEval.riskAmountUsd)} (${String.format(Locale.US, "%.2f", riskEval.riskPercent)}%) • SL $slText • TP $tpText",
                            requireExplicitConfirmation = true,
                            onSuccess = { dispatchOrder(true) },
                            onError = { errMsg -> executionFeedback = false to errMsg },
                            onFallbackRequested = {
                                showTwoFactorFallback = true
                                if (viewModel.verifyTwoFactorCode(twoFactorInput)) {
                                    dispatchOrder(false)
                                } else {
                                    executionFeedback = false to "Enter your 6-digit 2FA Vault PIN to authorize this trade."
                                }
                            }
                        )
                    } else {
                        showTwoFactorFallback = true
                        if (viewModel.verifyTwoFactorCode(twoFactorInput)) {
                            dispatchOrder(false)
                        } else {
                            executionFeedback = false to "Device biometrics not enrolled. Enter your 6-digit 2FA Vault PIN to authorize."
                        }
                    }
                } else {
                    dispatchOrder(false)
                }
            },
            enabled = riskEval.approved && !isSubmitting,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isBuy) BullEmerald else BearCrimson,
                contentColor = if (isBuy) ObsidianBg else TextPrimary
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("confirm_execute_order_button")
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                if (sensitiveReq.required) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = when {
                        accountType == "LIVE" -> "Verify & Execute LIVE ${orderType.label}"
                        sensitiveReq.required -> "Verify & Execute ${orderType.label}"
                        else -> "Execute ${orderType.label}"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Close Ticket")
        }
        Spacer(Modifier.height(18.dp))
    }
}
