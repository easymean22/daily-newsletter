# Task Brief: 명시적 Exponential Backoff + Flash-Lite 자동 전환

Task ID: TASK-20260429-038
Status: active

## Goal
Gemini 2.5 Flash가 503 등 transient 에러를 반복하면 (1) 더 길고 명시적인 exponential backoff로 재시도, (2) 그래도 실패 시 자동으로 `gemini-2.5-flash-lite`로 전환해 다시 시도. 사용자는 모달 실패 메시지 보기 전 가능한 모든 회복 경로를 거침.

## User-visible behavior
- 503 발생 → 기존 배너 메시지 그대로 ("잠시 후 다시 시도하고 있어요 (1/N)").
- Flash 시도 모두 소진 → 배너 메시지 전환: "다른 모델로 시도하고 있어요" + 모델 전환 후 재시도 진행 표시.
- Flash-Lite 시도도 모두 실패 시에만 기존 AlertDialog ("생성을 마치지 못했어요...").

## Scope

### 1. `app/src/main/java/com/dailynewsletter/service/GeminiRetry.kt`
- Backoff 더 적극적으로:
  ```kotlin
  // base 1500ms, 2^n 증가, jitter 0~500ms
  private val DELAYS_MS = longArrayOf(1500, 3000, 6000, 12000)  // 4 attempts → max wait 22.5s
  ```
- jitter 추가 (thundering herd 방지):
  ```kotlin
  val delayMs = DELAYS_MS[attempt] + (0..500L).random()
  ```
- `RetryEvent.Switching(label: String, toModel: String)` variant 추가.
- 새 함수:
  ```kotlin
  suspend fun <T> withModelFallback(
      label: String,
      primaryModel: String,
      fallbackModel: String,
      block: suspend (model: String) -> T
  ): T {
      return try {
          withRetry(label) { block(primaryModel) }
      } catch (e: IllegalStateException) {
          // primary backoff exhausted (transient)
          Log.w(TAG, "$label primary $primaryModel exhausted, fallback → $fallbackModel")
          _events.tryEmit(RetryEvent.Switching(label, fallbackModel))
          withRetry("$label-fallback") { block(fallbackModel) }
      }
  }
  ```
- 기존 `withRetry` 시그니처는 그대로 (호환성).

### 2. `app/src/main/java/com/dailynewsletter/service/GeminiTopicSuggester.kt`
- `companion object`에 `const val FALLBACK_MODEL = "gemini-2.5-flash-lite"` 추가.
- `geminiApi.generateContent` 호출을 `GeminiRetry.withModelFallback("topic-suggest", DEFAULT_MODEL, FALLBACK_MODEL) { model -> geminiApi.generateContent(apiKey, model, request) }` 로 변경.
- 기존 prompt/parsing 변경 0.

### 3. `app/src/main/java/com/dailynewsletter/service/NewsletterGenerationService.kt`
- `geminiApi.generateContent` 호출을 동일하게 `GeminiRetry.withModelFallback("newsletter", GeminiTopicSuggester.DEFAULT_MODEL, GeminiTopicSuggester.FALLBACK_MODEL) { model -> geminiApi.generateContent(...) }` 로 변경.
- 기존 prompt/parsing/파일 흐름 변경 0.

### 4. `app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterViewModel.kt`
- 기존 `RetryEvent.Retrying` collect 분기에 `RetryEvent.Switching` 분기 추가:
  ```kotlin
  is RetryEvent.Switching -> {
      val msg = "다른 모델로 시도하고 있어요"
      _uiState.update { it.copy(manualGenStatus = ManualGenStatus.Retrying(msg)) }
  }
  ```

## Out of Scope
- 4xx (400/401/429) 처리 변경 — 그대로.
- Notion API 재시도 — 후속.
- 모델 선택 UI — 후속 (현재는 코드 상수로 전환).
- 다른 모델로의 fallback (gemini-2.5-pro 등) — 후속.

## Files Owned By This Task
- `app/src/main/java/com/dailynewsletter/service/GeminiRetry.kt`
- `app/src/main/java/com/dailynewsletter/service/GeminiTopicSuggester.kt`
- `app/src/main/java/com/dailynewsletter/service/NewsletterGenerationService.kt`
- `app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterViewModel.kt`

## Files Explicitly Not Owned
- 그 외 모든 파일 (특히 KeywordRepository/ViewModel/Screen — 병렬 TASK-030 소유).

## Forbidden Changes
- No new dependency.
- No `withRetry` 시그니처 제거 (새 `withModelFallback`은 추가).

## Acceptance Criteria
- [ ] `GeminiRetry.kt`에 `withModelFallback` 함수 정의.
- [ ] `RetryEvent.Switching(label, toModel)` variant 추가.
- [ ] DELAYS_MS가 1500/3000/6000/12000 (4단계).
- [ ] jitter 추가 (`(0..500L).random()` 또는 동등).
- [ ] `GeminiTopicSuggester`에 `FALLBACK_MODEL = "gemini-2.5-flash-lite"` 상수.
- [ ] NewsletterGenerationService의 generateContent 호출이 `withModelFallback` 안.
- [ ] GeminiTopicSuggester의 generateContent 호출이 `withModelFallback` 안.
- [ ] NewsletterViewModel이 `RetryEvent.Switching` 처리.
- [ ] 빌드 성공 또는 SKIPPED_ENVIRONMENT_NOT_AVAILABLE.

## Verification Command Candidates
- `grep -n "withModelFallback\\|Switching\\|FALLBACK_MODEL\\|gemini-2.5-flash-lite" app/src/main/java/com/dailynewsletter`
- `./gradlew :app:assembleDebug`

## Expected Implementer Output
- 변경 4 파일. 핵심 코드 인용. grep 결과. 빌드 결과. 사용자 다음 동작 1줄.

## STOP_AND_ESCALATE
- `gemini-2.5-flash-lite` 모델 ID가 실제 Gemini API에서 안 받아지면(404) — 다른 fallback 후보(예: `gemini-2.0-flash`)로 갈지 사용자에게 escalate.
