package com.dailynewsletter.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dailynewsletter.service.NewsletterGenerationService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class NewsletterWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val newsletterGenerationService: NewsletterGenerationService
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            newsletterGenerationService.generateAndSaveNewsletter()
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
