package com.example.ui.charts

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.AiTradeIdea
import com.example.data.model.Candle
import com.example.data.model.IndicatorSettings
import com.example.data.model.MarketSymbol
import com.example.data.model.TechnicalSnapshot
import com.example.ui.theme.AiCyan
import com.example.ui.theme.AmberGold
import com.example.ui.theme.BearCrimson
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.BullEmerald
import com.example.ui.theme.MidnightSurface
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun InteractiveCandlestickChart(
    symbol: MarketSymbol,
    candles: List<Candle>,
    snapshot: TechnicalSnapshot,
    indicators: IndicatorSettings,
    activeAiIdea: AiTradeIdea?,
    modifier: Modifier = Modifier
) {
    var visibleCount by remember { mutableIntStateOf(44) }
    var scrollOffsetCandles by remember { mutableFloatStateOf(0f) }
    var selectedGlobalIndex by remember(symbol, candles.size) { mutableStateOf<Int?>(null) }

    val total = candles.size
    val clampedVisible = visibleCount.coerceIn(18, max(18, min(90, total)))
    val maxOffset = max(0, total - clampedVisible).toFloat()
    val effectiveOffset = scrollOffsetCandles.coerceIn(0f, maxOffset).roundToInt()
    val startIndex = max(0, total - clampedVisible - effectiveOffset)
    val endIndex = min(total, startIndex + clampedVisible)
    val visibleCandles = if (total > 0) candles.subList(startIndex, endIndex) else emptyList()

    val selectedCandle = selectedGlobalIndex?.let { idx ->
        candles.getOrNull(idx)
    } ?: visibleCandles.lastOrNull()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MidnightSurface)
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Crosshair OHLCV Readout + Zoom Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectedCandle != null) {
                val timeStr = remember(selectedCandle.timestampMs) {
                    SimpleDateFormat("MM/dd HH:mm", Locale.US).format(Date(selectedCandle.timestampMs))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$timeStr  O:${fmt(selectedCandle.open, symbol.digits)}  H:${fmt(selectedCandle.high, symbol.digits)}",
                        style = MonoPriceSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = "L:${fmt(selectedCandle.low, symbol.digits)}  C:${fmt(selectedCandle.close, symbol.digits)}  V:${selectedCandle.volume.toInt()}",
                        style = MonoPriceSmall,
                        color = if (selectedCandle.isBullish) BullEmerald else BearCrimson
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { visibleCount = (visibleCount - 8).coerceAtLeast(18) },
                    modifier = Modifier.testTag("chart_zoom_in")
                ) {
                    Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom in chart", tint = TextSecondary)
                }
                IconButton(
                    onClick = { visibleCount = (visibleCount + 8).coerceAtMost(90) },
                    modifier = Modifier.testTag("chart_zoom_out")
                ) {
                    Icon(Icons.Filled.ZoomOut, contentDescription = "Zoom out chart", tint = TextSecondary)
                }
            }
        }

        // Main Price + Indicator Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianBg)
        ) {
            if (visibleCandles.isEmpty()) {
                Text(
                    text = "Loading market candles...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                val labelPaint = remember {
                    Paint().apply {
                        color = android.graphics.Color.parseColor("#94A3B8")
                        textSize = 26f
                        isAntiAlias = true
                    }
                }
                val badgePaint = remember {
                    Paint().apply {
                        color = android.graphics.Color.parseColor("#0A111C")
                        textSize = 26f
                        isAntiAlias = true
                        isFakeBoldText = true
                    }
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("candlestick_canvas")
                        .pointerInput(total, clampedVisible) {
                            detectHorizontalDragGestures { _, dragAmount ->
                                val candlePx = (size.width * 0.84f) / clampedVisible
                                val deltaCandles = dragAmount / max(candlePx, 4f)
                                scrollOffsetCandles = (scrollOffsetCandles + deltaCandles).coerceIn(0f, maxOffset)
                            }
                        }
                        .pointerInput(startIndex, clampedVisible) {
                            detectTapGestures { offset ->
                                val chartWidth = size.width * 0.84f
                                if (offset.x <= chartWidth && visibleCandles.isNotEmpty()) {
                                    val localIdx = ((offset.x / chartWidth) * visibleCandles.size)
                                        .toInt()
                                        .coerceIn(0, visibleCandles.lastIndex)
                                    val tapped = startIndex + localIdx
                                    selectedGlobalIndex = if (selectedGlobalIndex == tapped) null else tapped
                                }
                            }
                        }
                ) {
                    val rightAxisWidth = size.width * 0.16f
                    val chartWidth = size.width - rightAxisWidth
                    val chartTop = 14f
                    val chartBottom = size.height - 16f
                    val chartHeight = max(chartBottom - chartTop, 20f)

                    var minPrice = visibleCandles.minOf { it.low }
                    var maxPrice = visibleCandles.maxOf { it.high }

                    if (indicators.showBollinger) {
                        for (i in startIndex until endIndex) {
                            snapshot.bbUpper.getOrNull(i)?.let { maxPrice = max(maxPrice, it) }
                            snapshot.bbLower.getOrNull(i)?.let { minPrice = min(minPrice, it) }
                        }
                    }
                    if (activeAiIdea != null && activeAiIdea.symbol == symbol.ticker) {
                        maxPrice = max(maxPrice, max(activeAiIdea.takeProfit, activeAiIdea.stopLoss))
                        minPrice = min(minPrice, min(activeAiIdea.takeProfit, activeAiIdea.stopLoss))
                    }

                    val pad = max((maxPrice - minPrice) * 0.08, symbol.pipSize * 4)
                    minPrice -= pad
                    maxPrice += pad
                    val priceRange = max(maxPrice - minPrice, 1e-9)

                    fun yForPrice(p: Double): Float {
                        val norm = ((p - minPrice) / priceRange).toFloat()
                        return chartBottom - norm * chartHeight
                    }

                    // Horizontal Grid + Price Labels
                    val gridSteps = 4
                    for (g in 0..gridSteps) {
                        val frac = g / gridSteps.toFloat()
                        val y = chartTop + frac * chartHeight
                        val priceVal = maxPrice - frac * priceRange
                        drawLine(
                            color = BorderSubtle.copy(alpha = 0.45f),
                            start = Offset(0f, y),
                            end = Offset(chartWidth, y),
                            strokeWidth = 1f
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            fmt(priceVal, symbol.digits),
                            chartWidth + 8f,
                            y + 8f,
                            labelPaint
                        )
                    }

                    // Support & Resistance Zones
                    if (indicators.showSupportResistance) {
                        val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                        snapshot.zones.forEach { zone ->
                            if (zone.price in minPrice..maxPrice) {
                                val y = yForPrice(zone.price)
                                val zColor = if (zone.isResistance) BearCrimson.copy(alpha = 0.55f)
                                else BullEmerald.copy(alpha = 0.55f)
                                drawLine(
                                    color = zColor,
                                    start = Offset(0f, y),
                                    end = Offset(chartWidth, y),
                                    strokeWidth = 2f,
                                    pathEffect = dash
                                )
                            }
                        }
                    }

                    // Fibonacci Retracement Levels
                    if (indicators.showFibonacci && snapshot.fibonacci != null) {
                        val fibDash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        snapshot.fibonacci.levels.forEach { (ratioLabel, fibPrice) ->
                            if (fibPrice in minPrice..maxPrice) {
                                val y = yForPrice(fibPrice)
                                drawLine(
                                    color = AmberGold.copy(alpha = 0.45f),
                                    start = Offset(0f, y),
                                    end = Offset(chartWidth, y),
                                    strokeWidth = 1.5f,
                                    pathEffect = fibDash
                                )
                                drawContext.canvas.nativeCanvas.drawText(
                                    "Fib $ratioLabel",
                                    10f,
                                    y - 5f,
                                    labelPaint
                                )
                            }
                        }
                    }

                    val stepX = chartWidth / visibleCandles.size.toFloat()

                    // Volume Bars at bottom
                    if (indicators.showVolume) {
                        val maxVol = max(visibleCandles.maxOf { it.volume }, 1.0)
                        val volMaxH = chartHeight * 0.16f
                        visibleCandles.forEachIndexed { localIdx, c ->
                            val xCenter = (localIdx + 0.5f) * stepX
                            val barW = max(stepX * 0.52f, 2f)
                            val barH = ((c.volume / maxVol).toFloat() * volMaxH)
                            drawRect(
                                color = (if (c.isBullish) BullEmerald else BearCrimson).copy(alpha = 0.18f),
                                topLeft = Offset(xCenter - barW / 2f, chartBottom - barH),
                                size = Size(barW, barH)
                            )
                        }
                    }

                    // Bollinger Bands
                    if (indicators.showBollinger) {
                        drawIndicatorLine(
                            values = snapshot.bbUpper.subList(startIndex, endIndex),
                            stepX = stepX,
                            color = AiCyan.copy(alpha = 0.45f),
                            strokeWidth = 1.8f,
                            yForValue = ::yForPrice
                        )
                        drawIndicatorLine(
                            values = snapshot.bbLower.subList(startIndex, endIndex),
                            stepX = stepX,
                            color = AiCyan.copy(alpha = 0.45f),
                            strokeWidth = 1.8f,
                            yForValue = ::yForPrice
                        )
                    }

                    // EMA 20 & EMA 50
                    if (indicators.showEma20) {
                        drawIndicatorLine(
                            values = snapshot.ema20.subList(startIndex, endIndex),
                            stepX = stepX,
                            color = AiCyan,
                            strokeWidth = 2.6f,
                            yForValue = ::yForPrice
                        )
                    }
                    if (indicators.showEma50) {
                        drawIndicatorLine(
                            values = snapshot.ema50.subList(startIndex, endIndex),
                            stepX = stepX,
                            color = AmberGold,
                            strokeWidth = 2.6f,
                            yForValue = ::yForPrice
                        )
                    }

                    // Candlesticks
                    visibleCandles.forEachIndexed { localIdx, candle ->
                        val centerX = (localIdx + 0.5f) * stepX
                        val bodyW = max(stepX * 0.62f, 3f)
                        val candleColor = if (candle.isBullish) BullEmerald else BearCrimson

                        val highY = yForPrice(candle.high)
                        val lowY = yForPrice(candle.low)
                        val openY = yForPrice(candle.open)
                        val closeY = yForPrice(candle.close)

                        drawLine(
                            color = candleColor,
                            start = Offset(centerX, highY),
                            end = Offset(centerX, lowY),
                            strokeWidth = max(bodyW * 0.16f, 1.5f)
                        )

                        val topBody = min(openY, closeY)
                        val bodyHeight = max(abs(closeY - openY), 2.5f)
                        drawRect(
                            color = candleColor,
                            topLeft = Offset(centerX - bodyW / 2f, topBody),
                            size = Size(bodyW, bodyHeight)
                        )
                    }

                    // AI Trade Idea Entry / SL / TP overlays
                    if (activeAiIdea != null && activeAiIdea.symbol == symbol.ticker && activeAiIdea.bias != "NEUTRAL") {
                        val aiDash = PathEffect.dashPathEffect(floatArrayOf(12f, 6f), 0f)
                        listOf(
                            Triple(activeAiIdea.suggestedEntry, AiCyan, "ENTRY"),
                            Triple(activeAiIdea.stopLoss, BearCrimson, "SL"),
                            Triple(activeAiIdea.takeProfit, BullEmerald, "TP")
                        ).forEach { (priceLevel, lineColor, tag) ->
                            if (priceLevel in minPrice..maxPrice) {
                                val y = yForPrice(priceLevel)
                                drawLine(
                                    color = lineColor,
                                    start = Offset(0f, y),
                                    end = Offset(chartWidth, y),
                                    strokeWidth = 2.4f,
                                    pathEffect = aiDash
                                )
                                drawContext.canvas.nativeCanvas.drawText(
                                    "$tag ${fmt(priceLevel, symbol.digits)}",
                                    12f,
                                    y - 6f,
                                    labelPaint
                                )
                            }
                        }
                    }

                    // Live Current Price Line + Pill
                    val lastClose = visibleCandles.last().close
                    val liveY = yForPrice(lastClose)
                    val liveColor = if (visibleCandles.last().isBullish) BullEmerald else BearCrimson
                    drawLine(
                        color = liveColor.copy(alpha = 0.7f),
                        start = Offset(0f, liveY),
                        end = Offset(chartWidth, liveY),
                        strokeWidth = 1.6f
                    )
                    drawRoundRect(
                        color = liveColor,
                        topLeft = Offset(chartWidth + 4f, liveY - 16f),
                        size = Size(rightAxisWidth - 8f, 32f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        fmt(lastClose, symbol.digits),
                        chartWidth + 10f,
                        liveY + 8f,
                        badgePaint
                    )

                    // Crosshair selection vertical line
                    val selIdx = selectedGlobalIndex
                    if (selIdx != null && selIdx in startIndex until endIndex) {
                        val localSel = selIdx - startIndex
                        val cx = (localSel + 0.5f) * stepX
                        drawLine(
                            color = TextPrimary.copy(alpha = 0.5f),
                            start = Offset(cx, chartTop),
                            end = Offset(cx, chartBottom),
                            strokeWidth = 1.5f
                        )
                    }
                }
            }
        }

        // RSI(14) Sub-Pane
        if (indicators.showRsi && visibleCandles.isNotEmpty()) {
            val visibleRsi = snapshot.rsi14.subList(startIndex, endIndex)
            SubPaneHeader("RSI (14)", String.format(Locale.US, "%.1f", snapshot.latestRsi), AiCyan)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ObsidianBg)
            ) {
                val chartW = size.width * 0.84f
                val stepX = chartW / visibleRsi.size.toFloat()
                fun yRsi(v: Double): Float = size.height - ((v / 100.0).toFloat() * (size.height - 12f)) - 6f
                val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                drawLine(BearCrimson.copy(alpha = 0.4f), Offset(0f, yRsi(70.0)), Offset(chartW, yRsi(70.0)), 1.2f, pathEffect = dash)
                drawLine(BullEmerald.copy(alpha = 0.4f), Offset(0f, yRsi(30.0)), Offset(chartW, yRsi(30.0)), 1.2f, pathEffect = dash)
                drawIndicatorLine(visibleRsi, stepX, AiCyan, 2.2f, ::yRsi)
            }
        }

        // MACD(12, 26, 9) Sub-Pane
        if (indicators.showMacd && visibleCandles.isNotEmpty()) {
            val visibleHist = snapshot.macdHist.subList(startIndex, endIndex)
            val visibleMacd = snapshot.macdLine.subList(startIndex, endIndex)
            val visibleSignal = snapshot.macdSignal.subList(startIndex, endIndex)
            SubPaneHeader("MACD (12,26,9)", String.format(Locale.US, "%.5f", snapshot.latestMacdHist), AmberGold)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ObsidianBg)
            ) {
                val chartW = size.width * 0.84f
                val stepX = chartW / max(visibleHist.size, 1).toFloat()
                val allVals = (visibleHist + visibleMacd + visibleSignal).filterNotNull()
                val absMax = max(allVals.maxOfOrNull { abs(it) } ?: 0.001, 1e-6)
                val midY = size.height / 2f
                fun yMacd(v: Double): Float = midY - ((v / absMax).toFloat() * (size.height * 0.42f))
                drawLine(BorderSubtle, Offset(0f, midY), Offset(chartW, midY), 1f)
                visibleHist.forEachIndexed { idx, h ->
                    if (h != null) {
                        val x = (idx + 0.5f) * stepX
                        val yVal = yMacd(h)
                        val barW = max(stepX * 0.5f, 2f)
                        drawRect(
                            color = if (h >= 0) BullEmerald.copy(alpha = 0.55f) else BearCrimson.copy(alpha = 0.55f),
                            topLeft = Offset(x - barW / 2f, min(midY, yVal)),
                            size = Size(barW, max(abs(yVal - midY), 1.5f))
                        )
                    }
                }
                drawIndicatorLine(visibleMacd, stepX, AiCyan, 1.8f, ::yMacd)
                drawIndicatorLine(visibleSignal, stepX, AmberGold, 1.8f, ::yMacd)
            }
        }
    }
}

@Composable
private fun SubPaneHeader(title: String, value: String, accent: Color) {
    Surface(
        color = SlateElevated,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Text(value, style = MonoPriceSmall, color = accent)
        }
    }
}

private fun DrawScope.drawIndicatorLine(
    values: List<Double?>,
    stepX: Float,
    color: Color,
    strokeWidth: Float,
    yForValue: (Double) -> Float
) {
    val path = Path()
    var started = false
    values.forEachIndexed { idx, value ->
        if (value != null) {
            val x = (idx + 0.5f) * stepX
            val y = yForValue(value)
            if (!started) {
                path.moveTo(x, y)
                started = true
            } else {
                path.lineTo(x, y)
            }
        }
    }
    if (started) {
        drawPath(path = path, color = color, style = Stroke(width = strokeWidth))
    }
}

private fun fmt(value: Double, digits: Int): String {
    return String.format(Locale.US, "%.${digits}f", value)
}
