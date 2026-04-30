# Task Brief: 알람 설정 UI + 영속화

Task ID: TASK-20260428-024
Status: active

## Goal
설정 화면에 매일 인쇄 알람의 **시간**과 **요일**을 사용자가 선택·저장할 수 있게 한다. 본 Task는 UI + 영속화만 담당. 실제 AlarmManager 스케줄링은 다음 Task(TASK-025)에서.

## User-visible behavior
- 설정 화면 하단(또는 적절한 섹션)에 "인쇄 알람" 영역 신규 추가:
  - **시간 선택**: 시(0~23) + 분(0~59) — Material3 TimePicker 또는 그와 유사한 UI.
  - **요일 선택**: 월/화/수/목/금/토/일 7개 chip. 각각 토글.
  - 저장(또는 즉시 저장) 시 SettingsEntity에 영속화.
- 이미 존재하는 `KEY_PRINT_TIME_HOUR/MINUTE` 재사용. 새 키 `KEY_ALARM_DAYS` 추가.
- 디폴트 값: 시 7, 분 0 (기존 디폴트 그대로). 요일은 **빈 상태**(아무것도 선택 안 됨 → 알람 비활성화 의미).
- 사용자가 모든 요일 체크 해제 시 알람 OFF로 간주(별도 ON/OFF 토글 없음).

## Scope

### 1. `app/src/main/java/com/dailynewsletter/data/local/entity/SettingsEntity.kt`
- `companion object`에 새 상수 추가:
  ```kotlin
  const val KEY_ALARM_DAYS = "alarm_days"
  ```
- 기존 `KEY_PRINT_TIME_HOUR`/`KEY_PRINT_TIME_MINUTE` 그대로 사용. 추가 변경 0.

### 2. `app/src/main/java/com/dailynewsletter/data/repository/SettingsRepository.kt`
- `getAlarmDays()` / `setAlarmDays(days: Set<DayOfWeek>)` 추가.
- 영속 포맷: `Set<DayOfWeek>` → comma-separated `DayOfWeek.name` 문자열 (예: `"MONDAY,TUESDAY,FRIDAY"`).
- 빈 set → 빈 문자열 저장.
- `getAlarmDays()` 반환: 저장값이 null/빈문자열이면 emptySet, 아니면 `name` 파싱 후 `DayOfWeek.valueOf` (잘못된 토큰은 skip).
- 기존 `getPrintTimeHour/Minute` 그대로. `setPrintTimeHour(h: Int)` / `setPrintTimeMinute(m: Int)` 추가 (없다면).
- import: `java.time.DayOfWeek`.

### 3. `app/src/main/java/com/dailynewsletter/ui/settings/SettingsViewModel.kt`
- UiState에 추가:
  ```kotlin
  val alarmHour: Int = 7,
  val alarmMinute: Int = 0,
  val alarmDays: Set<DayOfWeek> = emptySet()
  ```
- 초기 load 시 SettingsRepository에서 읽어 state 채움.
- 사용자 액션:
  - `setAlarmHour(h: Int)` / `setAlarmMinute(m: Int)`: state 업데이트 + repository에 즉시 저장.
  - `toggleAlarmDay(day: DayOfWeek)`: state.alarmDays 토글 + repository에 즉시 저장.
- 기타 기존 setter들과 동일한 즉시 저장 패턴.

### 4. `app/src/main/java/com/dailynewsletter/ui/settings/SettingsScreen.kt`
- 기존 설정 항목들 아래에 "인쇄 알람" 섹션 추가.
- 시간 선택: 가장 간단한 형태로 OK
  - Material3 `TimePicker` Composable 사용 (실험적 API 허용) **또는**
  - Hour/Minute 두 개의 NumberPicker 스타일 OutlinedTextField (`keyboardOptions = KeyboardType.Number`).
  - 시간 변경 즉시 `viewModel.setAlarmHour(h)` 호출.
- 요일 선택: `FlowRow` 또는 `Row` 안에 7개의 `FilterChip` 또는 `Chip`. selected 상태에 따라 색 다르게.
  - 클릭 시 `viewModel.toggleAlarmDay(day)`.
  - 라벨: `"월"`, `"화"`, ... 한국어 한 글자.
  - DayOfWeek 순서: MONDAY, TUESDAY, ..., SUNDAY.

