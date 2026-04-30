# Task Brief: Exact alarm permission fix + alarm error label split

Task ID: TASK-20260430-040
Status: active

## Goal

Unblock TASK-027 device verification. Current symptom on user's device:

```
DB 생성 실패: Caller com.dailynewsletter needs to hold
android.permission.SCHEDULE_EXACT_ALARM or android.permission.USE_EXACT_ALARM
to set exact alarms.
```

Two distinct bugs cause this:

1. **Permission gap.** Manifest only declares `SCHEDULE_EXACT_ALARM` (API 31-32, requires user to manually grant via system Settings). It does NOT declare `USE_EXACT_ALARM` (API 33+, auto-granted). On any modern device the alarm scheduling path throws `SecurityException` from `AlarmManager.setAlarmClock`.
2. **Misleading error label.** `SettingsViewModel.exceptionHandler` is shared across `runSetup()`, `setAlarmHour()`, `setAlarmMinute()`, `toggleAlarmDay()`. Any exception in any of those paths flows to `setupResult = SetupResult.Failed(...)`, which `SettingsScreen.kt:63` displays with prefix `"DB 생성 실패: "`. Alarm permission failures get falsely labeled as DB setup failures.

Device Android version is unknown — fix must work for API 26 (no permission needed), API 31-32 (`SCHEDULE_EXACT_ALARM` requires explicit user grant via system Settings), and API 33+ (`USE_EXACT_ALARM` auto-granted).

## User-visible behavior

After fix:

- API 33+ device: user toggles alarm time/day → alarm is scheduled silently. No error.
- API 31-32 device, permission granted in system Settings → alarm scheduled silently. No error.
- API 31-32 device, permission NOT granted → snackbar `"알람 권한이 필요합니다"` + button `"권한 설정 열기"`. Tapping the button launches `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM` system page. Returning to the app and toggling again succeeds.
- DB setup failures still show `"DB 생성 실패: ..."`. Alarm failures show `"알람 설정 실패: ..."`. The two channels are visually distinct in the snackbar text.

## Scope

### 1. `app/src/main/AndroidManifest.xml`

Add (keep existing `SCHEDULE_EXACT_ALARM`):

```xml
<uses-permission android:name="android.permission.USE_EXACT_ALARM" />
```

Place adjacent to the existing `SCHEDULE_EXACT_ALARM` line. No other manifest changes.

### 2. `app/src/main/java/com/dailynewsletter/alarm/AlarmScheduler.kt`

- Add a sealed return type:
  ```kotlin
  sealed class RescheduleResult {
      object Scheduled : RescheduleResult()
      object Cancelled : RescheduleResult()       // days empty or no next trigger
      object PermissionRequired : RescheduleResult() // canScheduleExactAlarms == false on API 31+
      data class Failed(val message: String) : RescheduleResult()
  }
  ```
- Change `suspend fun reschedule()` to `suspend fun reschedule(): RescheduleResult`.
- Inside `reschedule()`, **before** calling `alarmManager.setAlarmClock(...)`:
  ```kotlin
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
      && !alarmManager.canScheduleExactAlarms()) {
      Log.w(TAG, "exact alarm permission not granted")
      return RescheduleResult.PermissionRequired
  }
  ```
- Wrap the `setAlarmClock` call in try/catch for `SecurityException` → return `Failed(e.message)`. Catch only `SecurityException`, let other exceptions propagate.
- Existing log statements remain.
- All four early-exit paths (`days.isEmpty()`, `triggerMillis == null`, permission, success) return the matching `RescheduleResult`.

### 3. `app/src/main/java/com/dailynewsletter/alarm/AlarmReceiver.kt`

- `AlarmReceiver` currently calls `alarmScheduler.reschedule()` without inspecting result. Keep the call but ignore the new return value (no UI in receiver). Wrap in try/catch as defensive measure to prevent receiver crash if permission is revoked between alarm fires:
  ```kotlin
  try { alarmScheduler.reschedule() } catch (t: Throwable) { Log.w(TAG, "reschedule failed", t) }
  ```
- No other changes.

### 4. `app/src/main/java/com/dailynewsletter/ui/settings/SettingsViewModel.kt`

- Add separate state for alarm errors (do NOT route through `_setupState`):
  ```kotlin
  sealed class AlarmFeedback {
      object Idle : AlarmFeedback()
      object PermissionRequired : AlarmFeedback()
      data class Failed(val message: String) : AlarmFeedback()
  }

  private val _alarmFeedback = MutableStateFlow<AlarmFeedback>(AlarmFeedback.Idle)
  val alarmFeedback: StateFlow<AlarmFeedback> = _alarmFeedback.asStateFlow()

  fun clearAlarmFeedback() { _alarmFeedback.value = AlarmFeedback.Idle }
  ```
