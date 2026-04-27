# Task Brief: Gemini 구조화 출력 모드 적용 + 토큰 한도 상향

Task ID: TASK-20260426-011
Status: active
Depends on: TASK-20260426-009, TASK-20260426-010 (완료됨)

## Goal
키워드 등록 → 자동 주제 생성 시 발생한 `"Gemini 응답에서 JSON 배열을 찾지 못했습니다"` 오류를 근원에서 차단한다. Gemini의 공식 **JSON mode**(responseMimeType)를 사용해 마크다운 fence 없는 순수 JSON 반환을 강제하고, maxOutputTokens를 상향해 응답 잘림도 방지한다.

## User-visible behavior
- 키워드 추가 → 스낵바 "주제 N건 생성됨" 표시 + Notion Topics DB에 N건 추가.
- 응답 파싱 실패 가능성 거의 0.
- 실패 시 에러 메시지에 응답 원문 일부가 포함되어 진단 가능 (현재 동작 유지).

## Root cause 가설 (확인된 증상)
- 사용자 보고: `Gemini 응답에서 JSON 배열을 찾지 못했습니다. json [ € "title". ...`
- `take(200)` 절단이라 닫는 `]` 유무 미확인 — 둘 중 하나:
  1. `maxOutputTokens=2048`로 응답이 닫는 `]` 전에 잘림.
  2. Gemini가 마크다운 fence를 비표준 형식으로 생성(혹은 fence 없는 일반 텍스트).

JSON mode를 켜면 두 가능성 모두 무력화: 응답이 항상 순수 JSON object/array로만 옴.

## Scope
1. `data/remote/gemini/GeminiModels.kt`:
   - `GeminiGenerationConfig`에 `@SerializedName("responseMimeType") val responseMimeType: String? = null` 필드 추가.
2. `service/GeminiTopicSuggester.kt`:
   - 요청 시 `generationConfig`에 `responseMimeType = "application/json"`, `maxOutputTokens = 8192` 지정.
   - 프롬프트에서 ```json``` 마크다운 예시 제거 (`responseMimeType`로 강제하므로 불필요). 단, "JSON 배열만 출력" 지시는 유지.
   - `extractJsonArray` 함수는 그대로 유지 (방어용 fallback). 단, JSON mode가 정상 동작하면 첫 호출에서 fenced regex와 무관하게 `text.indexOf('[')` 경로로 통과.

## Out of Scope
- `NewsletterGenerationService.kt` 수정 없음 (HTML 출력이라 JSON mode 부적합 — 별도 후속 결정).
- 프롬프트 본문 의미 변경 없음.
- `SuggestedTopic` 데이터 클래스, JSON 파싱 후처리, ViewModel orchestration 변경 없음.
- Notion API 변경 없음.

## Gemini API 사양 (참고)
출처: https://ai.google.dev/gemini-api/docs/structured-output

JSON mode 요청 예시:
```json
{
  "contents": [{ "role": "user", "parts": [{ "text": "..." }] }],
  "generationConfig": {
    "maxOutputTokens": 8192,
    "temperature": 0.7,
    "responseMimeType": "application/json"
  }
}
```

`responseMimeType`을 `"application/json"`으로 지정하면 응답의 `candidates[0].content.parts[0].text`는 마크다운 fence 없는 순수 JSON 문자열.

(`responseSchema`까지 지정하면 더 엄격하지만 본 Brief에서는 불필요 — 프롬프트 안내만으로 충분.)

## Files Likely Involved
- `app/src/main/java/com/dailynewsletter/data/remote/gemini/GeminiModels.kt`
- `app/src/main/java/com/dailynewsletter/service/GeminiTopicSuggester.kt`

## Files Owned By This Task
- 위 2개.

## Files Explicitly Not Owned
- `app/src/main/java/com/dailynewsletter/service/NewsletterGenerationService.kt`
- 그 외 모든 파일.

## Forbidden Changes
- No new dependency.
- No prompt 의미 변경 (마크다운 예시 줄 제거만 허용).
- No public API rename.
- No file rename/delete.
- TASK-007/008/009/010 변경분 보존.

## Acceptance Criteria
- [ ] `GeminiGenerationConfig`에 `responseMimeType` 필드 추가, default null.
- [ ] `GeminiTopicSuggester`가 요청 시 `responseMimeType = "application/json"`, `maxOutputTokens = 8192`로 호출.
- [ ] grep `responseMimeType` → 정의 1 + 사용 1 = 2 hits.
- [ ] grep `maxOutputTokens = 8192` → 1 hit (GeminiTopicSuggester).
- [ ] `extractJsonArray` 함수 시그니처/구현 유지 (방어용).
- [ ] 빌드 성공 또는 SKIPPED_ENVIRONMENT_NOT_AVAILABLE.

## Verification Command Candidates
- `grep -rn "responseMimeType" app/src/main`
- `grep -rn "maxOutputTokens = 8192" app/src/main`
- `./gradlew :app:assembleDebug`

## Expected Implementer Output
- 변경 파일 2개.
- 핵심 줄 인용 (변경 전/후).
- grep 결과.
- 빌드 결과(또는 SKIPPED).
- 사용자 다음 동작 1줄 (재빌드 + 키워드 재시도).

## STOP_AND_ESCALATE
- Gemini가 `responseMimeType` 필드를 다른 이름으로 받는다고 의심되면 escalate.
- 8192 토큰이 무료 티어 제한을 넘는다고 의심되면 escalate (실제로는 8192는 무료 티어 내).
