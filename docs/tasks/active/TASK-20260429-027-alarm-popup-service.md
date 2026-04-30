# Task Brief: 알람 팝업 + 사운드 + 자동 생성 (자동 인쇄 흐름 마무리)

Task ID: TASK-20260429-027
Status: active

## Goal
TASK-024/025로 알람 시간/요일 영속화 + AlarmManager 스케줄링까지 완료. 본 Task에서 알람 발사 시점에:
1. **Foreground Service**가 알람 사운드 looping 재생 (진동 OFF).
2. **Full-screen Activity**가 잠금화면 위로 떠 "프린트 하세요" 팝업.
3. 미인쇄 뉴스레터가 있으면 그것을, 없으면 즉석 자동 생성 후 그것을 인쇄 대상으로 준비.
4. 사용자가 팝업의 "인쇄" 버튼 탭 → 알람 정지 + PrintManager 다이얼로그 표시. 인쇄 성공·실패와 무관하게 알람은 즉시 정지.

## User-visible behavior
- 알람 시간 도달 → **즉시 시스템 기본 알람음 재생 시작** (진동 X).
- 잠금화면이어도 화면 위로 "프린트 하세요" 팝업 등장.
- 팝업 본문:
  - 미인쇄 뉴스레터 있음 → 제목 표시 + "인쇄" 버튼 활성.
  - 미인쇄 뉴스레터 없음 → "뉴스레터 준비 중..." 표시 + 버튼 비활성. 백그라운드에서 Gemini로 새 뉴스레터 생성 (TASK-026의 deep-dive 흐름). 완료되면 버튼 활성.
- 알람음은 **사용자가 팝업의 인쇄 버튼을 탭할 때까지** 계속 재생 (인쇄 누르기 전까지 닫기 버튼 없음).
- 인쇄 버튼 탭 → (a) 알람 정지, (b) PrintManager 다이얼로그 표시 (TASK-023의 흐름 재사용), (c) 팝업 닫힘.
- PrintManager 다이얼로그에서 사용자가 취소해도 알람은 이미 꺼진 상태 — 다시 울리지 않음.

## Scope

### 1. 신규 `app/src/main/java/com/dailynewsletter/alarm/AlarmService.kt`
- `@AndroidEntryPoint` `Service` (foreground).
- `companion object`:
  - `@Volatile var isRunning: Boolean = false`
  - `fun stop(context: Context)` — Activity가 호출, `context.stopService(Intent(..., AlarmService::class.java))`.
- `onStartCommand`:
  - `startForeground(NOTIFICATION_ID, buildNotification())` — high priority + full-screen intent → `AlarmActivity`.
  - `playAlarmSound()`.
- `playAlarmSound()`:
  ```kotlin
  val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
  mediaPlayer = MediaPlayer().apply {
      setDataSource(this@AlarmService, uri)
      setAudioAttributes(
          AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_ALARM)
              .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
              .build()
      )
      isLooping = true
      prepare()
      start()
  }
  ```
- `onDestroy`: mediaPlayer.stop()/release(), `isRunning = false`.
- `onBind`: return null.
- 알림 채널 ID는 기존 `DailyNewsletterApp.CHANNEL_PRINT` 또는 신규 `CHANNEL_ALARM`. 신규 권장.

### 2. 신규 `app/src/main/java/com/dailynewsletter/alarm/AlarmActivity.kt`
- `@AndroidEntryPoint` `ComponentActivity`.
- `onCreate`:
  - `setShowWhenLocked(true)`
  - `setTurnScreenOn(true)`
  - Compose UI 설정.
- 의존성 (Hilt `@Inject lateinit var`):
  - `newsletterRepository: NewsletterRepository`
  - `newsletterGenerationService: NewsletterGenerationService`
  - `printService: PrintService`
- 상태:
  ```kotlin
  sealed class AlarmUiState {
      object Loading : AlarmUiState()
      data class Ready(val newsletter: NewsletterUiItem) : AlarmUiState()
      data class Generating(val message: String) : AlarmUiState()
      data class GenerationFailed(val message: String) : AlarmUiState()
  }
  ```
- `onCreate` 안에서 lifecycleScope.launch:
  1. `newsletterRepository.getLatestUnprintedNewsletter()` 호출.
  2. null 아니면 `state = Ready(it)`.
  3. null이면 `state = Generating("뉴스레터 준비 중...")` → `newsletterGenerationService.generateLatest(pageCount)` 호출 → 성공 시 결과를 다시 `getNewsletter(id)`로 가져와 `Ready(item)`. 실패 시 `GenerationFailed(msg)`.
