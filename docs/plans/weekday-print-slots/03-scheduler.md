---
updated: 2026-04-19
summary: "WorkScheduler 요일별 재설계 + OFF 토글 정책 + PrintWorker 요일 메타 입력"
parent: ./README.md
---

# 스케줄러 확장

## 대안 비교 (3개)

### 옵션 S-A: 요일별 독립 PeriodicWork 7개 (추천)

- Unique work name: `daily_print_MONDAY` ... `daily_print_SUNDAY`.
- 각 `PeriodicWorkRequestBuilder<PrintWorker>(7, TimeUnit.DAYS)` — 주 1회 주기.
- 초기 delay = 지금부터 "다음 해당 요일 H:M"까지의 밀리초.
- `inputData = workDataOf("day_of_week" to "MONDAY")`.
- OFF 요일 또는 빈 슬롯 요일은 **enqueue하지 않음** + 기존 `cancelUniqueWork("daily_print_<day>")`.

### 옵션 S-B: 단일 PeriodicWork 1일 주기 + Worker 내부 요일 필터

- 기존 `daily_print` unique work를 1일 주기로 유지.
- Worker 진입 시 `LocalDate.now().dayOfWeek` → `getSlotsForDay(today)` 조회 → 슬롯 없으면 즉시 return.
- 시각은 어떻게 다룰 것인가? — 1일 주기 단일 delay는 특정 시각 1개만 반영 가능. **요일별로 시각이 다르면 불가**.

### 옵션 S-C: OneTimeWorkRequest 체이닝 (매 실행 후 다음 실행 enqueue)

- `PrintWorker.doWork()` 완료 후 다음 enabled 요일의 시각을 계산해 `OneTimeWorkRequest`로 enqueue.
- `enqueueUniqueWork("next_print", REPLACE, ...)`.
- WorkManager 주기 기능 의존 X — 유연.

## Trade-off

| 축 | S-A (요일별 7개) | S-B (단일 1일 주기) | S-C (OneTime 체이닝) |
|---|---|---|---|
| **spec 의도 부합** ★ | 요일별 시각 다름 자연 지원. 1:1. | 요일별 시각 동일 가정 필요 — spec 위배 또는 UX 제약. | 가장 유연. |
| **구현 복잡도** | 중 — 7 enqueue 루프 + cancel 배선. | 낮 — 기존 1개 유지. | 높 — 다음 실행 계산 로직 + 체이닝 안정성 |
| **OFF 토글 반영** | `cancelUniqueWork` 1회로 깨끗이 제거. | Worker 진입 후 즉시 return — 불필요 wakeup 발생. | 체인 끊기 관리 복잡. |
| **설정 변경 반영** | 전체 재schedule: `scheduleAll()` = 7회 `enqueueUniquePeriodicWork(UPDATE, ...)` — WorkManager가 중복 처리. | 단일 재schedule. | 현재 체인 cancel + 새 OneTime enqueue. |
| **WorkManager 신뢰성** | PeriodicWork 기본 신뢰성 (1주 주기도 표준 지원). | 동일. | 체이닝 끊기면 영구 중단 — MVP 수용 어려움. |
| **관측/디버깅** | `adb shell dumpsys jobscheduler | grep daily_print_` → 7건. 명확. | 1건만. | 항상 다음 1개만 보임. |
| **다음날 검증** | 당일 요일 work만 확인하면 됨. | 내일 요일 확인 어려움 (같은 1일 주기). | — |
| **Doze 모드 영향** | 7주기 모두 동일 영향 (setExact 아님). | 동일. | OneTime도 동일. |
| **되돌리기 비용** | 중 — 7개 unique work 전부 cancel 후 재설계. | 낮. | 높. |

## 추천안: **옵션 S-A**

근거:

1. **spec §3 "요일별 시각"을 구조로 표현할 유일한 옵션**. S-B/C는 워크어라운드.
2. **OFF 토글 반영이 깔끔** — `cancelUniqueWork("daily_print_<day>")` 1회.
3. **PeriodicWork 1주 주기는 WorkManager 표준** — `PeriodicWorkRequestBuilder(7, TimeUnit.DAYS)`. 신뢰성 검증된 경로.
4. **디버깅/관측** — 7개 unique work가 모두 보이면 "다음 실행 시점"을 사용자가 즉시 인지 가능.

