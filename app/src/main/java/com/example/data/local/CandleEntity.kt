package com.example.data.local

import androidx.room.Entity
import com.example.data.model.Candle

@Entity(
    tableName = "cached_candles",
    primaryKeys = ["symbol", "timeframe", "timestampMs"]
)
data class CandleEntity(
    val symbol: String,
    val timeframe: String,
    val timestampMs: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val cachedAtMs: Long
) {
    fun toDomain(): Candle = Candle(
        timestampMs = timestampMs,
        open = open,
        high = high,
        low = low,
        close = close,
        volume = volume
    )

    companion object {
        fun fromDomain(symbol: String, timeframe: String, candle: Candle, cachedAtMs: Long): CandleEntity =
            CandleEntity(
                symbol = symbol,
                timeframe = timeframe,
                timestampMs = candle.timestampMs,
                open = candle.open,
                high = candle.high,
                low = candle.low,
                close = candle.close,
                volume = candle.volume,
                cachedAtMs = cachedAtMs
            )
    }
}
