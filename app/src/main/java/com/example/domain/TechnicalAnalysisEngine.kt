package com.example.domain

import com.example.data.model.Candle
import com.example.data.model.CandlestickPattern
import com.example.data.model.FibonacciLevels
import com.example.data.model.PriceZone
import com.example.data.model.TechnicalSnapshot
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object TechnicalAnalysisEngine {

    fun analyze(candles: List<Candle>): TechnicalSnapshot {
        if (candles.isEmpty()) {
            return TechnicalSnapshot(
                ema20 = emptyList(),
                ema50 = emptyList(),
                bbUpper = emptyList(),
                bbMiddle = emptyList(),
                bbLower = emptyList(),
                rsi14 = emptyList(),
                macdLine = emptyList(),
                macdSignal = emptyList(),
                macdHist = emptyList(),
                atr14 = 0.0,
                fibonacci = null,
                zones = emptyList(),
                patterns = emptyList(),
                latestRsi = 50.0,
                latestMacdHist = 0.0,
                trendBias = "Neutral"
            )
        }

        val closes = candles.map { it.close }
        val ema20 = computeEma(closes, 20)
        val ema50 = computeEma(closes, 50)
        val (bbUpper, bbMiddle, bbLower) = computeBollingerBands(closes, 20, 2.0)
        val rsi14 = computeRsi(closes, 14)
        val (macdLine, macdSignal, macdHist) = computeMacd(closes)
        val atr14 = computeAtr(candles, 14)
        val fib = computeFibonacci(candles)
        val zones = detectSupportResistance(candles)
        val patterns = detectCandlestickPatterns(candles)

        val latestRsi = rsi14.lastOrNull { it != null } ?: 50.0
        val latestMacdHist = macdHist.lastOrNull { it != null } ?: 0.0
        val latestEma20 = ema20.lastOrNull { it != null }
        val latestEma50 = ema50.lastOrNull { it != null }
        val latestClose = closes.last()

        val trendBias = when {
            latestEma20 != null && latestEma50 != null && latestClose > latestEma20 && latestEma20 > latestEma50 ->
                "Bullish Structure"
            latestEma20 != null && latestEma50 != null && latestClose < latestEma20 && latestEma20 < latestEma50 ->
                "Bearish Structure"
            else -> "Consolidation / Mixed"
        }

        return TechnicalSnapshot(
            ema20 = ema20,
            ema50 = ema50,
            bbUpper = bbUpper,
            bbMiddle = bbMiddle,
            bbLower = bbLower,
            rsi14 = rsi14,
            macdLine = macdLine,
            macdSignal = macdSignal,
            macdHist = macdHist,
            atr14 = atr14,
            fibonacci = fib,
            zones = zones,
            patterns = patterns,
            latestRsi = latestRsi,
            latestMacdHist = latestMacdHist,
            trendBias = trendBias
        )
    }

    fun computeEma(values: List<Double>, period: Int): List<Double?> {
        if (values.size < period || period <= 0) return List(values.size) { null }
        val result = MutableList<Double?>(values.size) { null }
        val k = 2.0 / (period + 1.0)
        var ema = values.take(period).average()
        result[period - 1] = ema
        for (i in period until values.size) {
            ema = (values[i] - ema) * k + ema
            result[i] = ema
        }
        return result
    }

    private fun computeBollingerBands(
        values: List<Double>,
        period: Int,
        stdMultiplier: Double
    ): Triple<List<Double?>, List<Double?>, List<Double?>> {
        val upper = MutableList<Double?>(values.size) { null }
        val mid = MutableList<Double?>(values.size) { null }
        val lower = MutableList<Double?>(values.size) { null }
        if (values.size < period) return Triple(upper, mid, lower)

        for (i in (period - 1) until values.size) {
            val window = values.subList(i - period + 1, i + 1)
            val mean = window.average()
            val variance = window.sumOf { (it - mean) * (it - mean) } / period
            val stdDev = sqrt(variance)
            mid[i] = mean
            upper[i] = mean + stdMultiplier * stdDev
            lower[i] = mean - stdMultiplier * stdDev
        }
        return Triple(upper, mid, lower)
    }

    private fun computeRsi(closes: List<Double>, period: Int): List<Double?> {
        if (closes.size <= period) return List(closes.size) { null }
        val result = MutableList<Double?>(closes.size) { null }
        var avgGain = 0.0
        var avgLoss = 0.0
        for (i in 1..period) {
            val change = closes[i] - closes[i - 1]
            if (change >= 0) avgGain += change else avgLoss += abs(change)
        }
        avgGain /= period
        avgLoss /= period
        result[period] = if (avgLoss == 0.0) 100.0 else 100.0 - (100.0 / (1.0 + avgGain / avgLoss))

        for (i in (period + 1) until closes.size) {
            val change = closes[i] - closes[i - 1]
            val gain = if (change > 0) change else 0.0
            val loss = if (change < 0) abs(change) else 0.0
            avgGain = ((avgGain * (period - 1)) + gain) / period
            avgLoss = ((avgLoss * (period - 1)) + loss) / period
            result[i] = if (avgLoss == 0.0) 100.0 else 100.0 - (100.0 / (1.0 + avgGain / avgLoss))
        }
        return result
    }

    private fun computeMacd(
        closes: List<Double>
    ): Triple<List<Double?>, List<Double?>, List<Double?>> {
        val ema12 = computeEma(closes, 12)
        val ema26 = computeEma(closes, 26)
        val macdLine = MutableList<Double?>(closes.size) { null }
        val validMacdValues = mutableListOf<Double>()
        val validIndices = mutableListOf<Int>()

        for (i in closes.indices) {
            val fast = ema12[i]
            val slow = ema26[i]
            if (fast != null && slow != null) {
                val diff = fast - slow
                macdLine[i] = diff
                validMacdValues.add(diff)
                validIndices.add(i)
            }
        }

        val signalCompact = computeEma(validMacdValues, 9)
        val signalLine = MutableList<Double?>(closes.size) { null }
        val histogram = MutableList<Double?>(closes.size) { null }

        for (idx in validIndices.indices) {
            val origIdx = validIndices[idx]
            val sig = signalCompact[idx]
            val m = macdLine[origIdx]
            signalLine[origIdx] = sig
            if (sig != null && m != null) {
                histogram[origIdx] = m - sig
            }
        }
        return Triple(macdLine, signalLine, histogram)
    }

    private fun computeAtr(candles: List<Candle>, period: Int): Double {
        if (candles.size < 2) return 0.0
        val trueRanges = mutableListOf<Double>()
        for (i in 1 until candles.size) {
            val current = candles[i]
            val prevClose = candles[i - 1].close
            val tr = max(
                current.high - current.low,
                max(abs(current.high - prevClose), abs(current.low - prevClose))
            )
            trueRanges.add(tr)
        }
        return trueRanges.takeLast(period).average()
    }

    private fun computeFibonacci(candles: List<Candle>): FibonacciLevels? {
        if (candles.size < 20) return null
        val lookback = candles.takeLast(60)
        var highIdx = 0
        var lowIdx = 0
        var swingHigh = lookback[0].high
        var swingLow = lookback[0].low
        lookback.forEachIndexed { index, candle ->
            if (candle.high > swingHigh) {
                swingHigh = candle.high
                highIdx = index
            }
            if (candle.low < swingLow) {
                swingLow = candle.low
                lowIdx = index
            }
        }
        val range = swingHigh - swingLow
        if (range <= 0.0) return null
        val isUptrend = lowIdx < highIdx
        val ratios = listOf("0.236" to 0.236, "0.382" to 0.382, "0.500" to 0.500, "0.618" to 0.618, "0.786" to 0.786)
        val levels = ratios.associate { (label, ratio) ->
            val price = if (isUptrend) swingHigh - range * ratio else swingLow + range * ratio
            label to price
        }
        return FibonacciLevels(
            swingHigh = swingHigh,
            swingLow = swingLow,
            isUptrend = isUptrend,
            levels = levels
        )
    }

    private fun detectSupportResistance(candles: List<Candle>): List<PriceZone> {
        if (candles.size < 15) return emptyList()
        val window = candles.takeLast(80)
        val latestClose = window.last().close
        val pivots = mutableListOf<Double>()
        for (i in 2 until window.size - 2) {
            val c = window[i]
            val isPivotHigh = c.high > window[i - 1].high && c.high > window[i - 2].high &&
                c.high > window[i + 1].high && c.high > window[i + 2].high
            val isPivotLow = c.low < window[i - 1].low && c.low < window[i - 2].low &&
                c.low < window[i + 1].low && c.low < window[i + 2].low
            if (isPivotHigh) pivots.add(c.high)
            if (isPivotLow) pivots.add(c.low)
        }
        if (pivots.isEmpty()) return emptyList()

        val tolerance = max(latestClose * 0.0018, 1e-5)
        val clusters = mutableListOf<MutableList<Double>>()
        for (pivot in pivots.sorted()) {
            val matched = clusters.firstOrNull { cluster ->
                abs(cluster.average() - pivot) <= tolerance
            }
            if (matched != null) matched.add(pivot)
            else clusters.add(mutableListOf(pivot))
        }

        return clusters
            .map { cluster ->
                val avgPrice = cluster.average()
                PriceZone(
                    price = avgPrice,
                    isResistance = avgPrice >= latestClose,
                    touches = cluster.size
                )
            }
            .sortedByDescending { it.touches }
            .take(4)
    }

    private fun detectCandlestickPatterns(candles: List<Candle>): List<CandlestickPattern> {
        if (candles.size < 3) return emptyList()
        val found = mutableListOf<CandlestickPattern>()
        val start = max(1, candles.size - 12)
        for (i in start until candles.size) {
            val prev = candles[i - 1]
            val curr = candles[i]
            val body = abs(curr.close - curr.open)
            val range = max(curr.high - curr.low, 1e-9)
            val upperWick = curr.high - max(curr.open, curr.close)
            val lowerWick = min(curr.open, curr.close) - curr.low

            if (body / range < 0.12) {
                found.add(
                    CandlestickPattern(
                        name = "Doji Indecision",
                        bias = "Neutral",
                        candleIndex = i,
                        description = "Tight body indicates equilibrium between buyers and sellers."
                    )
                )
            } else if (!prev.isBullish && curr.isBullish &&
                curr.close >= prev.open && curr.open <= prev.close && body > abs(prev.close - prev.open)
            ) {
                found.add(
                    CandlestickPattern(
                        name = "Bullish Engulfing",
                        bias = "Bullish",
                        candleIndex = i,
                        description = "Bullish candle body engulfed prior bearish bar."
                    )
                )
            } else if (prev.isBullish && !curr.isBullish &&
                curr.close <= prev.open && curr.open >= prev.close && body > abs(prev.close - prev.open)
            ) {
                found.add(
                    CandlestickPattern(
                        name = "Bearish Engulfing",
                        bias = "Bearish",
                        candleIndex = i,
                        description = "Bearish candle body engulfed prior bullish bar."
                    )
                )
            } else if (lowerWick > body * 2.1 && upperWick < body * 0.6) {
                found.add(
                    CandlestickPattern(
                        name = "Hammer Rejection",
                        bias = "Bullish",
                        candleIndex = i,
                        description = "Long lower wick shows intraday downside rejection."
                    )
                )
            } else if (upperWick > body * 2.1 && lowerWick < body * 0.6) {
                found.add(
                    CandlestickPattern(
                        name = "Shooting Star",
                        bias = "Bearish",
                        candleIndex = i,
                        description = "Long upper wick signals supply rejection near highs."
                    )
                )
            }
        }
        return found.takeLast(4).reversed()
    }
}