- UI Composable:
  - 중앙 카드/Column: 상단 "프린트 하세요" Title.
  - 본문: 상태에 따라 newsletter 제목 또는 진행 메시지 또는 실패 메시지.
  - 하단 큰 인쇄 버튼:
    - `enabled = state is Ready`.
    - onClick = `onPrintClick()`.
- `onPrintClick()`:
  ```kotlin
  AlarmService.stop(this)
  val item = (state as? AlarmUiState.Ready)?.newsletter ?: return
  val html = item.htmlContent
  if (html != null) printService.startSystemPrint(this, html, item.title)
  lifecycleScope.launch {
      try { newsletterRepository.markNewsletterPrinted(item.id) } catch (_: Exception) {}
      finish()
  }
  ```
- `onBackPressed` 무시 (사용자가 인쇄 버튼만 누를 수 있도록). 또는 BackHandler 비활성.

### 3. `app/src/main/java/com/dailynewsletter/alarm/AlarmReceiver.kt`
- 기존 `Log.d` + `alarmScheduler.reschedule()` 흐름 보존.
- 추가: `context.startForegroundService(Intent(context, AlarmService::class.java))` 호출.
- TODO 주석 제거.

### 4. `app/src/main/java/com/dailynewsletter/data/repository/NewsletterRepository.kt`
- 신규 `suspend fun getLatestUnprintedNewsletter(): NewsletterUiItem?`:
  - Notion `queryDatabase`에 `filter = Status != "printed"`, `sort = Date desc`, `pageSize = 1`.
  - 결과 1건 → `getNewsletter(id)`로 본문 포함 fetch (lazy 보충).
  - 없으면 null.
- 다른 함수들 변경 0.

### 5. `app/src/main/java/com/dailynewsletter/service/NewsletterGenerationService.kt`
- 신규 `suspend fun generateLatest(pageCount: Int): GeneratedNewsletter`:
  - `topicRepository.findPendingTopicsByTag("모든주제")` 호출 → 결과 비면 모든 태그 중 latest. 또는 단순화: 새 helper `topicRepository.getLatestPendingTopic()` 사용.
  - 그 1주제로 deep-dive 생성 (기존 generateForSlot 흐름의 단일 토픽 버전 — 코드 추출 또는 인라인 호출).
  - 실패 시 propagate.
- 기존 `generateForSlot(tag, pageCount)` 시그니처 그대로 유지.

### 6. `app/src/main/java/com/dailynewsletter/data/repository/TopicRepository.kt`
- 신규 `suspend fun getLatestPendingTopic(): TopicUiItem?`:
  - `queryDatabase`에 `Status != "consumed"` + `Date desc` + `pageSize = 1`.
  - 결과 1건 또는 null.

### 7. `app/src/main/AndroidManifest.xml`
- `<application>` 내부에 추가:
  ```xml
  <service
      android:name=".alarm.AlarmService"
      android:foregroundServiceType="mediaPlayback"
      android:exported="false" />

  <activity
      android:name=".alarm.AlarmActivity"
      android:exported="false"
      android:showOnLockScreen="true"
      android:turnScreenOn="true"
      android:launchMode="singleTask"
      android:taskAffinity=""
      android:excludeFromRecents="true" />
  ```
- 다른 변경 0.

### 8. `app/src/main/java/com/dailynewsletter/DailyNewsletterApp.kt` (또는 동등 Application 클래스)
- 신규 알림 채널 ID 추가 (예: `const val CHANNEL_ALARM = "alarm"`) + `NotificationChannel` 등록 (importance HIGH, sound 없음 — 사운드는 MediaPlayer가 담당, 진동 OFF).
- 또는 `CHANNEL_PRINT` 재활용. 결정은 implementer.

## Out of Scope
- 알람 ON/OFF 별도 토글 — TASK-024의 "빈 요일 = OFF" 그대로.
- AlarmActivity의 정교한 시각/사운드 스타일링 — 기능만.
- PrintJob.isCompleted observe로 정확한 status 갱신 — TASK-023 시점부터 후속.
- 인쇄 전 미리보기 — out.
- 다른 지점에서의 자동 인쇄(WorkManager 등) — out.
- ViewModel 도입 — Activity-내 state로 충분.

## Files Owned By This Task
- `app/src/main/java/com/dailynewsletter/alarm/AlarmService.kt` (신규)
- `app/src/main/java/com/dailynewsletter/alarm/AlarmActivity.kt` (신규)
- `app/src/main/java/com/dailynewsletter/alarm/AlarmReceiver.kt` (수정)
- `app/src/main/java/com/dailynewsletter/data/repository/NewsletterRepository.kt`
- `app/src/main/java/com/dailynewsletter/data/repository/TopicRepository.kt`
- `app/src/main/java/com/dailynewsletter/service/NewsletterGenerationService.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/dailynewsletter/DailyNewsletterApp.kt` (알림 채널 추가)

