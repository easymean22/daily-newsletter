# Task Brief: Alarm time picker → 12-hour + AM/PM + NumberPicker dial

Task ID: TASK-20260430-041
Status: active

## Prerequisite

This task starts from the post-TASK-040 state of `SettingsScreen.kt`. Do NOT touch the `alarmFeedback` wiring or the `"알람 권한이 필요합니다"` / `"알람 설정 실패"` / `"DB 생성 실패"` snackbar logic introduced by TASK-040. This brief only swaps the alarm time-picker dialog body.

## Goal

Replace the Material3 `TimePicker(is24Hour = true)` analog clock face with a 3-column **wheel/dial NumberPicker** dialog:

- `[ Hour 1–12 ] [ Minute 0–59 ] [ AM / PM ]`
- Each column is an Android `android.widget.NumberPicker` (the classic wheel/dial widget) wrapped via `androidx.compose.ui.viewinterop.AndroidView`.
- Internal storage stays 24-hour (`alarmHour: 0..23`, `alarmMinute: 0..59`). The dialog converts 24h ↔ 12h+AM/PM at open and at confirm.

No new dependency. No change to `AlarmScheduler`, `SettingsViewModel.setAlarmHour/Minute`, `SettingsRepository`, or persisted format.

## User-visible behavior

- Tap the alarm-time card on Settings → dialog opens with title `"알람 시간 설정"`.
- Three vertical wheels side-by-side. The center row of each wheel shows the currently selected value, with adjacent values dimmed above and below. User scrolls (or taps the +/- arrows on the wheel) to change the value.
  - Hour wheel: 1, 2, 3, …, 12. Wraps from 12 → 1 if user keeps scrolling.
  - Minute wheel: 00, 01, …, 59. Wraps. Two-digit zero-padded display.
  - AM/PM wheel: AM, PM. Two-position only.
- Below the wheels: `취소` and `확인` buttons (existing pattern).
- `확인` tap → convert to 24-hour, call `viewModel.setAlarmHour(h24)` and `viewModel.setAlarmMinute(m)`, dismiss.
- `취소` tap → dismiss without saving.
- The Settings list card still displays time as `"%02d:%02d"` of the 24-hour stored value (no UI text change). Users see e.g. `"19:30"` on the card and `[7] [30] [PM]` in the dialog.

## Scope

### `app/src/main/java/com/dailynewsletter/ui/settings/SettingsScreen.kt`

1. Remove the imports `androidx.compose.material3.TimePicker` and `androidx.compose.material3.rememberTimePickerState`. Add imports:
   - `android.widget.NumberPicker`
   - `androidx.compose.ui.viewinterop.AndroidView`
   - any required `remember`/`mutableStateOf` already present.
2. Replace the existing alarm-time dialog (the `if (showAlarmTimePicker) { ... }` block, currently lines ~261-282) with the new 12-hour wheel dialog.
3. Add a private composable inside the same file:
   ```kotlin
   @Composable
   private fun AlarmTimeWheelDialog(
       initialHour24: Int,
       initialMinute: Int,
       onConfirm: (hour24: Int, minute: Int) -> Unit,
       onDismiss: () -> Unit,
   ) {
       // Convert 24h → 12h + AM/PM
       val initIs24Pm = initialHour24 >= 12
       val initHour12Display = ((initialHour24 + 11) % 12) + 1  // 0→12, 1→1, ..., 12→12, 13→1, ..., 23→11

       var hour12 by remember { mutableStateOf(initHour12Display) }
       var minute by remember { mutableStateOf(initialMinute) }
       var isPm by remember { mutableStateOf(initIs24Pm) }

       AlertDialog(
           onDismissRequest = onDismiss,
           title = { Text("알람 시간 설정") },
           text = {
               Row(
                   modifier = Modifier.fillMaxWidth(),
                   horizontalArrangement = Arrangement.SpaceEvenly,
                   verticalAlignment = Alignment.CenterVertically,
               ) {
                   AndroidView(
                       factory = { ctx ->
                           NumberPicker(ctx).apply {
                               minValue = 1
                               maxValue = 12
                               wrapSelectorWheel = true
                               value = hour12
                               setOnValueChangedListener { _, _, new -> hour12 = new }
                           }
                       },
                       update = { it.value = hour12 },
                   )
                   AndroidView(
                       factory = { ctx ->
                           NumberPicker(ctx).apply {
                               minValue = 0
                               maxValue = 59
                               wrapSelectorWheel = true
                               setFormatter { v -> "%02d".format(v) }
                               value = minute
                               setOnValueChangedListener { _, _, new -> minute = new }
                           }
                       },
                       update = { it.value = minute },
                   )
                   AndroidView(
                       factory = { ctx ->
                           NumberPicker(ctx).apply {
                               minValue = 0
                               maxValue = 1
                               displayedValues = arrayOf("AM", "PM")
                               wrapSelectorWheel = false
                               value = if (isPm) 1 else 0
                               setOnValueChangedListener { _, _, new -> isPm = new == 1 }
                           }
                       },
                       update = { it.value = if (isPm) 1 else 0 },
                   )
               }
           },
           confirmButton = {
               TextButton(onClick = {
                   // Convert 12h + AM/PM → 24h
                   val hour24 = when {
                       !isPm && hour12 == 12 -> 0
                       !isPm -> hour12
                       isPm && hour12 == 12 -> 12
                       else -> hour12 + 12
                   }
                   onConfirm(hour24, minute)
               }) { Text("확인") }
           },
           dismissButton = {
               TextButton(onClick = onDismiss) { Text("취소") }
           },
       )
   }
   ```