ADR-0006에 박는 결정 #2.

## `WorkScheduler` 재설계

### 신규 `scheduleAll()` 구조

```kotlin
suspend fun scheduleAll() {
    // 기존 enqueued work 정리
    workManager.cancelUniqueWork("newsletter_generation")    // newsletter-shelf 플랜 몫
    workManager.cancelUniqueWork("daily_topic_selection")    // topic-generation-paths 몫
    workManager.cancelUniqueWork("daily_print")              // 본 플랜 — 기존 단일 프린트 work 제거

    scheduleWeeklyPrints()
    scheduleCleanup()   // 기존 유지 (MVP 판정 밖)
}

private suspend fun scheduleWeeklyPrints() {
    val schedule = settingsRepository.getWeeklySchedule()
    for (day in DayOfWeek.values()) {
        val uniqueName = "daily_print_${day.name}"
        val daySchedule = schedule.byDay[day]

        if (daySchedule == null || !daySchedule.enabled || daySchedule.slots.isEmpty()) {
            workManager.cancelUniqueWork(uniqueName)   // OFF 또는 빈 슬롯 → 제거
            continue
        }

        val delay = calculateDelayUntilNextOccurrence(day, daySchedule.hour, daySchedule.minute)
        val request = PeriodicWorkRequestBuilder<PrintWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("day_of_week" to day.name))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
            .addTag("print")
            .addTag("day:${day.name}")
            .build()

        workManager.enqueueUniquePeriodicWork(
            uniqueName,
            ExistingPeriodicWorkPolicy.UPDATE,   // 시각 변경 시 기존 정책 갱신
            request
        )
    }
}

private fun calculateDelayUntilNextOccurrence(day: DayOfWeek, hour: Int, minute: Int): Long {
    val now = LocalDateTime.now()
    val todayAtTime = now.toLocalDate().atTime(hour, minute)
    var daysUntil = (day.value - now.dayOfWeek.value + 7) % 7
    var target = todayAtTime.plusDays(daysUntil.toLong())
    if (target.isBefore(now)) target = target.plusDays(7)
    return Duration.between(now, target).toMillis()
}
```

### 트리거 지점 — 언제 `scheduleAll()`이 호출되나

- 기존: `MainActivity` 또는 `DailyNewsletterApp.onCreate()` (확인 필요 — 구현 단계에서 점검).
- 추가: **`WeeklyScheduleViewModel`이 저장 완료 후 `WorkScheduler.scheduleAll()`을 호출**해 즉시 반영.
- 호출 빈도: 설정 변경 시 + 앱 시작 시. `ExistingPeriodicWorkPolicy.UPDATE`라 중복 호출 안전.

## `PrintWorker` 변경 (newsletter-shelf 7단계 + 본 플랜)

newsletter-shelf 플랜이 `inputData.getString("newsletter_id")` 의존을 제거하고 `PrintOrchestrator.runForToday()` 호출로 교체. 본 플랜은 이를 요일 입력으로 확장.

```kotlin
@HiltWorker
class PrintWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val printOrchestrator: PrintOrchestrator
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val dayName = inputData.getString("day_of_week")
            ?: return Result.failure()    // 본 플랜 이전 enqueue가 남아있지 않도록 scheduleAll에서 cancel 처리
        val day = runCatching { DayOfWeek.valueOf(dayName) }.getOrNull()
            ?: return Result.failure()

        return try {
            printOrchestrator.runForDay(day)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 1) Result.retry() else Result.failure()   // spec §6 "당일 1회 재시도"
        }
    }
}
```

## `PrintOrchestrator` 시그니처 확정

newsletter-shelf 02-backend.md의 `runForToday()`를 `runForDay(day: DayOfWeek)`로 바꿈. 내부 구현은 동일:

```kotlin
suspend fun runForDay(day: DayOfWeek) {
    val slots = settingsRepository.getSlotsForDay(day)
    if (slots.isEmpty()) {
        notifier.notifyInfo("${day.koreanName()}은 설정된 슬롯이 없습니다")
        return
    }
    for (slot in slots) {
        try {
            handleSlot(slot)
        } catch (e: Exception) {
            notifier.notifyError("슬롯 [${slot.tag}, ${slot.pages}장] 처리 실패: ${e.message}")
        }
    }
}
```

- `runForToday()`는 `runForDay(LocalDate.now().dayOfWeek)`의 편의 메서드로 유지하거나, PrintWorker가 직접 `runForDay(day)`를 호출하도록 하여 제거.
- newsletter-shelf 플랜은 stub으로 `Slot("모든주제", pages)` 1개를 반환했으나, 본 플랜이 `getSlotsForDay`를 실제 구현으로 교체 → stub 제거.

## on/off 토글 정책 (spec §3 "한 번 OFF면 사용자가 다시 켤 때까지 OFF")

### 요구사항 재확인

- 사용자가 월요일 OFF → 다음 월요일도 자동으로 ON되지 않음.
- OFF된 요일은 프린트 시각 도달해도 **아무것도 실행하지 않음** (알림도 X).

### 구현

- 저장 스키마 `DaySchedule.enabled: Boolean`.
- OFF 전환 시 `WeeklyScheduleViewModel.toggleDay(day, false)` → `SettingsRepository.setWeeklySchedule(...)` → `WorkScheduler.scheduleAll()` → 해당 요일 `cancelUniqueWork("daily_print_<day>")`.
- ON 복귀 시 동일 경로 — 재schedule.
- **자동 ON 복귀 방지**: 저장 스키마가 사용자 명시 전까지 `enabled=false` 유지. 별도 로직 불필요.

### 알림 정책

- OFF 요일: Worker가 아예 enqueue되지 않으므로 notify 경로 없음.
- ON + 빈 슬롯: 이론상 가능 (enabled=true, slots=[]). `scheduleWeeklyPrints()`에서 "빈 슬롯"도 cancel 처리 — enqueue 스킵. 이 상태는 UI에서 "슬롯을 추가해주세요" 안내로 유도.
- 슬롯 처리 중 주제/뉴스레터 부족: newsletter-shelf 04-infra.md의 기존 알림 정책 그대로.

## 배포 전 체크 (인프라 메모)

- **Doze 모드**: `PeriodicWorkRequest`는 Doze 중 연기 가능. 정확한 시각 보장 아님 (spec §6 "장기 신뢰성은 MVP 판정 밖"과 정합).
- **WorkManager 로그**: `adb shell dumpsys jobscheduler | grep daily_print_` — 7 unique work 각각의 다음 실행 시각 확인 가능.
- **디버그 진입점**: 수동 E2E 시 "지금으로부터 2분 후 월요일 프린트 트리거" 같은 버튼이 필요할 수 있음 — 본 플랜 scope 밖. MVP 1일 E2E 검증은 실제 요일 + 실제 시각으로.

## 에러 / 빈 상태 (스케줄러 보장 계약)

| 상황 | 정책 |
|---|---|
| 모든 요일 OFF | `scheduleWeeklyPrints()`가 7개 work 모두 cancel. Worker 실행 없음. UI는 "프린트 설정이 없습니다" 표시. |
| 모든 요일 ON + 빈 슬롯 | 7개 work 모두 cancel (scheduleWeeklyPrints 진입부 조건). |
| 특정 요일만 ON + 슬롯 있음 | 해당 요일 work만 enqueue. |
| 스케줄 저장 후 scheduleAll 호출 실패 | 저장은 성공했으나 WorkManager 반영 실패 — 다음 앱 시작 시 `scheduleAll()` 재호출로 자연 복구. 단, UI는 "저장은 됐지만 스케줄 반영 실패" 토스트 (ViewModel 책임). |
| Worker 실행 시 `inputData["day_of_week"]` 누락 or 잘못된 값 | `Result.failure()` — 다음 실행은 1주 후 동일 slot. 로그 경고. 본 플랜 이전에 남은 work가 실행되는 경우 scheduleAll의 cancel로 방지. |
