---
updated: 2026-04-26
status: accepted
owner: designer
summary: "주제 생성 3경로 (자동/수동/직접) — 옵션 C ClaudeTopicSuggester + ViewModel orchestration. Claude는 태그에 관여하지 않음 (라운드 1). Q1~Q5 모두 해소."
consumed_by:
  - "2026-04-26 planner: 라운드 1 문서 정합 sweep — Claude의 태그 제안(`SuggestedTopic.tags`, `availableTagPool`) 제거, 모든 경로의 default 태그 = `모든주제` 단독으로 명문화"
  - "2026-04-26 사용자 응답: Q1~Q4 권장안 일괄 수용 → accepted"
next_action: "implementer 진입. e2e-rehearsal Scope A를 위해 단계 1·2·3·4·5·6·8·9·11(7번 TopicsScreen UI는 Scope A 비범위로 보류)."
refs:
  # (refs는 배경 확인용 — 작업 전 필수 읽기 아님)
  - docs/specs/mvp.md
  - docs/decisions/0003-tag-system-data-model.md
  - docs/plans/tag-system/README.md
---

# 주제 생성 3경로 (MVP 핸드오프 #2)

> **라운드 1 확정 (2026-04-19, 본문 갱신 2026-04-26):** Claude는 태그 시스템에 관여하지 않는다. 자동·수동·직접 작성 모든 경로에서 생성된 Topic은 default 태그 = `모든주제` 단독으로 저장되며, 사용자가 이후 수동으로 추가 태그를 붙이고 뗀다. `ClaudeTopicSuggester` 응답에는 `tags` 필드가 없으며, `availableTagPool` 파라미터도 폐기.

세 경로의 의도·트리거·결과물:

| 경로 | 트리거 | Claude 호출 여부 | 결과 |
| --- | --- | --- | --- |
| **(a) 자동 생성** | 키워드 등록 직후 | O — 키워드 풀 + 과거 주제 → 주제(들) 제안 (제목 + sourceKeywordIds만; **태그는 제안하지 않음**) | Topics DB에 1~N개 주제, default 태그 `모든주제` 단독 (Claude가 N 결정) |
| **(b) 수동 버튼** | 사용자가 주제 화면에서 "주제 생성" 명시적 탭 | O — (a)와 동일 호출, 트리거만 다름 | Topics DB에 1~N개 주제, default 태그 `모든주제` 단독 |
| **(c) 직접 작성** | 사용자가 주제 입력 폼에 제목 + (선택적으로 태그) 입력 | X | Topics DB에 1개 주제. 사용자가 태그를 입력하면 그대로, 비워두면 `모든주제` 단독 |

세 경로 모두 **invariant("Topics는 항상 `모든주제` 태그 포함")** 를 통해 빈 태그 주제가 만들어지지 않음 — `TopicRepository.saveTopic()` 한 곳에서 강제 (전제 플랜: tag-system).

## 바로 써야 하는 의존 인터페이스

```kotlin
// ClaudeTopicSuggester (본 플랜 1단계에서 신설, service/ClaudeTopicSuggester.kt)
// 라운드 1: tags / availableTagPool 제거. Claude는 제목·우선순위·sourceKeywordIds만 제안.
data class SuggestedTopic(
    val title: String,
    val priorityType: String,         // "direct" | "prerequisite" | "peripheral"
    val sourceKeywordIds: List<String>,
    val reason: String
)

@Singleton
class ClaudeTopicSuggester @Inject constructor(
    private val claudeApi: ClaudeApi,
    private val settingsRepository: SettingsRepository
) {
    suspend fun suggest(
        pendingKeywords: List<KeywordUiItem>,
        pastTopicTitles: List<String>
    ): List<SuggestedTopic>
}

// TopicRepository (tag-system 플랜 4단계에서 확장, 본 플랜 2단계에서 사이클 제거)
suspend fun saveTopic(
    title: String,
    priorityType: String,
    sourceKeywordIds: List<String>,
    tags: List<String>  // 자동/수동 경로는 emptyList() 전달 → ensureFreeTopicTag()가 `모든주제` 보충
)
suspend fun deleteTodayTopics()                   // 신설 (본 플랜 3단계, 기존 getTodayTopics+forEach delete 묶음)
suspend fun getAllPastTopicTitles(): List<String>

// KeywordRepository (tag-system 4단계 + 본 플랜 4단계 확장)
suspend fun addKeyword(text: String, type: String, tags: List<String>): KeywordUiItem
// 반환 타입 Unit → KeywordUiItem (자동 경로가 sourceKeywordIds 구성에 id 필요)
suspend fun getPendingKeywords(): List<KeywordUiItem>
```

