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
import com.dailynewsletter.data.repository.NewsletterRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class PrintWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val newsletterRepository: NewsletterRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val newsletterId = inputData.getString("newsletter_id") ?: return Result.failure()

        return try {
            newsletterRepository.printNewsletter(newsletterId)
            showNotification("프린트 완료", "오늘의 뉴스레터가 프린트되었습니다")
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                showNotification("프린트 실패", "탭하여 재시도하세요: ${e.message}")
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

        val notification = NotificationCompat.Builder(applicationContext, DailyNewsletterApp.CHANNEL_PRINT)
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1002, notification)
    }
}