## Files Explicitly Not Owned
- `app/src/main/java/com/dailynewsletter/ui/*` (Newsletter/Topic/Keyword/Settings Screen + ViewModel은 변경 0)
- 그 외 모든 파일.

## Forbidden Changes
- No new dependency.
- No DB 스키마 변경.
- No PrintService 시그니처 변경 (TASK-023 그대로 사용).
- No `generateForSlot(tag, pageCount)` 시그니처 변경.
- No `markNewsletterPrinted(id)` 시그니처 변경.
- No 새 권한 추가 (TASK-025에서 모두 추가됨).

## Android Constraints
- Foreground Service는 Android 14+에서 `foregroundServiceType` 명시 필수 → `mediaPlayback`.
- `setShowWhenLocked(true)` API 27+. min-sdk 24면 fallback `Window.FLAG_SHOW_WHEN_LOCKED` 사용 — 본 앱 min-sdk 확인 후 적합한 쪽.
- `RingtoneManager.TYPE_ALARM` 기본 사운드 가져오기 — 디바이스마다 약간 다름. OK.
- `MediaPlayer` 대신 `Ringtone` 도 가능하지만 looping 명시적으로 안 됨 → MediaPlayer 권장.
- AlarmActivity의 onPrintClick 호출이 동일 lifecycle scope 안에서 PrintManager.print() 호출 + finish() — PrintDocumentAdapter callback이 finish 후에도 살아있어야 인쇄 완료. PrintService.startSystemPrint가 WebView를 Singleton에 보관하는 패턴(TASK-023) 그대로면 OK.

## Acceptance Criteria
- [ ] `AlarmService.kt`, `AlarmActivity.kt` 신규 파일.
- [ ] `AlarmService`에 `MediaPlayer` + `RingtoneManager.TYPE_ALARM` + `isLooping = true`.
- [ ] `AlarmService.companion object`의 `stop(context)` 함수.
- [ ] `AlarmReceiver`가 `startForegroundService` 호출.
- [ ] `AlarmActivity`가 `setShowWhenLocked(true)` 호출.
- [ ] `AlarmActivity`의 인쇄 버튼 onClick 안에서 `AlarmService.stop(this)` + `printService.startSystemPrint` 순서 호출.
- [ ] `NewsletterRepository.getLatestUnprintedNewsletter()` 함수 정의.
- [ ] `TopicRepository.getLatestPendingTopic()` 함수 정의.
- [ ] `NewsletterGenerationService.generateLatest(pageCount)` 함수 정의.
- [ ] AndroidManifest에 `<service ... AlarmService>` + `<activity ... AlarmActivity>` 등록.
- [ ] 빌드 성공 또는 SKIPPED_ENVIRONMENT_NOT_AVAILABLE.

## Verification Command Candidates
- `grep -rn "AlarmService\\|AlarmActivity\\|getLatestUnprintedNewsletter\\|getLatestPendingTopic\\|generateLatest" app/src/main`
- `grep -n "AlarmService\\|AlarmActivity" app/src/main/AndroidManifest.xml`
- `./gradlew :app:assembleDebug`

## Expected Implementer Output
- 변경 파일 8개 (3 신규 + 5 수정). 각 신규 파일 핵심 코드 인용. AlarmReceiver 변경 인용. Manifest 추가분 인용. grep 결과. 빌드 결과. 사용자 다음 동작 1줄: 재빌드 → 알람 시간 1~2분 뒤로 + 오늘 요일 → 디바이스 잠금 → 알람 시간 도달 시 잠금 위 팝업 + 사운드 + 인쇄 버튼 탭 시 시스템 다이얼로그.

## STOP_AND_ESCALATE
- `setShowWhenLocked(true)`가 min-sdk 미만에서 reflection 필요하면 — Window.FLAG_SHOW_WHEN_LOCKED 깃발로 대체 (escalate 불필요).
- `MediaPlayer.prepare()`가 main thread에서 block하면 (몇 ms는 OK) — `prepareAsync`로 변경. escalate 불필요.
- AlarmService의 `Hilt @AndroidEntryPoint`가 Service에 못 붙으면 — Manual EntryPointAccessors로 우회. escalate 불필요.
- `generateLatest`가 `findPendingTopicsByTag("모든주제")`로도 비면 — 그냥 throw "주제가 없습니다" 후 AlarmActivity가 GenerationFailed 표시. escalate 불필요.
- AlarmActivity가 finish 직후 PrintDocumentAdapter callback 끊기는 문제 발견 시 — Activity finish 지연 (예: 1초 delay 후 finish) 또는 PrintService에 WebView 보관 강참조 보강. 둘 다 안 통하면 escalate.
