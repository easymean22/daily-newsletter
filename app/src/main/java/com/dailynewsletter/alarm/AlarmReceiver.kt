package com.dailynewsletter.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var alarmScheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AlarmReceiver", "alarm fired at ${System.currentTimeMillis()}")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Schedule the next occurrence immediately after firing
                try { alarmScheduler.reschedule() } catch (t: Throwable) { Log.w("AlarmReceiver", "reschedule failed", t) }

                // Start AlarmService to play sound and show full-screen popup
                val serviceIntent = Intent(context, AlarmService::class.java)
                context.startForegroundService(serviceIntent)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
