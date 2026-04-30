# Task Result: TASK-20260428-025 AlarmManager Scheduler + Receiver Skeleton

Task ID: TASK-20260428-025
Status: IMPLEMENTED

## Changed Files

### New files
- `app/src/main/java/com/dailynewsletter/alarm/AlarmScheduler.kt`
  - `@Singleton` Hilt class injecting `@ApplicationContext` + `SettingsRepository`
  - `suspend fun reschedule()`: reads hour/minute/days from repo, computes next trigger, calls `setAlarmClock`, or cancels if days empty
  - `internal fun computeNextTrigger(...)`: searches up to 7 days forward from now for the next matching DayOfWeek
  - PendingIntent targeting `AlarmReceiver`, requestCode=1001
- `app/src/main/java/com/dailynewsletter/alarm/AlarmReceiver.kt`
  - `@AndroidEntryPoint` BroadcastReceiver
  - `onReceive`: logs fired timestamp, calls `alarmScheduler.reschedule()` via `goAsync()` + `CoroutineScope(Dispatchers.IO)`
  - TODO comment for TASK-026 AlarmService start
- `app/src/main/java/com/dailynewsletter/alarm/BootReceiver.kt`
  - `@AndroidEntryPoint` BroadcastReceiver
  - Handles `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`
  - Reschedules alarm via `goAsync()` + coroutine

### Modified files
- `app/src/main/AndroidManifest.xml`
  - Added 4 permissions: `WAKE_LOCK`, `USE_FULL_SCREEN_INTENT`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`
  - Added `<receiver>` for `AlarmReceiver` (exported=false) and `BootReceiver` (exported=true with intent-filter)
- `app/src/main/java/com/dailynewsletter/ui/settings/SettingsViewModel.kt`
  - Added `AlarmScheduler` constructor injection
  - Added `alarmScheduler.reschedule()` call at end of `setAlarmHour`, `setAlarmMinute`, `toggleAlarmDay` coroutine blocks

## Behavior Changed
- Saving alarm hour/minute/days in Settings now immediately schedules (or cancels) an `AlarmManager.setAlarmClock` alarm
- Device reboot or app reinstall triggers rescheduling via `BootReceiver`
- When the alarm fires, `AlarmReceiver` logs the event and reschedules the next occurrence
- No sound/notification/print triggered yet (TASK-026 placeholder TODO in AlarmReceiver)

## Tests Added or Updated
- None added (no existing unit test infrastructure for alarm module; unit test for `computeNextTrigger` is straightforward to add in a follow-up)

## Commands Run
- `grep -rn "setAlarmClock|AlarmScheduler|AlarmReceiver|BootReceiver" app/src/main` — all 11 hits confirmed
- `grep -n "USE_FULL_SCREEN_INTENT|WAKE_LOCK|FOREGROUND_SERVICE" app/src/main/AndroidManifest.xml` — all 4 lines confirmed
- `./gradlew :app:assembleDebug` — SKIPPED_ENVIRONMENT_NOT_AVAILABLE (bash permission denied in this session; build must be run from Android Studio or terminal)

## Notes for Verifier

Important files:
- `/app/src/main/java/com/dailynewsletter/alarm/AlarmScheduler.kt`
- `/app/src/main/java/com/dailynewsletter/alarm/AlarmReceiver.kt`
- `/app/src/main/java/com/dailynewsletter/alarm/BootReceiver.kt`
- `/app/src/main/AndroidManifest.xml`
- `/app/src/main/java/com/dailynewsletter/ui/settings/SettingsViewModel.kt`

Suggested checks:
1. Build: `./gradlew :app:assembleDebug`
2. Grep: `grep -rn "setAlarmClock" app/src/main` should hit AlarmScheduler.kt line 53
3. Grep: `grep -n "alarmScheduler.reschedule" app/src/main/java/com/dailynewsletter/ui/settings/SettingsViewModel.kt` should show 3 hits
4. Manual smoke test: install on device, open Settings, set alarm time 1-2 minutes ahead, check today's day is selected, watch logcat for `AlarmScheduler: scheduled at ...`. When time arrives, `AlarmReceiver: alarm fired at ...` should appear.

Known limitations:
- `computeNextTrigger` uses `ZonedDateTime.now()` (system default timezone) — appropriate for a local alarm clock
- No fallback to `setExactAndAllowWhileIdle` for OEMs that may suppress `setAlarmClock` (Xiaomi MIUI, etc.) — escalation point if needed per Brief