## 핵심 결정 (옵션 C 채택, 라운드 1 반영)

- **`ClaudeTopicSuggester` 신설** (순수 Claude 호출 + 응답 파싱, side-effect 없음). 응답에 태그 없음.
- **저장 책임은 ViewModel** — `KeywordViewModel`이 (a)를, `TopicsViewModel`이 (b)·(c)를 orchestration.
- **자동·수동 경로의 default 태그 = `모든주제` 단독**. ViewModel이 `saveTopic(..., tags = emptyList())`로 전달하면 invariant가 보충.
- **순환 의존성 해소**: `TopicRepository`에서 `TopicSelectionService` 주입 제거. `TopicSelectionService` 파일 자체를 삭제.
- **`DailyTopicWorker` 제거** (spec §3 "사용자 행동 트리거" 재정의와 충돌).
- **`KeywordRepository.addKeyword` 반환 타입을 `KeywordUiItem`으로 확장** (자동 경로 orchestration을 위해 생성된 id 필요).

## 파일 지도

| 파일 | 내용 | 참조 시점 |
|---|---|---|
| [01-background.md](./01-background.md) | 배경 / 기존 코드 이슈 2건 | 설계 배경 파악 시만 |
| [02-backend.md](./02-backend.md) | 백엔드 설계 (대안 A/B/C + 추천안 상세 + Claude 프롬프트 + 자동 경로 흐름) — sub-file은 라운드 1 문구 정합 후속 정리 대상. 결정 사항은 본 README의 라운드 1 callout이 우선. | 1~5단계 작업 시만 |
| [03-ui.md](./03-ui.md) | UI 설계 (옵션 U-B 경로별 분산) + 와이어프레임 | 6~7단계 작업 시만 |
| [04-checklist.md](./04-checklist.md) | implementer 체크리스트 11단계 | 항상 참조 |

## 진행률

- [x] 1단계: ClaudeTopicSuggester 신설 + 프롬프트 변경 (응답 스키마에 태그 없음)
- [x] 2단계: 사이클 해소 — TopicRepository에서 TopicSelectionService 제거
- [x] 3단계: TopicsViewModel orchestration (최소 — 정합 유지 stub)
- [x] 4단계: KeywordRepository.addKeyword 시그니처 + 반환 타입 확장
- [x] 5단계: KeywordViewModel 자동 경로 orchestration
- [x] 6단계: KeywordScreen UI — 태그 입력 + 자동 경로 스낵바
- [ ] 7단계: TopicsScreen UI — 수동 버튼 + 직접 작성 진입점 (**Scope A 비범위** — 자동 경로만 검증)
- [x] 8단계: DailyTopicWorker + 스케줄 제거
- [x] 9단계: DI 점검
- [ ] 10단계: 수동 E2E 검증
- [ ] 11단계: 산출물 상태 갱신

## 사용자 확인 5개 — 모두 해소 (2026-04-26)

~~1. 자동 경로의 호출 빈도 / 토글~~ → **(2026-04-26 사용자 일괄 수용)** 키워드 추가 시 즉시 Claude 호출. debounce/토글은 #6에서 추후 도입 가능.
~~2. 자동 경로가 키워드 상태를 자동 resolved로 전환할지~~ → **(2026-04-26 일괄 수용)** 건드리지 않음. 사용자가 수동 토글.
~~3. 수동 "주제 생성" 버튼 pending 0개 처리~~ → **(2026-04-26 일괄 수용)** 버튼 enabled + 누르면 안내 다이얼로그.
~~4. 옵션 풀 자동완성 UI~~ → **(2026-04-26 일괄 수용)** 본 플랜 scope 제외. #6에서 자연 활용.
~~5. 자동 생성 경로의 태그 부여 책임~~ → **해결(2026-04-19 라운드 1)** Claude는 태그에 관여하지 않는다. default = `모든주제` 단독.

## 관련 결정

- ADR-0003 v3 (태그 모델 — 본 플랜의 전제, 라운드 1 supersedes 반영)
- 본 플랜은 **신규 ADR을 만들지 않는다**. 사이클 해소·워커 제거·Claude 태그 제거는 spec §3 라운드 1을 따른 결과이며, 별도 ADR로 박을 만한 되돌리기 어려운 결정이 아님.