4. Replace the existing dialog block with:
   ```kotlin
   if (showAlarmTimePicker) {
       AlarmTimeWheelDialog(
           initialHour24 = state.alarmHour,
           initialMinute = state.alarmMinute,
           onConfirm = { h24, m ->
               viewModel.setAlarmHour(h24)
               viewModel.setAlarmMinute(m)
               showAlarmTimePicker = false
           },
           onDismiss = { showAlarmTimePicker = false },
       )
   }
   ```

### Conversion correctness (manual proof — do NOT skip during review)

24h → 12h + AM/PM (open):
- `00:xx` → `12 AM xx`
- `01..11:xx` → `H AM xx`
- `12:xx` → `12 PM xx`
- `13..23:xx` → `H-12 PM xx`

12h + AM/PM → 24h (confirm):
- `12 AM` → `00`
- `1..11 AM` → `1..11`
- `12 PM` → `12`
- `1..11 PM` → `13..23`

Both transforms must round-trip identically for all 1440 minutes of the day. Implementer must include a small Kotlin pure-function `fun convert24To12(h24: Int): Triple<Int, Int, Boolean>` and `fun convert12To24(h12: Int, isPm: Boolean): Int` extracted into the same file as private helpers, used inside the composable.

## Out of Scope

- Permission/error flow (TASK-040).
- Snackbar wording for alarm feedback (TASK-040).
- Settings list card display format (stays `%02d:%02d` 24h).
- Persistence format change (still 24h hour + minute).
- Per-weekday-time UI (separate `weekday-print-slots` plan).
- AlarmScheduler / AlarmReceiver / Manifest changes.
- Tests.

## Relevant Context

- Existing dialog code in `app/src/main/java/com/dailynewsletter/ui/settings/SettingsScreen.kt` (lines ~261-282 pre-TASK-040; line numbers may shift after TASK-040 lands).
- Existing imports in the same file already include AlertDialog, TextButton, Text, Row, Modifier, Alignment.
- Stable project summary: `docs/context/project-map.md`.

## Files Likely Involved

- `app/src/main/java/com/dailynewsletter/ui/settings/SettingsScreen.kt`

## Files Owned By This Task

- `app/src/main/java/com/dailynewsletter/ui/settings/SettingsScreen.kt`

## Files Explicitly Not Owned

- `app/src/main/java/com/dailynewsletter/ui/settings/SettingsViewModel.kt` (untouched)
- `app/src/main/java/com/dailynewsletter/data/repository/SettingsRepository.kt` (untouched)
- `app/src/main/java/com/dailynewsletter/alarm/*` (untouched)
- `app/src/main/AndroidManifest.xml` (untouched)
- All other files.

## Forbidden Changes

- No new dependency.
- No persistence format change.
- No `setAlarmHour` / `setAlarmMinute` / `toggleAlarmDay` signature change.
- No `alarmFeedback` / TASK-040 wiring change.
- No card-display format change on the Settings screen.
- No public API contract change.
- No broad refactor.
- No formatting-only edits to unrelated parts of the file.

