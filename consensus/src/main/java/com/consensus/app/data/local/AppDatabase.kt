package com.consensus.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.consensus.app.data.local.dao.ExchangeDao
import com.consensus.app.data.local.dao.ThreadDao
import com.consensus.app.data.local.entity.ExchangeEntity
import com.consensus.app.data.local.entity.ThreadEntity

@Database(
    entities = [ThreadEntity::class, ExchangeEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun threadDao(): ThreadDao
    abstract fun exchangeDao(): ExchangeDao
}
