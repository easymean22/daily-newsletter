package com.dailynewsletter.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.dailynewsletter.data.local.dao.SettingsDao
import com.dailynewsletter.data.local.entity.SettingsEntity

@Database(
    entities = [SettingsEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
}
