package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CandleDao {
    @Query("SELECT * FROM cached_candles WHERE symbol = :symbol AND timeframe = :timeframe ORDER BY timestampMs ASC")
    fun observeCandles(symbol: String, timeframe: String): Flow<List<CandleEntity>>

    @Query("SELECT * FROM cached_candles WHERE symbol = :symbol AND timeframe = :timeframe ORDER BY timestampMs ASC")
    suspend fun getCandles(symbol: String, timeframe: String): List<CandleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCandles(candles: List<CandleEntity>)

    @Query("DELETE FROM cached_candles WHERE symbol = :symbol AND timeframe = :timeframe")
    suspend fun clearSeries(symbol: String, timeframe: String)
}
