# Task Brief: SuggestedTopic data class 복원 (TASK-009 누락분)

Task ID: TASK-20260426-010
Status: active
Depends on: TASK-20260426-009 (완료됨)

## Goal
TASK-009에서 `ClaudeTopicSuggester.kt`를 삭제하면서 그 안에 있던 `data class SuggestedTopic`도 같이 사라졌다. `GeminiTopicSuggester.kt`가 이 타입을 그대로 참조하고 있어서 빌드가 깨진다. 데이터 클래스를 복원해 빌드 통과.

## User-visible behavior
- 빌드 성공.
- 런타임 동작 변화 없음 (TASK-009 의도 그대로).

## Build Errors (확인된 실패)
```
GeminiTopicSuggester.kt:29:13 Unresolved reference 'SuggestedTopic'
GeminiTopicSuggester.kt:33:47 Not enough information to infer type argument for 'T'
GeminiTopicSuggester.kt:95:62 Unresolved reference 'SuggestedTopic'
```

## Scope
- `data class SuggestedTopic`을 적절한 파일에 추가한다.
- 권장 위치: `service/GeminiTopicSuggester.kt`의 클래스 선언 직전(같은 패키지 `com.dailynewsletter.service`).
- 필드는 기존(삭제된 ClaudeTopicSuggester.kt) 정의와 동일:
  ```kotlin
  data class SuggestedTopic(
      val title: String,
      val priorityType: String,         // "direct" | "prerequisite" | "peripheral"
      val sourceKeywordIds: List<String>,
      val reason: String
  )
  ```

## Out of Scope
- API 호출 로직, 프롬프트, JSON 파싱 변경 없음.
- 다른 ViewModel/Repository/UI 변경 없음.
- 기존 import 정리 외의 import 변경 없음.

## Files Likely Involved
- `app/src/main/java/com/dailynewsletter/service/GeminiTopicSuggester.kt`

## Files Owned By This Task
- `app/src/main/java/com/dailynewsletter/service/GeminiTopicSuggester.kt`

## Files Explicitly Not Owned
- 그 외 모든 파일.

## Forbidden Changes
- No new dependency.
- No prompt change.
- No public API rename.
- No file rename/move beyond adding the data class.
- TASK-007/TASK-008/TASK-009 변경분 보존.

## Acceptance Criteria
- [ ] `data class SuggestedTopic`이 `GeminiTopicSuggester.kt`(또는 service 패키지 내) 어디에든 1회 정의되어 있다.
- [ ] 필드 4개(`title`, `priorityType`, `sourceKeywordIds`, `reason`) 모두 동일.
- [ ] grep `data class SuggestedTopic app/src/main` → 정확히 1 hit.
- [ ] grep `import .*SuggestedTopic app/src/main` 결과로 끊긴 참조 없음 (내부 패키지 동일이라 import 불필요할 수 있음).
- [ ] 빌드 성공 또는 SKIPPED_ENVIRONMENT_NOT_AVAILABLE.

## Verification Command Candidates
- `grep -rn "data class SuggestedTopic" app/src/main`
- `grep -rn "SuggestedTopic" app/src/main` (참조 위치 확인용)
- `./gradlew :app:assembleDebug`

## Expected Implementer Output
- 변경 파일 1개.
- 추가된 줄 인용 (5–7줄).
- grep 결과 인용.
- 빌드 결과(또는 SKIPPED).
