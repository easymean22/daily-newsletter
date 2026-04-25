package com.dailynewsletter.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dailynewsletter.DailyNewsletterApp
import com.dailynewsletter.MainActivity
import com.dailynewsletter.R
import com.dailynewsletter.service.TopicSelectionService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class DailyTopicWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val topicSelectionService: TopicSelectionService
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            topicSelectionService.selectAndSaveTopics()
            showNotification("오늘의 주제가 선정되었습니다", "탭하여 확인하고 수정할 수 있습니다")
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                showNotification("주제 선정 실패", e.message ?: "알 수 없는 오류")
                Result.failure()
            }
        }
    }

    private fun showNotification(title: String, message: String) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, DailyNewsletterApp.CHANNEL_TOPICS)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1001, notification)
    }
}