## Android Constraints

- `android.widget.NumberPicker` is part of the Android SDK (no new dependency).
- Compose interop via `androidx.compose.ui.viewinterop.AndroidView` (already part of `compose.ui` already on classpath).
- Use existing Kotlin style.
- Keep UI state deterministic — `hour12 / minute / isPm` mutable state lives only inside `AlarmTimeWheelDialog`.
- Keep side effects outside composables — `setAlarmHour/setAlarmMinute` calls happen in `onConfirm` lambda (event handler).
- API 26+ supported (NumberPicker exists since API 11).

## Acceptance Criteria

- [ ] `SettingsScreen.kt` no longer imports `androidx.compose.material3.TimePicker` or `rememberTimePickerState`.
- [ ] `SettingsScreen.kt` imports `android.widget.NumberPicker` and `androidx.compose.ui.viewinterop.AndroidView`.
- [ ] Private `@Composable AlarmTimeWheelDialog(...)` exists in the same file.
- [ ] Three `AndroidView { NumberPicker }` columns: hour 1..12, minute 0..59 with `%02d` formatter, AM/PM via `displayedValues`.
- [ ] Hour and minute wheels use `wrapSelectorWheel = true`. AM/PM wheel uses `wrapSelectorWheel = false`.
- [ ] Confirm button converts 12h+AM/PM → 24h via the rules in "Conversion correctness" and calls `viewModel.setAlarmHour(h24)` + `viewModel.setAlarmMinute(m)`.
- [ ] Open of dialog correctly seeds the wheels from `state.alarmHour` (24h) → 12h + AM/PM.
- [ ] Settings list card display format unchanged (`%02d:%02d` of 24h).
- [ ] No change to `AlarmScheduler`, `AlarmReceiver`, `SettingsViewModel`, `SettingsRepository`, `AndroidManifest.xml`.
- [ ] No regression to TASK-040 snackbar flow (`알람 권한이 필요합니다`, `알람 설정 실패: ...`, `DB 생성 실패: ...`).
- [ ] `./gradlew :app:assembleDebug` succeeds, or `SKIPPED_ENVIRONMENT_NOT_AVAILABLE`.

## Verification Command Candidates

- `grep -n "TimePicker\|rememberTimePickerState" app/src/main/java/com/dailynewsletter/ui/settings/SettingsScreen.kt` — should find 0 hits.
- `grep -n "NumberPicker\|AndroidView\|AlarmTimeWheelDialog" app/src/main/java/com/dailynewsletter/ui/settings/SettingsScreen.kt` — should find expected hits.
- `grep -n "displayedValues\|wrapSelectorWheel\|setFormatter" app/src/main/java/com/dailynewsletter/ui/settings/SettingsScreen.kt`
- `grep -n "setAlarmHour\|setAlarmMinute\|alarmFeedback\|DB 생성 실패\|알람 권한이 필요합니다\|알람 설정 실패" app/src/main/java/com/dailynewsletter/ui/settings/SettingsScreen.kt` — TASK-040 wiring untouched.
- `./gradlew :app:assembleDebug`

## Expected Implementer Output

- Changed files: 1 (`SettingsScreen.kt`).
- Behavior changed: alarm time dialog now uses 3-column NumberPicker wheels with 12-hour + AM/PM. 24h storage unchanged.
- Tests added/updated: none.
- Commands run: greps + assembleDebug.
- Notes for verifier: device check — open Settings → tap alarm time card → confirm three wheels appear → confirm conversion correctness for at least: 00:00 (12 AM 00), 12:00 (12 PM 00), 13:30 (1 PM 30), 23:59 (11 PM 59).

## STOP_AND_ESCALATE

- If `AndroidView { NumberPicker }` produces a sizing collapse inside `AlertDialog` content (NumberPicker's WRAP_CONTENT not measured) — try `Modifier.height(150.dp)` on each column. If still broken, escalate.
- If TASK-040 result file shows that `SettingsScreen.kt` was restructured in a way that conflicts with this brief's line references — re-anchor edits to the new structure preserving TASK-040 wiring; if ambiguity remains, escalate.
- If round-trip conversion fails any of the 4 boundary cases listed above — fix the helper and re-verify; do not escalate unless the spec itself is ambiguous.
