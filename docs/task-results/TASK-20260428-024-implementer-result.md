## Implementation Summary

Task ID: TASK-20260428-024
Status: IMPLEMENTED

## Changed Files

- `app/src/main/java/com/dailynewsletter/data/local/entity/SettingsEntity.kt`: Added `const val KEY_ALARM_DAYS = "alarm_days"` to companion object.
- `app/src/main/java/com/dailynewsletter/data/repository/SettingsRepository.kt`: Added `import java.time.DayOfWeek`; added `setPrintTimeHour(h)`, `setPrintTimeMinute(m)`, `getAlarmDays()`, `setAlarmDays(days)`.
- `app/src/main/java/com/dailynewsletter/ui/settings/SettingsViewModel.kt`: Added `import java.time.DayOfWeek`; added `alarmHour`, `alarmMinute`, `alarmDays` fields to `SettingsUiState`; added alarm-days parsing in combine block; added `setAlarmHour`, `setAlarmMinute`, `toggleAlarmDay` functions.
- `app/src/main/java/com/dailynewsletter/ui/settings/SettingsScreen.kt`: Added `import FilterChip`, `import java.time.DayOfWeek`; added `showAlarmTimePicker` state; added "인쇄 알람" section with alarm time Card, 7-chip weekday row (MONDAY-SUNDAY / 월-일), and alarm TimePicker AlertDialog calling `setAlarmHour`/`setAlarmMinute`.

## Behavior Changed

- Settings screen now shows an "인쇄 알람" section below "뉴스레터 분량".
- Tapping the alarm time card opens a Material3 TimePicker dialog (24-hour). Confirming calls `setAlarmHour`/`setAlarmMinute` which persist via `setPrintTimeHour`/`setPrintTimeMinute`.
- Seven FilterChip components labeled 월/화/수/목/금/토/일 (MONDAY through SUNDAY). Tapping toggles and persists via `setAlarmDays`. Selected chips are visually distinct via Material3 FilterChip `selected` state.
- Empty alarmDays = alarm off (no separate toggle). A hint text is shown when no days are selected.
- alarmHour/alarmMinute share KEY_PRINT_TIME_HOUR/MINUTE storage (reuse existing keys per Brief).

## Tests Added or Updated

None added. Task Brief did not require unit tests for this UI + persistence slice. Repository logic (`getAlarmDays`/`setAlarmDays`) is straightforward enough to verify via manual device testing.

## Commands Run

- `./gradlew :app:assembleDebug` — SKIPPED_ENVIRONMENT_NOT_AVAILABLE (Bash permission denied; user builds in Android Studio).

## Grep Verifications (all passed)

1. `KEY_ALARM_DAYS|getAlarmDays|setAlarmDays` in `data/` — found in SettingsEntity.kt (constant) and SettingsRepository.kt (getter + setter).
2. `alarmHour|alarmMinute|alarmDays|toggleAlarmDay|setAlarmHour|setAlarmMinute` in `ui/settings/` — found across SettingsViewModel.kt and SettingsScreen.kt.
3. `MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY` in SettingsScreen.kt — all 7 DayOfWeek values present.
4. `TimePicker|setAlarmHour` in SettingsScreen.kt — both present (TimePicker import + usage, setAlarmHour call).

## Notes for Verifier

- Important files:
  - `/app/src/main/java/com/dailynewsletter/data/local/entity/SettingsEntity.kt`
  - `/app/src/main/java/com/dailynewsletter/data/repository/SettingsRepository.kt`
  - `/app/src/main/java/com/dailynewsletter/ui/settings/SettingsViewModel.kt`
  - `/app/src/main/java/com/dailynewsletter/ui/settings/SettingsScreen.kt`
- Suggested checks:
  - Build: `./gradlew :app:assembleDebug`
  - Navigate to Settings screen -> scroll to "인쇄 알람" section -> tap time card -> change time -> confirm -> verify time label updates.
  - Tap weekday chips -> verify selected state toggles and persists across app restart.
- Known limitations:
  - `kotlinx.coroutines.flow.map` import in SettingsViewModel was pre-existing and unused — not removed (pre-existing warning, out of scope).
  - `alarmHour`/`alarmMinute` share storage with `printTimeHour`/`printTimeMinute` per Task Brief ("이미 존재하는 KEY_PRINT_TIME_HOUR/MINUTE 재사용"). Both print-time and alarm-time Cards therefore show the same stored value and saving from either overwrites the same keys. TASK-025 may need to differentiate if these should be separate.
- User next action: rebuild in Android Studio -> open Settings screen -> scroll to "인쇄 알람" -> set time and select days -> restart app and verify values persist.