- Replace each call to `alarmScheduler.reschedule()` in `setAlarmHour`, `setAlarmMinute`, `toggleAlarmDay` with a helper:
  ```kotlin
  private fun rescheduleWithFeedback() {
      viewModelScope.launch {
          try {
              when (val r = alarmScheduler.reschedule()) {
                  is RescheduleResult.PermissionRequired ->
                      _alarmFeedback.value = AlarmFeedback.PermissionRequired
                  is RescheduleResult.Failed ->
                      _alarmFeedback.value = AlarmFeedback.Failed(r.message)
                  RescheduleResult.Scheduled, RescheduleResult.Cancelled -> { /* silent */ }
              }
          } catch (e: SecurityException) {
              _alarmFeedback.value = AlarmFeedback.PermissionRequired
          } catch (e: Exception) {
              _alarmFeedback.value = AlarmFeedback.Failed(e.message ?: "알람 설정 실패")
          }
      }
  }
  ```
- The existing `setAlarmHour/setAlarmMinute/toggleAlarmDay` should:
  1. `settingsRepository.set...` (still inside `viewModelScope.launch(exceptionHandler)`),
  2. then call `rescheduleWithFeedback()` (its own scope so its errors don't leak into `_setupState`).
- Do NOT change the `runSetup()` function. The "DB 생성 실패" channel must remain intact for actual DB setup failures.

### 5. `app/src/main/java/com/dailynewsletter/ui/settings/SettingsScreen.kt`

- Collect `viewModel.alarmFeedback` as state.
- When `AlarmFeedback.PermissionRequired`: show snackbar
  - text = `"알람 권한이 필요합니다"`,
  - actionLabel = `"권한 설정 열기"`,
  - on action tap: launch
    ```kotlin
    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    context.startActivity(intent)
    ```
  - on dismiss/action: call `viewModel.clearAlarmFeedback()`.
- When `AlarmFeedback.Failed(msg)`: show snackbar `"알람 설정 실패: $msg"`. clear after.
- Existing `"DB 생성 실패: ..."` snackbar logic stays.
- API guard: `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM` is API 31+. The flow only fires when `PermissionRequired` is emitted, which itself is API 31+ guarded → safe. Still wrap the intent launch in try/catch and fall back to a plain snackbar `"권한 설정을 열 수 없습니다. 시스템 설정 → 앱 → 알람으로 이동해 주세요."` if `ActivityNotFoundException`.

## Out of Scope

- TASK-041 (alarm time picker UI rework — 12-hour / AM-PM / dial). Separate brief.
- Periodic re-check of permission state on app resume (e.g. observing `OnAlarmManagerExactAlarmPermissionStateChangedReceiver`). Not needed for MVP — user gets feedback when they next toggle alarm.
- Auto-retry after permission grant. User must toggle a setting again to retrigger.
- Test coverage. None of the existing alarm path has tests; do not introduce in this brief.

## Relevant Context

- Use existing patterns in:
  - `app/src/main/java/com/dailynewsletter/alarm/AlarmScheduler.kt`
  - `app/src/main/java/com/dailynewsletter/alarm/AlarmReceiver.kt`
  - `app/src/main/java/com/dailynewsletter/ui/settings/SettingsViewModel.kt`
  - `app/src/main/java/com/dailynewsletter/ui/settings/SettingsScreen.kt`
- Stable project summary:
  - `docs/context/project-map.md`
- Background:
  - TASK-20260428-024 (alarm settings persistence)
  - TASK-20260428-025 (AlarmManager scheduler + Receiver + Boot)
  - TASK-20260429-027 (alarm popup service — currently device-blocked by this bug)

## Files Likely Involved

- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/dailynewsletter/alarm/AlarmScheduler.kt`
- `app/src/main/java/com/dailynewsletter/alarm/AlarmReceiver.kt`
- `app/src/main/java/com/dailynewsletter/ui/settings/SettingsViewModel.kt`
- `app/src/main/java/com/dailynewsletter/ui/settings/SettingsScreen.kt`

## Files Owned By This Task

- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/dailynewsletter/alarm/AlarmScheduler.kt`
- `app/src/main/java/com/dailynewsletter/alarm/AlarmReceiver.kt`
- `app/src/main/java/com/dailynewsletter/ui/settings/SettingsViewModel.kt`
- `app/src/main/java/com/dailynewsletter/ui/settings/SettingsScreen.kt`

## Files Explicitly Not Owned

- `app/src/main/java/com/dailynewsletter/alarm/AlarmService.kt`
- `app/src/main/java/com/dailynewsletter/alarm/AlarmActivity.kt`
- All other files.

## Forbidden Changes

- No new dependency.
- No DB schema change.
- No `setAlarmClock` API replacement (still must be exact alarm — daily print accuracy depends on it).
- No removal of existing `SCHEDULE_EXACT_ALARM` permission.
- No public API change to `AlarmService` / `AlarmActivity`.
- No background scheduling policy change.
- No broad refactor.
- No formatting-only edits to unrelated parts of touched files.

## Android Constraints

- Use existing Kotlin style.
- Use existing Jetpack Compose patterns (Material3 + Snackbar via `SnackbarHostState`).
- Use existing ViewModel/Repository patterns (Hilt `@Inject`, `viewModelScope`).
- Keep UI state deterministic — `AlarmFeedback` is consumed once and cleared.
- Keep side effects outside composables — intent launch goes through `LaunchedEffect` + `context.startActivity`.
- API guard: `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S` (API 31) for `canScheduleExactAlarms()` and `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`.

## Acceptance Criteria

- [ ] `AndroidManifest.xml` declares both `SCHEDULE_EXACT_ALARM` and `USE_EXACT_ALARM`.
- [ ] `AlarmScheduler.reschedule()` returns `RescheduleResult` (sealed class, 4 cases).
- [ ] `AlarmScheduler.reschedule()` calls `canScheduleExactAlarms()` before `setAlarmClock()` on API 31+.
- [ ] `AlarmScheduler.reschedule()` catches `SecurityException` from `setAlarmClock` and returns `Failed`.
- [ ] `AlarmReceiver` wraps `reschedule()` in try/catch.
- [ ] `SettingsViewModel.alarmFeedback: StateFlow<AlarmFeedback>` is exposed.
- [ ] `SettingsViewModel.runSetup()` is unchanged in behavior — DB setup errors still flow to `setupResult` with prefix `"DB 생성 실패: "`.
- [ ] `SettingsScreen.kt` shows `"알람 권한이 필요합니다"` snackbar + `"권한 설정 열기"` action when feedback is `PermissionRequired`.
- [ ] `SettingsScreen.kt` shows `"알람 설정 실패: $msg"` snackbar when feedback is `Failed`.
- [ ] DB setup error path still produces `"DB 생성 실패: ..."` snackbar (no regression).
- [ ] `./gradlew :app:assembleDebug` succeeds, or `SKIPPED_ENVIRONMENT_NOT_AVAILABLE` if Bash permission denied.

## Verification Command Candidates

- `grep -n "USE_EXACT_ALARM\|SCHEDULE_EXACT_ALARM" app/src/main/AndroidManifest.xml`
- `grep -n "canScheduleExactAlarms\|RescheduleResult" app/src/main/java/com/dailynewsletter/alarm/AlarmScheduler.kt`
- `grep -n "AlarmFeedback\|alarmFeedback" app/src/main/java/com/dailynewsletter/ui/settings/SettingsViewModel.kt`
- `grep -n "ACTION_REQUEST_SCHEDULE_EXACT_ALARM\|알람 권한이 필요합니다\|알람 설정 실패\|DB 생성 실패" app/src/main/java/com/dailynewsletter/ui/settings/SettingsScreen.kt`
- `./gradlew :app:assembleDebug`

## Expected Implementer Output

- Changed files: 5 (1 manifest + 4 Kotlin).
- Behavior changed: alarm scheduling now reports its own error channel; DB setup label only fires for actual DB setup failures; permission denial routes to system Settings.
- Tests added/updated: none (consistent with project state).
- Commands run: grep verification + assembleDebug result.
- Notes for verifier: confirm runtime behavior on a device — toggle alarm day → expected silent success; revoke permission via system Settings → toggle again → expected `"알람 권한이 필요합니다"` snackbar with action button.

## STOP_AND_ESCALATE

- If `setAlarmClock` requires a different permission shape on a target API not covered above — escalate.
- If the existing `exceptionHandler` mechanism in `SettingsViewModel` cannot be cleanly bypassed for the alarm path without a broader refactor — escalate with options.
- If `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM` is not resolvable at all (very old device) — fall back to plain snackbar without action button. Do not escalate.
