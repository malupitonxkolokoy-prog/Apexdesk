package com.example.data.remote

import android.util.Log
import com.example.data.model.AiTradeIdea
import com.example.data.model.Candle
import com.example.data.model.MarketSymbol
import com.example.data.model.TechnicalSnapshot
import com.example.data.model.Timeframe
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.Locale

class GeminiChartAnalyzer {

    private val setupSchema = Schema.obj(
        properties = mapOf(
            "bias" to Schema.enumeration(
                values = listOf("BULLISH", "BEARISH", "NEUTRAL"),
                description = "Overall probabilistic directional bias based on structure and confluence"
            ),
            "confidencePercent" to Schema.integer(
                description = "Realistic probabilistic confluence confidence score between 25 and 85"
            ),
            "riskRewardRatio" to Schema.double(
                description = "Projected reward-to-risk multiple (e.g. 2.1)"
            ),
            "suggestedEntry" to Schema.double(
                description = "Suggested entry price level"
            ),
            "stopLoss" to Schema.double(
                description = "Technical stop-loss level beyond structural invalidation"
            ),
            "takeProfit" to Schema.double(
                description = "Technical take-profit target near opposing liquidity or S/R zone"
            ),
            "confluenceFactors" to Schema.array(
                items = Schema.string(),
                description = "3 to 5 concise technical confluence observations"
            ),
            "invalidationCondition" to Schema.string(
                description = "Exact price/candle condition that invalidates this setup"
            ),
            "reasoning" to Schema.string(
                description = "Simple-language explanation of chart structure, momentum, and volatility"
            ),
            "riskNote" to Schema.string(
                description = "Reminder emphasizing probability, stop-loss discipline, and non-guaranteed outcomes"
            )
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = setupSchema
                temperature = 0.2f
            }
        )
    }

    suspend fun analyzeChart(
        symbol: MarketSymbol,
        timeframe: Timeframe,
        candles: List<Candle>,
        snapshot: TechnicalSnapshot,
        minRiskReward: Double
    ): Result<AiTradeIdea> {
        if (candles.size < 20) {
            return Result.failure(IllegalStateException("Insufficient OHLCV candles for analysis."))
        }

        val last = candles.last()
        val recentBars = candles.takeLast(12).joinToString("\n") { c ->
            String.format(
                Locale.US,
                "O=%.5f H=%.5f L=%.5f C=%.5f V=%.0f",
                c.open, c.high, c.low, c.close, c.volume
            )
        }
        val zonesSummary = if (snapshot.zones.isEmpty()) "None detected"
        else snapshot.zones.joinToString("; ") { z ->
            val kind = if (z.isResistance) "Resistance" else "Support"
            "$kind @ ${String.format(Locale.US, "%.${symbol.digits}f", z.price)} (${z.touches}x)"
        }
        val patternsSummary = if (snapshot.patterns.isEmpty()) "None"
        else snapshot.patterns.joinToString("; ") { "${it.name} (${it.bias})" }

        val prompt = """
            You are an institutional market structure analyst assisting a disciplined MetaTrader 5 trader.
            Analyze the telemetry below for ${symbol.ticker} on the ${timeframe.label} timeframe.
            
            RULES:
            - Never claim guaranteed profitability or certainty. Use realistic probabilistic framing (confidencePercent between 25 and 85).
            - Suggested entry, stopLoss, and takeProfit must be realistic relative to current price ${String.format(Locale.US, "%.${symbol.digits}f", last.close)} and ATR(14) = ${String.format(Locale.US, "%.${symbol.digits}f", snapshot.atr14)}.
            - Aim for riskRewardRatio >= ${String.format(Locale.US, "%.1f", minRiskReward)} when bias is BULLISH or BEARISH; if structure is unclear, output NEUTRAL.
            - Explain reasoning in clear, simple language suitable for decision support.
            
            CURRENT TELEMETRY:
            - Current Close: ${String.format(Locale.US, "%.${symbol.digits}f", last.close)}
            - Trend Structure (EMA 20 vs 50): ${snapshot.trendBias}
            - EMA20: ${snapshot.ema20.lastOrNull()?.let { String.format(Locale.US, "%.${symbol.digits}f", it) } ?: "N/A"}
            - EMA50: ${snapshot.ema50.lastOrNull()?.let { String.format(Locale.US, "%.${symbol.digits}f", it) } ?: "N/A"}
            - RSI(14): ${String.format(Locale.US, "%.1f", snapshot.latestRsi)}
            - MACD Histogram: ${String.format(Locale.US, "%.6f", snapshot.latestMacdHist)}
            - ATR(14) Volatility: ${String.format(Locale.US, "%.${symbol.digits}f", snapshot.atr14)}
            - Key Support/Resistance Zones: $zonesSummary
            - Recent Candlestick Patterns: $patternsSummary
            
            LAST 12 CANDLES:
            $recentBars
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                model.generateContent(prompt)
            }
            val jsonText = response.text?.takeIf { it.isNotBlank() }
                ?: return Result.failure(IllegalStateException("Gemini returned an empty analysis response."))

            val obj = JSONObject(jsonText)
            val bias = obj.optString("bias", "NEUTRAL").uppercase(Locale.US)
            val confidence = obj.optInt("confidencePercent", 50).coerceIn(15, 88)
            val rr = obj.optDouble("riskRewardRatio", 1.5).coerceIn(0.5, 10.0)
            val entry = obj.optDouble("suggestedEntry", last.close)
            val atrFallback = if (snapshot.atr14 > 0.0) snapshot.atr14 else last.close * 0.002
            val defaultSl = if (bias == "SELL" || bias == "BEARISH") entry + atrFallback * 1.5 else entry - atrFallback * 1.5
            val defaultTp = if (bias == "SELL" || bias == "BEARISH") entry - atrFallback * 3.0 else entry + atrFallback * 3.0
            val sl = obj.optDouble("stopLoss", defaultSl)
            val tp = obj.optDouble("takeProfit", defaultTp)

            val factorsJson = obj.optJSONArray("confluenceFactors")
            val factors = if (factorsJson != null) {
                (0 until factorsJson.length()).mapNotNull { idx ->
                    factorsJson.optString(idx).takeIf { it.isNotBlank() }
                }
            } else {
                emptyList()
            }

            Result.success(
                AiTradeIdea(
                    symbol = symbol.ticker,
                    timeframe = timeframe.label,
                    bias = bias,
                    confidencePercent = confidence,
                    riskRewardRatio = rr,
                    suggestedEntry = entry,
                    stopLoss = sl,
                    takeProfit = tp,
                    confluenceFactors = factors,
                    invalidationCondition = obj.optString(
                        "invalidationCondition",
                        "Setup invalidated on a decisive close beyond stop-loss structure."
                    ),
                    reasoning = obj.optString(
                        "reasoning",
                        "Structure evaluated using trend, momentum, and volatility confluence."
                    ),
                    riskNote = obj.optString(
                        "riskNote",
                        "Probabilistic decision-support only. Past performance does not guarantee future results."
                    ),
                    generatedAtMs = System.currentTimeMillis()
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Chart analysis timed out", e)
            Result.failure(Exception("AI analysis timed out after 60 seconds. Please retry."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini chart analysis failed", e)
            Result.failure(Exception(e.localizedMessage ?: "AI chart analysis failed."))
        }
    }

    private companion object {
        const val TAG = "GeminiChartAnalyzer"
    }
}
