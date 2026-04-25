package com.dailynewsletter

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DailyNewsletterApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val topicChannel = NotificationChannel(
            CHANNEL_TOPICS,
            "주제 선정 알림",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "오늘의 주제가 선정되었을 때 알림"
        }

        val printChannel = NotificationChannel(
            CHANNEL_PRINT,
            "프린트 알림",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "프린트 성공/실패 알림"
        }

        manager.createNotificationChannel(topicChannel)
        manager.createNotificationChannel(printChannel)
    }

    companion object {
        const val CHANNEL_TOPICS = "topics"
        const val CHANNEL_PRINT = "print"
    }
}
