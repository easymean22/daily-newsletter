# Task Brief: AlarmManager 스케줄러 + Receiver 골격

Task ID: TASK-20260428-025
Status: active

## Goal
TASK-024에서 저장된 알람 시간/요일 설정을 기반으로, 시계앱 수준의 신뢰도로 매일 정시에 알람을 발사할 수 있는 인프라 구축. 본 Task는 **스케줄링 + Receiver 등록**까지. 실제 사운드/팝업/인쇄 트리거는 TASK-026(또는 후속)에서.

## User-visible behavior
- 사용자가 설정에서 알람 시간/요일을 저장하면 → 다음 해당 요일/시간에 자동 재예약.
- 디바이스 재부팅 후에도 알람 다시 예약됨.
- 본 Task에서는 알람이 발사되어도 **Log만 남음** (소리·팝업 없음). 다음 Task에서 Service 연결.

## Scope

### 1. 신규 파일 `app/src/main/java/com/dailynewsletter/alarm/AlarmScheduler.kt`
- `@Singleton` Hilt 클래스. 의존성: `@ApplicationContext context`, `SettingsRepository`.
- 함수:
  - `suspend fun reschedule()`: SettingsRepository에서 alarmHour/Minute/AlarmDays 읽음. 다음 발사 시각 계산 (오늘 시간이 이미 지났으면 다음 활성 요일). 빈 요일이면 기존 알람 cancel 후 return.
  - 내부 `computeNextTrigger(now: ZonedDateTime, hour: Int, minute: Int, days: Set<DayOfWeek>): Long?` 헬퍼 — 빈 셋 → null.
  - `AlarmManager.setAlarmClock(AlarmClockInfo(triggerAtMillis, openIntent), pendingIntent)` 사용. 시계앱 동급 신뢰도.
  - PendingIntent: `AlarmReceiver`로 가는 broadcast intent. requestCode=1001 고정.
  - 기존 alarm cancel: 같은 PendingIntent로 `alarmManager.cancel(...)`.
  - `Log.d("AlarmScheduler", "scheduled at $triggerInstant")` 로깅.

### 2. 신규 파일 `app/src/main/java/com/dailynewsletter/alarm/AlarmReceiver.kt`
- `@AndroidEntryPoint` BroadcastReceiver (Hilt).
- `onReceive(context, intent)`:
  - 본 Task: `Log.d("AlarmReceiver", "alarm fired at ${System.currentTimeMillis()}")`.
  - 그리고 즉시 `AlarmScheduler.reschedule()` 호출(다음 발사 일정 잡기). `goAsync()` + `CoroutineScope(Dispatchers.IO).launch`로 처리.
  - 향후 (TASK-026): AlarmService 시작 코드 추가될 자리. TODO 주석으로 표시.
- 의존성: `@Inject lateinit var alarmScheduler: AlarmScheduler`.

### 3. 신규 파일 `app/src/main/java/com/dailynewsletter/alarm/BootReceiver.kt`
- `@AndroidEntryPoint` BroadcastReceiver.
- `onReceive(context, intent)`:
  - `intent.action`이 `Intent.ACTION_BOOT_COMPLETED` 또는 `Intent.ACTION_LOCKED_BOOT_COMPLETED` 또는 `Intent.ACTION_MY_PACKAGE_REPLACED`이면:
  - `goAsync()` + Coroutine으로 `alarmScheduler.reschedule()` 호출.
  - `Log.d("BootReceiver", "rescheduled after boot/install")`.

### 4. `app/src/main/AndroidManifest.xml`
- `<uses-permission>` 추가:
  - `android.permission.WAKE_LOCK`
  - `android.permission.USE_FULL_SCREEN_INTENT`
  - `android.permission.FOREGROUND_SERVICE`
  - `android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK`
