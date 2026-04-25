package com.dailynewsletter.di

import android.content.Context
import androidx.room.Room
import com.dailynewsletter.data.local.AppDatabase
import com.dailynewsletter.data.local.dao.SettingsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "daily_newsletter.db")
            .build()

    @Provides
    fun provideSettingsDao(db: AppDatabase): SettingsDao = db.settingsDao()
}
