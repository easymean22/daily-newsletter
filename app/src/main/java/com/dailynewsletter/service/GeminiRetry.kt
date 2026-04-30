package com.dailynewsletter.service

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import retrofit2.HttpException

sealed class RetryEvent {
    data class Retrying(
        val label: String,
        val attempt: Int,
        val totalAttempts: Int,
        val delayMs: Long
    ) : RetryEvent()

    data class Switching(
        val label: String,
        val toModel: String
    ) : RetryEvent()
}

object GeminiRetry {
    private const val TAG = "GeminiRetry"
    private val DELAYS_MS = longArrayOf(1500, 3000, 6000, 12000)

    private val _events = MutableSharedFlow<RetryEvent>(replay = 0, extraBufferCapacity = 8)
    val events: SharedFlow<RetryEvent> = _events.asSharedFlow()

    suspend fun <T> withRetry(label: String, block: suspend () -> T): T {
        var lastError: Throwable? = null
        for (attempt in 0..DELAYS_MS.size) {
            try {
                return block()
            } catch (e: HttpException) {
                lastError = e
                val code = e.code()
                val transient = code == 503 || code == 502 || code == 504
                if (!transient || attempt == DELAYS_MS.size) {
                    throw if (transient) IllegalStateException(
                        "잠시 후 다시 시도해 주세요"
                    ) else e
                }
                val delayMs = DELAYS_MS[attempt] + (0..500L).random()
                Log.w(TAG, "$label HTTP $code, retry in ${delayMs}ms (attempt ${attempt + 1})")
                _events.tryEmit(RetryEvent.Retrying(label, attempt + 1, DELAYS_MS.size, delayMs))
                delay(delayMs)
            }
        }
        throw lastError ?: IllegalStateException("$label failed unexpectedly")
    }

    suspend fun <T> withModelFallback(
        label: String,
        primaryModel: String,
        fallbackModel: String,
        block: suspend (model: String) -> T
    ): T {
        return try {
            withRetry(label) { block(primaryModel) }
        } catch (e: IllegalStateException) {
            // primary backoff exhausted (transient)
            Log.w(TAG, "$label primary $primaryModel exhausted, fallback → $fallbackModel")
            _events.tryEmit(RetryEvent.Switching(label, fallbackModel))
            withRetry("$label-fallback") { block(fallbackModel) }
        }
    }
}