- 기존 `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `POST_NOTIFICATIONS` 그대로 유지.
- `<application>` 안에:
  ```xml
  <receiver android:name=".alarm.AlarmReceiver"
      android:exported="false" />

  <receiver android:name=".alarm.BootReceiver"
      android:exported="true">
      <intent-filter>
          <action android:name="android.intent.action.BOOT_COMPLETED" />
          <action android:name="android.intent.action.LOCKED_BOOT_COMPLETED" />
          <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
      </intent-filter>
  </receiver>
  ```
- `android:usesCleartextTraffic="true"` 그대로 유지 (다른 변경 없음).

### 5. `app/src/main/java/com/dailynewsletter/ui/settings/SettingsViewModel.kt`
- `AlarmScheduler` 주입 추가.
- `setAlarmHour`/`setAlarmMinute`/`toggleAlarmDay` 각 함수의 마지막에 `alarmScheduler.reschedule()` 호출 (`viewModelScope.launch { ... }` 내부).
- 다른 변경 없음.

## Out of Scope
- AlarmService (foreground sound/notification) — TASK-026.
- AlarmActivity (popup) — TASK-026.
- 자동 뉴스레터 생성 호출 — TASK-026.
- 인쇄 버튼 → PrintManager 흐름 — 이미 TASK-023에서 끝남.
- 알람 ON/OFF 별도 토글 — TASK-024 결정대로 빈 요일 셋이 OFF.
- DB 스키마 변경 0.

## Files Owned By This Task
- `app/src/main/java/com/dailynewsletter/alarm/AlarmScheduler.kt` (신규)
- `app/src/main/java/com/dailynewsletter/alarm/AlarmReceiver.kt` (신규)
- `app/src/main/java/com/dailynewsletter/alarm/BootReceiver.kt` (신규)
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/dailynewsletter/ui/settings/SettingsViewModel.kt`

## Files Explicitly Not Owned
- 그 외 모든 파일 (특히 NewsletterGenerationService, NewsletterViewModel, NewsletterScreen, NewsletterRepository — 병렬 진행 중인 TASK-026이 소유).

## Forbidden Changes
- No new dependency.
- No DB 스키마/migration.
- No Service/Activity 신규 (다음 Task).
- No ViewModel 외 다른 UI 파일 변경.

## Android Constraints
- `setAlarmClock` 사용 — Doze/App Standby에서도 시간 정확하게 발사.
- `SCHEDULE_EXACT_ALARM` 권한은 Android 12+에서 자동 부여됨 (앱 카테고리 alarm-clock). 본 앱은 `<uses-permission>`만으로 충분(이미 manifest에 있음).
- `BOOT_COMPLETED` 외에도 `LOCKED_BOOT_COMPLETED` 추가 — Direct Boot 환경에서 알람 살리기 위함.

## Acceptance Criteria
- [ ] `AlarmScheduler.kt`에 `fun reschedule()` 또는 `suspend fun reschedule()` 함수 존재.
- [ ] `AlarmScheduler`가 `setAlarmClock` 호출 (grep `setAlarmClock`).
- [ ] `AlarmReceiver.kt`가 BroadcastReceiver 상속 + `@AndroidEntryPoint`.
- [ ] `BootReceiver.kt`가 `BOOT_COMPLETED` action 처리.
- [ ] `AndroidManifest.xml`에 새 4개 권한 추가 (`USE_FULL_SCREEN_INTENT`, `WAKE_LOCK`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`).
- [ ] `AndroidManifest.xml`에 `AlarmReceiver`, `BootReceiver` `<receiver>` 등록.
- [ ] `SettingsViewModel`의 `setAlarmHour`/`setAlarmMinute`/`toggleAlarmDay`에서 `alarmScheduler.reschedule()` 호출.
- [ ] 빌드 성공 또는 SKIPPED_ENVIRONMENT_NOT_AVAILABLE.

## Verification Command Candidates
- `grep -rn "setAlarmClock\\|AlarmScheduler\\|AlarmReceiver\\|BootReceiver" app/src/main`
- `grep -n "USE_FULL_SCREEN_INTENT\\|WAKE_LOCK\\|FOREGROUND_SERVICE" app/src/main/AndroidManifest.xml`
- `./gradlew :app:assembleDebug`

## Expected Implementer Output
- 신규 3 파일 + 수정 2 파일.
- `AlarmScheduler.reschedule` 핵심 로직 인용 (다음 발사 시각 계산 + setAlarmClock 호출).
- 매니페스트 추가분 인용.
- grep 결과.
- 빌드 결과.
- 사용자 다음 동작 1줄: 재빌드 → 설정에서 알람 시간을 1~2분 뒤로 설정 + 오늘 요일 체크 → logcat에서 `AlarmScheduler` `scheduled at ...` 확인 → 시간 오면 `AlarmReceiver` `alarm fired` 로그 확인.

## STOP_AND_ESCALATE
- Hilt가 BroadcastReceiver 주입을 처리할 수 없으면 (`@AndroidEntryPoint`가 BroadcastReceiver에 못 붙으면) — 수동 EntryPointAccessors 패턴 시도, 그래도 막히면 escalate.
- `setAlarmClock`이 OEM 별로 silently 무시되는 경우(Xiaomi 등) — 현재 디바이스(테스트 기기)에서는 무시되어도 일반 `setExactAndAllowWhileIdle` fallback 도입은 escalate.
