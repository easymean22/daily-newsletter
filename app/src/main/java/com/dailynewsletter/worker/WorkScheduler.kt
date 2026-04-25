package com.dailynewsletter.worker

import android.content.Context
import androidx.work.*
import com.dailynewsletter.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    private val workManager = WorkManager.getInstance(context)

    suspend fun scheduleAll() {
        val hour = settingsRepository.getPrintTimeHour()
        val minute = settingsRepository.getPrintTimeMinute()

        scheduleDailyTopicSelection(hour, minute)
        scheduleNewsletterGeneration(hour, minute)
        schedulePrint(hour, minute)
        scheduleCleanup()
    }

    private fun scheduleDailyTopicSelection(printHour: Int, printMinute: Int) {
        // 2 hours before print time
        val topicTime = LocalTime.of(printHour, printMinute).minusHours(2)
        val delay = calculateDelayUntil(topicTime.hour, topicTime.minute)

        val request = PeriodicWorkRequestBuilder<DailyTopicWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag("daily_topic")
            .build()

        workManager.enqueueUniquePeriodicWork(
            "daily_topic_selection",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun scheduleNewsletterGeneration(printHour: Int, printMinute: Int) {
        // 30 minutes before print time
        val genTime = LocalTime.of(printHour, printMinute).minusMinutes(30)
        val delay = calculateDelayUntil(genTime.hour, genTime.minute)

        val request = PeriodicWorkRequestBuilder<NewsletterWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag("newsletter_gen")
            .build()

        workManager.enqueueUniquePeriodicWork(
            "newsletter_generation",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun schedulePrint(printHour: Int, printMinute: Int) {
        val delay = calculateDelayUntil(printHour, printMinute)

        val request = PeriodicWorkRequestBuilder<PrintWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
            .addTag("print")
            .build()

        workManager.enqueueUniquePeriodicWork(
            "daily_print",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
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
