---
updated: 2026-04-19
summary: "implementer 체크리스트 11단계"
parent: ./README.md
---

# 단계별 플랜 (implementer 체크리스트)

> **전제**: [tag-system](../tag-system/README.md)의 1~4단계가 완료되어 있어야 함 (Notion 모델·정규화·invariant·Repository 시그니처). 5단계(`listAvailableTagNames`)는 선택 — 본 플랜은 빈 풀 fallback이 가능하므로 없어도 진행 가능.

## 1단계: `ClaudeTopicSuggester` 신설 + 프롬프트 변경

- [x] `service/ClaudeTopicSuggester.kt` 신설. `SuggestedTopic` 데이터 클래스 + `suggest(...)` 메서드.
- [x] 본체 로직은 기존 `TopicSelectionService.selectAndSaveTopics` 본체에서 가져옴 (Claude 호출 + 응답 파싱).
- [x] 응답 스키마에 `tags` 필드 없음 (라운드 1 위반 금지). 파싱 실패 시 빈 리스트 fallback.
- [x] 태그 가이드 섹션 미포함 (라운드 1 — Claude는 태그에 관여하지 않음).
- [x] `availableTagPool` 파라미터 폐기 (라운드 1 정합).

## 2단계: 사이클 해소 — `TopicRepository`에서 `TopicSelectionService` 의존성 제거

- [x] `TopicRepository.kt`의 `topicSelectionService` 생성자 파라미터 제거.
- [x] `TopicRepository.regenerateTopics()` 메서드 제거 (TopicSelectionService 호출 라인 포함).
- [x] **`TopicSelectionService` 파일 삭제**.
- [x] 컴파일 확인 (정적 검사 — 빌드 환경 미설치로 SKIPPED_ENVIRONMENT_NOT_AVAILABLE).

## 3단계: `TopicsViewModel` orchestration (최소 — 정합 유지)

- [x] `regenerateTopics()` stub 유지 (TopicsScreen 컴파일 정합. 실제 orchestration은 단계 7 영역).
- [x] `TopicSelectionService` 호출 제거 (2단계에서 이미 처리).
- [ ] 전체 orchestration 재구현 (단계 7 범위 — Scope A 비범위).

## 4단계: `KeywordRepository.addKeyword` 시그니처 + 반환 타입 확장

- [x] `addKeyword(text, type, tags)` 시그니처 — tags 파라미터 이미 TASK-003에서 추가됨.
- [x] 반환 타입 `Unit → KeywordUiItem`. Notion `createPage` 응답의 `id`를 사용해 매핑.
- [x] `refreshKeywords()` 호출 유지 (StateFlow 갱신).

## 5단계: `KeywordViewModel` 자동 경로 orchestration

- [x] `KeywordViewModel`에 `ClaudeTopicSuggester`, `TopicRepository` 주입 (`NewsletterRepository` 미주입 — 라운드 1 scope 밖).
- [x] `addKeyword(text, type, tags)` 구현: 키워드 추가 후 즉시 `suggest()` → `saveTopic(..., emptyList())` 루프.
- [x] `KeywordUiState`에 `autoGenStatus` 필드 추가 (sealed class: `Idle / Running / Success(n) / Failed(msg)`).

## 6단계: KeywordScreen UI — 태그 입력 + 자동 경로 스낵바

- [x] BottomSheet에 태그 칩 입력 필드 추가 (`TagInput` composable — OutlinedTextField + Add 버튼 + InputChip 제거).
- [x] `addKeyword` 호출 시 tags 인자 전달.
- [x] `autoGenStatus`를 `SnackbarHost` + `LaunchedEffect`로 연동 — Running/Success/Failed 한국어 메시지.
- [x] [보기] NavController 이동은 미포함 (메시지만으로 충분 — 선택 사항이므로 defer).

## 7단계: TopicsScreen UI — 수동 버튼 의미 보강 + 직접 작성 진입점

- [ ] TopAppBar 액션에 `+` 아이콘 추가 (직접 작성). 기존 `Refresh` 아이콘은 유지하되 contentDescription을 "주제 생성"으로 변경.
- [ ] BottomSheet 컴포저블 신설 — 제목 OutlinedTextField + 태그 칩 입력 + 취소/저장.
- [ ] 저장 시 `viewModel.addManualTopic(title, tags)` 호출.
- [ ] 빈 상태 문구 변경: "아직 선정된 주제가 없습니다. 상단 ↻ 또는 + 버튼으로 추가하세요."
- [ ] (선택) 카드에 태그 배지 표시 — 태그가 있으면 칩으로, 없으면 표시 안 함 (invariant에 의해 최소 모든주제는 항상 있음).

## 8단계: `DailyTopicWorker` + 스케줄 제거

- [x] `worker/DailyTopicWorker.kt` 파일 삭제.
- [x] `WorkScheduler.scheduleDailyTopicSelection` 메서드 + `scheduleAll()`의 호출 제거.
- [x] `WorkScheduler.scheduleAll()` 진입부에 `workManager.cancelUniqueWork("daily_topic_selection")` 추가.
- [x] 알림 채널 `CHANNEL_TOPICS` 유지 (DailyNewsletterApp.kt 미수정).

## 9단계: DI 점검

- [x] `ClaudeTopicSuggester`는 `@Singleton` + `@Inject constructor` → Hilt 자동 발견. 별도 모듈 수정 불필요.
- [x] `AppModule.kt` / `NetworkModule.kt`에 `TopicSelectionService`·`DailyTopicWorker` 관련 `@Provides` 없음 (자동 binding이었으므로 별도 제거 불필요).
- [x] `KeywordViewModel`은 `@HiltViewModel` 그대로 — `ClaudeTopicSuggester`·`TopicRepository` 신규 의존성 자동 발견.

## 10단계: 수동 E2E 검증 (spec §4 acceptance와 정합)

- [ ] 앱 신규 설치 → setup 완료 → 키워드 화면.
- [ ] BottomSheet에서 키워드 1개 + 태그 0개 추가 → 키워드 목록에 추가 + 스낵바 "주제 생성 중…" 노출 + 수 초 후 "주제 N개가 생성됐어요" 또는 실패 메시지.
- [ ] Topics 화면 진입 → 자동 경로로 만들어진 주제들이 보임. 각 주제에 `모든주제` 배지가 최소 1개 (invariant 검증).
- [ ] TopAppBar `+` 탭 → BottomSheet에서 직접 작성 → 제목 + 태그 0개 → 저장 → 즉시 목록에 추가됨. 태그 배지에 `모든주제`만.
- [ ] TopAppBar `↻` 탭 → 기존 주제 삭제 + 새 자동 생성 → 목록 갱신.
- [ ] 키워드 0개 상태에서 `↻` 탭 → "키워드/메모를 먼저 추가해주세요" 안내.
- [ ] API Key 임시 제거 후 키워드 추가 → 키워드 추가는 성공, 자동 경로 스낵바에 "API 키를 먼저 설정해주세요".

## 11단계: 산출물 상태 갱신

- [ ] 본 플랜 README frontmatter `status: review` → 사용자/planner 컨펌 후 `accepted` → implementer 시작 시 `in-progress` → 완료 시 `consumed`.
- [ ] `docs/status.md` 갱신 (완료 시 archive로 이관).
