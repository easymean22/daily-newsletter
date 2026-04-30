package com.dailynewsletter.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dailynewsletter.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val TAG = "AlarmScheduler"
        private const val REQUEST_CODE = 1001
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    suspend fun reschedule() {
        val hour = settingsRepository.getPrintTimeHour()
        val minute = settingsRepository.getPrintTimeMinute()
        val days = settingsRepository.getAlarmDays()

        val pendingIntent = buildPendingIntent()

        if (days.isEmpty()) {
            alarmManager.cancel(pendingIntent)
            Log.d(TAG, "no alarm days configured — alarm cancelled")
            return
        }

        val now = ZonedDateTime.now()
        val triggerMillis = computeNextTrigger(now, hour, minute, days)

        if (triggerMillis == null) {
            alarmManager.cancel(pendingIntent)
            Log.d(TAG, "no next trigger computed — alarm cancelled")
            return
        }

        val triggerInstant = java.time.Instant.ofEpochMilli(triggerMillis)
        val openIntent = buildOpenIntent()
        val clockInfo = AlarmManager.AlarmClockInfo(triggerMillis, openIntent)
        alarmManager.setAlarmClock(clockInfo, pendingIntent)
        Log.d(TAG, "scheduled at $triggerInstant")
    }

    internal fun computeNextTrigger(
        now: ZonedDateTime,
        hour: Int,
        minute: Int,
        days: Set<DayOfWeek>
    ): Long? {
        if (days.isEmpty()) return null

        // Start from today's candidate time (truncate seconds/nanos)
        var candidate = now
            .withHour(hour)
            .withMinute(minute)
            .withSecond(0)
            .withNano(0)

        // If today's time has already passed, start looking from tomorrow
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusDays(1)
        }

        // Search up to 7 days ahead for the next matching day
        repeat(7) {
            if (candidate.dayOfWeek in days) {
                return candidate.toInstant().toEpochMilli()
            }
            candidate = candidate.plusDays(1)
        }

        return null
    }

    private fun buildPendingIntent(): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildOpenIntent(): PendingIntent {
        // Opens the app when user taps the clock entry in system alarm clock UI
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent()
        return PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
