# Task Brief: 주제 화면에 수동 생성 버튼 (전체 키워드 + 주제 이력 입력)

Task ID: TASK-20260429-039a
Status: active

## Goal
키워드 등록 시 자동 주제 생성은 제거됐고(이전 변경), 이제 사용자가 주제 화면에서 명시적으로 주제 생성 트리거. 전체 keyword pending 목록 + 과거 topic 제목들을 GeminiTopicSuggester에 넘겨 새 주제 N개 제안 → Notion에 저장.

## User-visible behavior
- 주제 탭 우상단에 `+` 버튼 (또는 FloatingActionButton).
- 탭하면 즉시 생성 시작 (확인 시트 없음 — 단순 버튼).
- 생성 중: 화면 상단 배너 "주제 생성 중..." (NewsletterScreen 패턴 모방 가능).
- 503 등 transient → TASK-038의 retry/fallback 자동 적용 (GeminiTopicSuggester가 이미 GeminiRetry.withModelFallback 사용하게 됨).
- 성공: Snackbar "주제 N개 추가됨" + 주제 목록 새로고침.
- 실패: Snackbar 또는 AlertDialog "잠시 후 다시 시도해 주세요".

## Scope

### 1. `app/src/main/java/com/dailynewsletter/ui/topics/TopicsViewModel.kt`
- 의존성 추가: `keywordRepository: KeywordRepository`, `geminiTopicSuggester: GeminiTopicSuggester`.
- 신규 sealed class:
  ```kotlin
  sealed class TopicGenStatus {
      object Idle : TopicGenStatus()
      object Running : TopicGenStatus()
      data class Retrying(val message: String) : TopicGenStatus()
      data class Success(val count: Int) : TopicGenStatus()
      data class Failed(val message: String) : TopicGenStatus()
  }
  ```
- UiState에 `topicGenStatus: TopicGenStatus = TopicGenStatus.Idle` 추가.
- 신규 함수:
  ```kotlin
  fun generateTopicsManually() {
      viewModelScope.launch(exceptionHandler) {
          _uiState.update { it.copy(topicGenStatus = TopicGenStatus.Running) }
          runCatching {
              val pending = keywordRepository.getPendingKeywords()
              val past = topicRepository.getAllPastTopicTitles()
              val suggested = geminiTopicSuggester.suggest(pending, past)
              suggested.forEach { topic ->
                  topicRepository.saveTopic(
                      title = topic.title,
                      priorityType = topic.priorityType,
                      sourceKeywordIds = topic.sourceKeywordIds,
                      tags = emptyList()
                  )
              }
              suggested.size
          }.onSuccess { n ->
              _uiState.update { it.copy(topicGenStatus = TopicGenStatus.Success(n)) }
              loadTopics()
          }.onFailure { e ->
              val msg = e.message ?: "잠시 후 다시 시도해 주세요"
              _uiState.update { it.copy(topicGenStatus = TopicGenStatus.Failed(msg)) }
          }
      }
  }
  fun clearTopicGenStatus() { ... }
  ```
- `init`에서 `GeminiRetry.events`도 collect — RetryEvent.Retrying/Switching 발생 시 topicGenStatus가 Running일 때만 Retrying으로 업데이트.

### 2. `app/src/main/java/com/dailynewsletter/ui/topics/TopicsScreen.kt`
- TopAppBar `actions`에 `+` IconButton 추가 (또는 별도 FAB):
  ```kotlin
  IconButton(
      onClick = { viewModel.generateTopicsManually() },
      enabled = state.topicGenStatus !is TopicGenStatus.Running &&
                state.topicGenStatus !is TopicGenStatus.Retrying
  ) { Icon(Icons.Default.Add, contentDescription = "주제 생성") }
  ```
- 화면 상단(주제 목록 위)에 진행 배너 — `topicGenStatus`가 Running 또는 Retrying이면 spinner + 메시지 (NewsletterScreen의 ManualGenBanner 패턴 흉내).
- LaunchedEffect로 Success/Failed 처리:
  - Success → Snackbar "주제 N개 추가됨" → clearTopicGenStatus().
  - Failed → AlertDialog "생성을 마치지 못했어요 / 잠시 후 다시 시도해 주세요" + 확인 버튼.

## Out of Scope
- 키워드 길게 눌러 생성 (TASK-039b — 후속).
- 주제 우선순위/드래그드롭/상세 화면 — TASK-031.
- TopicsScreen의 다른 UI 변경 — 0.

## Files Owned By This Task
- `app/src/main/java/com/dailynewsletter/ui/topics/TopicsViewModel.kt`
- `app/src/main/java/com/dailynewsletter/ui/topics/TopicsScreen.kt`

## Files Explicitly Not Owned
- `app/src/main/java/com/dailynewsletter/ui/keyword/*` (TASK-030 점유)
- `app/src/main/java/com/dailynewsletter/service/*` (TASK-038 점유)
- 그 외 모든 파일.

## Forbidden Changes
- No new dependency.
- No 다른 ViewModel/Screen/Service/Repository 변경.

## Acceptance Criteria
- [ ] `TopicGenStatus` sealed class 정의.
- [ ] `TopicsViewModel.generateTopicsManually()` 함수 존재.
- [ ] `TopicsScreen` TopAppBar 또는 FAB에 + 버튼 + onClick → generateTopicsManually.
- [ ] 진행 배너 코드 (Running/Retrying 분기).
- [ ] Failed 분기에 AlertDialog.
- [ ] 빌드 성공 또는 SKIPPED_ENVIRONMENT_NOT_AVAILABLE.

## Verification Command Candidates
- `grep -n "TopicGenStatus\\|generateTopicsManually" app/src/main/java/com/dailynewsletter/ui/topics`
- `./gradlew :app:assembleDebug`

## Expected Implementer Output
- 변경 2 파일. 핵심 코드 인용. grep 결과. 빌드 결과. 사용자 다음 동작 1줄.

## STOP_AND_ESCALATE
- `KeywordRepository.getPendingKeywords()` 함수가 없으면 — 이전 KeywordViewModel auto-gen 코드에서 호출 흔적 있었으므로 존재할 것. 없으면 escalate.
