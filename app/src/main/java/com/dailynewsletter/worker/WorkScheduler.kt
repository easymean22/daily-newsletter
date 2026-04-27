package com.dailynewsletter.worker

import android.content.Context
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager = WorkManager.getInstance(context)

    fun scheduleAll() {
        // Cancel legacy workers that have been removed
        workManager.cancelUniqueWork("daily_topic_selection")
        workManager.cancelUniqueWork("newsletter_generation")

        scheduleCleanup()
    }

    private fun scheduleCleanup() {
        val delay = calculateDelayUntil(0, 0) // Midnight

        val request = PeriodicWorkRequestBuilder<CleanupWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag("cleanup")
            .build()

        workManager.enqueueUniquePeriodicWork(
            "daily_cleanup",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun calculateDelayUntil(hour: Int, minute: Int): Long {
        val now = LocalDateTime.now()
        var target = now.toLocalDate().atTime(hour, minute)
        if (target.isBefore(now)) {
            target = target.plusDays(1)
        }
        return Duration.between(now, target).toMillis()
    }

    fun cancelAll() {
        workManager.cancelAllWork()
    }
}
