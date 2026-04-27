# Task Brief: Claude → Gemini API 전환 (전체)

Task ID: TASK-20260426-009
Status: active
Depends on: TASK-20260426-008 (KeywordViewModel.kt 동시 편집 회피)

## Goal
주제 제안(`ClaudeTopicSuggester`)과 뉴스레터 본문 생성(`NewsletterGenerationService`) 양쪽 모두를 Anthropic Claude API에서 Google Gemini API로 전환한다. 기존 Claude 관련 인프라는 깔끔히 제거(데드 코드 남기지 않음).

## User-visible behavior
- 설정 화면의 "Claude AI 연동" 섹션이 "Gemini AI 연동"으로 라벨 변경, 입력 필드도 "Gemini API Key".
- 키 입력 후 키워드 등록 → Gemini 호출로 주제가 1~N건 자동 생성되어 Notion Topics DB에 추가.
- 뉴스레터 수동 생성 버튼 → Gemini 호출로 HTML 본문 생성 후 Notion Newsletters DB에 저장.
- 실패 시 스낵바에 사유 표시(401/403/429 등 Gemini 응답 메시지를 그대로 노출).

## Scope
1. **Gemini 클라이언트 신설** (`app/src/main/java/com/dailynewsletter/data/remote/gemini/`):
   - `GeminiApi.kt` Retrofit 인터페이스.
   - `GeminiModels.kt` 요청/응답 데이터 클래스.
2. **DI**: `di/NetworkModule.kt`의 `@Named("claude")` Retrofit + `provideClaudeApi()`를 제거하고, 동일 패턴의 `@Named("gemini")` Retrofit + `provideGeminiApi()` 추가.
3. **Settings 키 전환**:
   - `SettingsEntity.KEY_CLAUDE_API_KEY = "claude_api_key"` 제거 또는 `KEY_GEMINI_API_KEY = "gemini_api_key"`로 교체.
   - `SettingsRepository.getClaudeApiKey()` → `getGeminiApiKey()`로 rename.
4. **UI**:
   - `SettingsViewModel`: state `claudeApiKey` → `geminiApiKey`, setup 가드 조건도 갱신.
   - `SettingsScreen`: 섹션 타이틀/필드 라벨 변경.
5. **호출지 교체**:
   - `service/ClaudeTopicSuggester.kt` 삭제 → `service/GeminiTopicSuggester.kt` 신설(같은 메서드 시그니처: `suggest(pendingKeywords, pastTopicTitles): List<SuggestedTopic>`). `SuggestedTopic` data class는 그대로 유지(필드/의미 동일).
   - `service/NewsletterGenerationService.kt`: `claudeApi` 의존을 `geminiApi`로 교체, 호출 코드만 수정. 다른 로직(`markTopicsConsumed` 등) 유지.
   - `ui/keyword/KeywordViewModel.kt`: 주입 타입을 `GeminiTopicSuggester`로 교체, 에러 메시지 문자열 "Claude API 키" → "Gemini API 키".
6. **Claude 인프라 삭제**:
   - `data/remote/claude/ClaudeApi.kt`, `data/remote/claude/ClaudeModels.kt` 삭제.
   - `claude` 디렉터리 자체 제거.

## Out of Scope
- 프롬프트 내용 변경 없음(기존 프롬프트 그대로 Gemini에 전달).
- Notion 호출 로직 변경 없음.
- Worker/Schedule 변경 없음.
- 응답 JSON 추출 로직(extractJsonArray)은 기존 패턴 그대로 재사용.
- 단위 테스트 신규 작성은 선택 사항(기존 테스트가 없으면 생략 가능).

## Gemini API 사양 (참고: https://ai.google.dev/gemini-api/docs/text-generation)

엔드포인트:
```
POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent
```

기본 모델: `gemini-2.5-flash`

인증: 헤더 `x-goog-api-key: {API_KEY}` (URL `?key=` 방식보다 헤더 권장).

요청 바디 예시:
```json
{
  "contents": [
    { "role": "user", "parts": [{ "text": "..." }] }
  ],
  "generationConfig": {
    "maxOutputTokens": 2048,
    "temperature": 0.7
  }
}
```

응답 본문 예시:
```json
{
  "candidates": [
    {
      "content": {
        "parts": [{ "text": "..." }],
        "role": "model"
      },
      "finishReason": "STOP"
    }
  ],
  "usageMetadata": { ... }
}
```

추출 경로: `candidates[0].content.parts[0].text`.

엔드포인트가 path에 model을 받기 때문에 Retrofit의 `@Path` 또는 `@Url` 사용. 추천:
```kotlin
@POST("v1beta/models/{model}:generateContent")
suspend fun generateContent(
    @Header("x-goog-api-key") apiKey: String,
    @Path("model") model: String,
    @Body request: GeminiRequest
): GeminiResponse
```

base URL은 `https://generativelanguage.googleapis.com/`.

> 출처: Google Gemini API 공식 문서 (2024–2025년 v1beta) — implementer가 구현 직전에 위 URL을 한 번 확인하여 필드 변경 여부 점검.

## Files Likely Involved
- 신규 생성:
  - `app/src/main/java/com/dailynewsletter/data/remote/gemini/GeminiApi.kt`
  - `app/src/main/java/com/dailynewsletter/data/remote/gemini/GeminiModels.kt`
  - `app/src/main/java/com/dailynewsletter/service/GeminiTopicSuggester.kt`
