package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CandleEntity::class], version = 1, exportSchema = false)
abstract class ApexCacheDatabase : RoomDatabase() {
    abstract fun candleDao(): CandleDao

    companion object {
        @Volatile
        private var instance: ApexCacheDatabase? = null

        fun getInstance(context: Context): ApexCacheDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ApexCacheDatabase::class.java,
                    "apexdesk_ohlcv_cache.db"
                ).build().also { instance = it }
            }
        }
    }
}
