# Task Brief: Gemini 503/UNAVAILABLE 재시도 + 명확한 에러 메시지

Task ID: TASK-20260429-034
Status: active

## Goal
Gemini가 일시적 과부하(503/UNAVAILABLE)로 응답 실패하는 경우 자동 재시도(지수 백오프)로 회피. 모든 재시도 실패 시 사용자에게 명확한 메시지 노출.

## User-visible behavior
- 뉴스레터 생성 또는 토픽 추천 중 Gemini가 503 반환 → 어플이 백그라운드에서 자동 재시도 (사용자 추가 동작 없음).
- 최대 3회 재시도 (1.5s, 3s, 6s 백오프) 후에도 실패 → "Gemini가 일시적으로 혼잡합니다. 잠시 후 다시 시도해 주세요" 메시지.
- 다른 에러(400, 401, 429 등)는 재시도 없이 즉시 에러.

## Scope

### 1. 신규 helper `app/src/main/java/com/dailynewsletter/service/GeminiRetry.kt`
```kotlin
object GeminiRetry {
    private const val TAG = "GeminiRetry"
    private val DELAYS_MS = longArrayOf(1500, 3000, 6000)

    suspend fun <T> withRetry(label: String, block: suspend () -> T): T {
        var lastError: Throwable? = null
        for (attempt in 0..DELAYS_MS.size) {
            try {
                return block()
            } catch (e: HttpException) {
                lastError = e
                val code = e.code()
                val transient = code == 503 || code == 502 || code == 504
                if (!transient || attempt == DELAYS_MS.size) {
                    throw if (transient) IllegalStateException(
                        "Gemini가 일시적으로 혼잡합니다. 잠시 후 다시 시도해 주세요"
                    ) else e
                }
                val delayMs = DELAYS_MS[attempt]
                Log.w(TAG, "$label HTTP $code, retry in ${delayMs}ms (attempt ${attempt + 1})")
                delay(delayMs)
            }
        }
        throw lastError ?: IllegalStateException("$label failed unexpectedly")
    }
}
```
- import: `retrofit2.HttpException`, `kotlinx.coroutines.delay`, `android.util.Log`.

### 2. `app/src/main/java/com/dailynewsletter/service/NewsletterGenerationService.kt`
- `geminiApi.generateContent(...)` 호출을 `GeminiRetry.withRetry("newsletter") { geminiApi.generateContent(...) }` 으로 감싼다.
- 다른 부분(파싱, 저장 등) 변경 0.

### 3. `app/src/main/java/com/dailynewsletter/service/GeminiTopicSuggester.kt`
- 동일하게 `geminiApi.generateContent(...)`를 `GeminiRetry.withRetry("topic-suggest") { ... }` 로 감싼다.

## Out of Scope
- 429 (quota) 별도 처리 — 후속.
- Notion 503 재시도 — 후속.
- 다른 transient 코드(timeout 등) — 후속.

## Files Owned By This Task
- `app/src/main/java/com/dailynewsletter/service/GeminiRetry.kt` (신규)
- `app/src/main/java/com/dailynewsletter/service/NewsletterGenerationService.kt`
- `app/src/main/java/com/dailynewsletter/service/GeminiTopicSuggester.kt`

## Files Explicitly Not Owned
- 그 외 모든 파일 (특히 NewsletterRepository, ViewModel, Screen — TASK-033 소유).

## Forbidden Changes
- No new dependency.
- No GeminiApi 시그니처 변경.

## Acceptance Criteria
- [ ] `GeminiRetry.kt` 신규 파일 존재.
- [ ] `withRetry` 함수가 503/502/504 transient만 재시도.
- [ ] `NewsletterGenerationService`의 `generateContent` 호출이 `withRetry`로 감싸져 있음.
- [ ] `GeminiTopicSuggester`의 `generateContent` 호출도 `withRetry`로 감싸져 있음.
- [ ] 빌드 성공 또는 SKIPPED_ENVIRONMENT_NOT_AVAILABLE.

## Verification Command Candidates
- `grep -rn "withRetry\|GeminiRetry" app/src/main/java/com/dailynewsletter/service`
- `./gradlew :app:assembleDebug`

## Expected Implementer Output
- 변경 파일 3개 (1신규 + 2수정). 핵심 코드 인용. grep 결과. 빌드 결과. 사용자 다음 동작 1줄 (다음 503 발생 시 자동 재시도되는지 logcat 확인).

## STOP_AND_ESCALATE
- `HttpException`이 retrofit2 패키지에서 인식 안 되면 (다른 import 경로) — 현재 코드의 GeminiApi가 어떤 wrapper로 throws하는지 확인 후 적절한 catch 타입 결정. retrofit2 직접이면 그대로, OkHttp 수준이면 IOException. escalate 불필요.