기존 다른 설정(API 키 등) UI는 손대지 말 것.

### Out of Scope
- AlarmManager 실제 호출/스케줄링 — TASK-025.
- 알람 ON/OFF 별도 토글 — 빈 요일 셋이 곧 OFF.
- 사운드/진동 옵션 UI — 후속.
- AlarmActivity/Service 구현 — TASK-026.
- 다른 설정 항목 변경 — 0.
- DB 스키마 변경 — 0 (key-value 추가뿐, 마이그레이션 불필요).

## Files Owned By This Task
- `app/src/main/java/com/dailynewsletter/data/local/entity/SettingsEntity.kt`
- `app/src/main/java/com/dailynewsletter/data/repository/SettingsRepository.kt`
- `app/src/main/java/com/dailynewsletter/ui/settings/SettingsScreen.kt`
- `app/src/main/java/com/dailynewsletter/ui/settings/SettingsViewModel.kt`

## Files Explicitly Not Owned
- `app/src/main/java/com/dailynewsletter/ui/newsletter/*`
- `app/src/main/java/com/dailynewsletter/service/*`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/dailynewsletter/data/local/dao/SettingsDao.kt` (변경 불필요 — generic key-value)
- 그 외 모든 파일.

## Forbidden Changes
- No new dependency.
- No DB 스키마/migration 변경.
- No AlarmManager/Receiver/Service 추가.
- No new permission.

## Android Constraints
- `java.time.DayOfWeek` 사용 (이미 desugaring/min API 24+ 가정 — 기존 코드에서 `OffsetDateTime` 등 사용 중이므로 호환).
- Compose state는 ViewModel UiState로 단방향.
- TimePicker가 Material3에서 `@OptIn(ExperimentalMaterial3Api::class)` 필요할 수 있음 — 어노테이션 추가 OK.

## Acceptance Criteria
- [ ] `SettingsEntity.kt`에 `const val KEY_ALARM_DAYS = "alarm_days"` 추가됨.
- [ ] `SettingsRepository.kt`에 `getAlarmDays()`/`setAlarmDays(...)` 함수 존재.
- [ ] `SettingsRepository.kt`에 `setPrintTimeHour(...)`/`setPrintTimeMinute(...)` 함수 존재 (없었다면).
- [ ] `SettingsViewModel`의 UiState에 `alarmHour`, `alarmMinute`, `alarmDays` 필드 존재.
- [ ] `SettingsViewModel`에 `setAlarmHour`, `setAlarmMinute`, `toggleAlarmDay` 함수 존재.
- [ ] `SettingsScreen.kt`에서 7개 요일에 해당하는 chip/button 렌더 (grep `MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY` 또는 `DayOfWeek.values()`).
- [ ] 시간 선택 UI 존재 (grep `TimePicker` 또는 `setAlarmHour`).
- [ ] 빌드 성공 또는 SKIPPED_ENVIRONMENT_NOT_AVAILABLE.

## Verification Command Candidates
- `grep -n "KEY_ALARM_DAYS\\|getAlarmDays\\|setAlarmDays" app/src/main/java/com/dailynewsletter/data`
- `grep -n "alarmHour\\|alarmMinute\\|alarmDays\\|toggleAlarmDay" app/src/main/java/com/dailynewsletter/ui/settings`
- `./gradlew :app:assembleDebug`

## Expected Implementer Output
- 변경 파일 4개.
- 핵심 코드 인용 (KEY 상수 / Repository getter·setter / ViewModel state·actions / Screen 섹션).
- grep 결과.
- 빌드 결과.
- 사용자 다음 동작 1줄: 재빌드 → 설정 화면 → "인쇄 알람" 섹션에서 시간·요일 선택 후 저장값이 보존되는지 확인.

## STOP_AND_ESCALATE
- TimePicker Material3 API가 현재 Compose BOM에서 사용 불가하면 NumberField fallback 채택 (escalate 불필요).
- DayOfWeek 사용이 desugaring 미설정으로 컴파일 안 되면 escalate (gradle 변경 필요).
- 기존 SettingsScreen 구조가 너무 파편화되어 새 섹션을 단순히 끼워넣기 어렵다면 escalate.
