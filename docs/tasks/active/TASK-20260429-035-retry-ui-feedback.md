# Task Brief: 재시도 중 사용자 메시지 표시

Task ID: TASK-20260429-035
Status: active

## Goal
TASK-034에서 만든 GeminiRetry가 백그라운드에서 silent하게 재시도 중. 사용자는 길게 기다리는 이유를 모름. 재시도 발생 시 ManualGenStatus에 "혼잡으로 재시도 중... 잠시만 기다려 주세요" 같은 메시지를 노출. 최종 성공/실패는 기존 그대로.

## User-visible behavior
- 뉴스레터 수동 생성 중 Gemini 503 발생:
  - 화면(BottomSheet 또는 Snackbar)에 **"혼잡으로 재시도 중입니다. 잠시만 기다려 주세요 (1/3)"** 같은 메시지 노출.
  - 재시도 성공 시 정상 Success 흐름으로 전환.
  - 재시도 모두 실패 시 기존 Failed("Gemini가 일시적으로 혼잡합니다...")로 마무리.
- 그 외 에러(400/401/429)는 즉시 Failed (변경 없음).

## Scope

### 1. `app/src/main/java/com/dailynewsletter/service/GeminiRetry.kt`
- `sealed class RetryEvent` 추가:
  ```kotlin
  sealed class RetryEvent {
      data class Retrying(val label: String, val attempt: Int, val totalAttempts: Int, val delayMs: Long) : RetryEvent()
  }
  ```
- `object GeminiRetry`에 `MutableSharedFlow<RetryEvent>(replay = 0, extraBufferCapacity = 8)` 보관, `val events: SharedFlow<RetryEvent>` exposed.
- `withRetry` 내부 재시도 직전 `_events.tryEmit(RetryEvent.Retrying(label, attempt+1, DELAYS_MS.size, delayMs))` 추가.
- 기존 throw/return 동작 그대로.

### 2. `app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterViewModel.kt`
- `ManualGenStatus`에 새 variant:
  ```kotlin
  data class Retrying(val message: String) : ManualGenStatus()
  ```
- `init`에서 `GeminiRetry.events`를 collect:
  ```kotlin
  viewModelScope.launch {
      GeminiRetry.events.collect { event ->
          when (event) {
              is RetryEvent.Retrying -> {
                  if (_uiState.value.manualGenStatus is ManualGenStatus.Running ||
                      _uiState.value.manualGenStatus is ManualGenStatus.Retrying) {
                      val msg = "혼잡으로 재시도 중입니다. 잠시만 기다려 주세요 (${event.attempt}/${event.totalAttempts})"
                      _uiState.update { it.copy(manualGenStatus = ManualGenStatus.Retrying(msg)) }
                  }
              }
          }
      }
  }
  ```
- `generateNewsletterManually`의 onSuccess/onFailure는 그대로 — Success/Failed가 Retrying을 덮어씀.
- `generateNewsletterManually` 진입 시 `manualGenStatus = Running` 그대로 (현재 코드 유지).

### 3. `app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterScreen.kt`
- 현재 `ManualGenStatus.Running` 표시 위치(BottomSheet 또는 별도 영역) 옆에 Retrying 분기 추가:
  ```kotlin
  is ManualGenStatus.Retrying -> {
      // 같은 spinner 유지 + status.message 텍스트 표시
  }
  ```
- TASK-033의 lazy loading UI는 보존.

## Out of Scope
- GeminiTopicSuggester 호출(키워드 추가 시 토픽 추천)에 동일 메시지 — 후속.
- 알람 자동 생성에 재시도 메시지 — TASK-027에서 비슷 패턴 채택.
- 재시도 횟수/딜레이 변경 — 그대로.

## Files Owned By This Task
- `app/src/main/java/com/dailynewsletter/service/GeminiRetry.kt`
- `app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterViewModel.kt`
- `app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterScreen.kt`

## Files Explicitly Not Owned
- `app/src/main/java/com/dailynewsletter/service/NewsletterGenerationService.kt` (변경 불필요 — withRetry 내부에서 emit).
- `app/src/main/java/com/dailynewsletter/service/GeminiTopicSuggester.kt`
- 그 외 모든 파일.

## Forbidden Changes
- No new dependency.
- No `withRetry` 시그니처 변경 (callback 인자 추가 X — SharedFlow로 처리).
- No `ManualGenStatus`의 기존 variant 제거.

## Acceptance Criteria
- [ ] `GeminiRetry.kt`에 `RetryEvent` sealed class + `events: SharedFlow<RetryEvent>` 정의.
- [ ] `withRetry`가 재시도 직전 `tryEmit`.
- [ ] `ManualGenStatus.Retrying(message)` data class 신규 variant.
- [ ] `NewsletterViewModel.init`에서 `GeminiRetry.events` collect 코루틴.
- [ ] Retrying 상태일 때 메시지에 `(${attempt}/${total})` 또는 동등 진행 정보 포함.
- [ ] `NewsletterScreen`에 `is ManualGenStatus.Retrying` 분기 (when 또는 if).
- [ ] 빌드 성공 또는 SKIPPED_ENVIRONMENT_NOT_AVAILABLE.

## Verification Command Candidates
- `grep -n "RetryEvent\|MutableSharedFlow\|class Retrying" app/src/main/java/com/dailynewsletter/service/GeminiRetry.kt`
- `grep -n "ManualGenStatus.Retrying\|GeminiRetry.events" app/src/main/java/com/dailynewsletter/ui/newsletter`
- `./gradlew :app:assembleDebug`

## Expected Implementer Output
- 변경 파일 3개. 핵심 코드 인용. grep 결과. 빌드 결과. 사용자 다음 동작 1줄.