- 수정:
  - `app/src/main/java/com/dailynewsletter/di/NetworkModule.kt`
  - `app/src/main/java/com/dailynewsletter/data/local/entity/SettingsEntity.kt`
  - `app/src/main/java/com/dailynewsletter/data/repository/SettingsRepository.kt`
  - `app/src/main/java/com/dailynewsletter/ui/settings/SettingsViewModel.kt`
  - `app/src/main/java/com/dailynewsletter/ui/settings/SettingsScreen.kt`
  - `app/src/main/java/com/dailynewsletter/service/NewsletterGenerationService.kt`
  - `app/src/main/java/com/dailynewsletter/ui/keyword/KeywordViewModel.kt`
- 삭제:
  - `app/src/main/java/com/dailynewsletter/data/remote/claude/ClaudeApi.kt`
  - `app/src/main/java/com/dailynewsletter/data/remote/claude/ClaudeModels.kt`
  - `app/src/main/java/com/dailynewsletter/service/ClaudeTopicSuggester.kt`

## Files Owned By This Task
위 "Files Likely Involved" 목록 전부.

## Files Explicitly Not Owned
- `app/src/main/java/com/dailynewsletter/ui/keyword/KeywordScreen.kt`
- `app/src/main/java/com/dailynewsletter/ui/topics/*`
- `app/src/main/java/com/dailynewsletter/ui/newsletter/*`
- `app/src/main/java/com/dailynewsletter/data/repository/KeywordRepository.kt`
- `app/src/main/java/com/dailynewsletter/data/repository/TopicRepository.kt`
- `app/src/main/java/com/dailynewsletter/data/repository/NewsletterRepository.kt`
- `app/src/main/java/com/dailynewsletter/service/NotionSetupService.kt`
- `app/src/main/java/com/dailynewsletter/data/tag/TagNormalizer.kt`
- 모든 Worker / Compose Screen / build.gradle / AndroidManifest.

## Forbidden Changes
- No new dependency (Retrofit/OkHttp/Gson 그대로 재사용 — Gemini는 평범한 REST + JSON).
- No DB schema change / Room migration.
- No Notion API 호출 로직 변경.
- No prompt rewording.
- No background scheduling change.
- No public API contract change beyond Suggester class rename.
- 기존 사용자가 입력해 둔 Claude API 키 값은 Gemini 키 칸에는 자동 마이그레이션하지 않음(다른 키이므로) — 사용자가 새로 입력. 기존 행은 DB에 남겨두든 `SettingsEntity`에서 키 상수만 제거하든 자유.

## Android Constraints
- 기존 Hilt + Retrofit 패턴 그대로.
- Coroutine suspend 함수, Gson 직렬화, Logging interceptor 재사용.
- ViewModel side effect는 launch + exceptionHandler 패턴 유지.
- `KeywordViewModel`에서 에러 분기 문자열만 갱신 (`API Key` 매칭 그대로 두면 다국어 양쪽 다 잡힘).

## Acceptance Criteria
- [ ] `grep -r ClaudeApi app/src/main` 결과 0건.
- [ ] `grep -r claude_api_key app/src/main` 결과 0건 (키 상수 제거 확인).
- [ ] `grep -r ClaudeTopicSuggester app/src/main` 결과 0건 (TODO 주석 포함; `TopicsViewModel.kt`의 옛 주석은 같은 컨텍스트에서 살릴 거면 GeminiTopicSuggester로 갱신, 아니면 그대로 두되 grep 0건 조건은 명시 — 결정 시 implementer 보고).
- [ ] `grep -rn "x-goog-api-key" app/src/main` 결과 ≥1건.
- [ ] 신규 파일 3개 존재: `GeminiApi.kt`, `GeminiModels.kt`, `GeminiTopicSuggester.kt`.
- [ ] 삭제 파일 3개 부재: `ClaudeApi.kt`, `ClaudeModels.kt`, `ClaudeTopicSuggester.kt`.
- [ ] 빌드 성공 또는 SKIPPED_ENVIRONMENT_NOT_AVAILABLE 보고.

## Verification Command Candidates
- 정적 grep (위 acceptance 6개)
- `./gradlew :app:assembleDebug`
- `./gradlew :app:testDebugUnitTest`

## Expected Implementer Output
- 신규/수정/삭제 파일 목록.
- `GeminiApi.kt`/`GeminiModels.kt`/`GeminiTopicSuggester.kt` 핵심 줄 인용.
- `NetworkModule.kt`/`SettingsScreen.kt` 변경 줄 인용.
- grep 6개 결과.
- 빌드 결과(또는 SKIPPED).
- 사용자가 다음으로 해야 할 동작 1줄 (예: "설정 화면에서 Gemini API Key 새로 입력").

## STOP_AND_ESCALATE 조건
- Gemini API 응답 형식이 위 사양과 다르게 동작하는 것이 의심되면 (예: 필드 이름이 바뀌었거나 v1beta 폐기) — implementer는 임의 추정하지 말고 STOP_AND_ESCALATE.
- 의존성 추가가 필요하다고 판단되면 STOP_AND_ESCALATE (이 Brief는 "no new dependency").

## 재현/검증 시나리오 (참고)
1. 사용자가 Google AI Studio에서 Gemini API Key 발급(`https://aistudio.google.com/apikey`).
2. 앱 설정 화면 → "Gemini AI 연동" 섹션 → API Key 입력.
3. 키워드 추가 → "주제 N건 생성됨" 스낵바.
4. Notion Topics DB에 N건 추가 확인.
5. 뉴스레터 수동 생성 → Newsletters DB에 1건 추가 + Topics consumed 처리.
