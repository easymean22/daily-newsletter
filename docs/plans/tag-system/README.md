---
updated: 2026-04-26
status: in-progress
owner: designer
summary: "태그 시스템 1급 편입 (MVP 핸드오프 #3) — 후속 4개 핸드오프의 공통 전제"
consumed_by:
  - "2026-04-19 implementer: 1~2단계 완료 — NotionModels multi_select 3종 + TagNormalizer + JVM 테스트 인프라 + TagNormalizerTest 11/11 PASS"
  - "2026-04-26 planner: 라운드 1 문서 정합 sweep — `자유주제` → `모든주제` rename, Claude 자동 추천·분류 제거, 사용자 수동 태그 생성·부여 플로우 명문화"
  - "2026-04-26 implementer(TASK-003): 3·4·6단계 완료 — NotionSetupService 3개 DB Tags multi_select 시드 + 3개 UI 모델 tags 필드 + Repository 태그 read/write + saveTopic ensureFreeTopicTag + findUnprintedNewsletterByTag + 호출지 emptyList() 임시 처리"
next_action: "7단계(수동 E2E): NotionSetupService.setupDatabases() 재실행 후 3개 DB Tags 컬럼 및 시드 옵션 확인. 5단계(listAvailableTagNames)는 핸드오프 #6 이월."
refs:
  # (refs는 배경 확인용 — 작업 전 필수 읽기 아님)
  - docs/specs/mvp.md
  - docs/decisions/0003-tag-system-data-model.md
---

# 태그 시스템 1급 편입 (MVP 핸드오프 #3)

> **라운드 1 확정 (2026-04-19, 본문 갱신 2026-04-26):** 태그 시스템에 Claude는 관여하지 않는다. 태그 자동 추천·자동 분류·`SuggestedTopic.tags` 같은 자동 부여 메커니즘은 모두 폐기. 태그 생성과 Topic에의 태그 부여는 **전부 사용자 수동**. 또한 시드 태그 이름은 `자유주제` → `모든주제`로 rename — 동일 invariant(모든 Topic에 기본 부여, 사용자 수정·삭제 불가) 유지. 코드 상수 식별자 `FREE_TOPIC_TAG`는 그대로 두고 리터럴 값만 `"모든주제"`로 rename(별도 implementer 항목, status.md).

후속 4개 핸드오프의 **공통 전제**:

- 핸드오프 #2 (주제 생성 3경로) — 자동/수동/직접 작성 모두에서 태그 부여.
- 핸드오프 #4 (lazy 뉴스레터 생성) — 매칭 조건이 "요일 태그 ∩ 뉴스레터 태그 ≥ 1".
- 핸드오프 #6 (요일별 프린트 설정 UI) — 태그 셀렉터가 핵심.
- 핸드오프 #7 (매칭 0건 엣지 정책) — 태그 매칭이 0건일 때의 분기.

본 플랜은 위 4개가 안정적으로 만들어질 수 있도록 **태그 모델·시드·정규화·매칭 쿼리·시그니처 변경**을 한 번에 확정한다.

## 핵심 invariant (사용자 답변 #1 + 라운드 1 반영)

모든 후속 핸드오프가 신뢰할 수 있는 불변식:

1. **모든 Topics 레코드는 항상 `모든주제` 태그를 포함한다.** (이전 이름 `자유주제`는 폐기 — 동일 invariant.)
   - 강제 지점: `TopicRepository.saveTopic(...)` 호출 직전, 정규화 단계에서 `모든주제`가 누락된 태그 리스트면 자동 보충(append).
   - 결과: `모든주제`는 "분류되지 않음을 의미하는 분류"이자 **모든 주제가 최소 1개 태그를 가진다는 안전망**.
2. **`모든주제`는 시스템 시드 태그**로, setup 시 3개 DB(Keywords/Topics/Newsletters) multi_select 옵션 풀에 미리 등록된다.
3. **사용자는 `모든주제`를 의미적으로 삭제할 수 없다** (Notion UI에서 옵션을 지워도 앱이 다음 saveTopic 시 자동 재등록).
4. **다른 태그들은 사용자가 수동으로 만들고 부여한다. 시스템(Claude 포함)은 태그를 자동 추천하지 않는다.** (라운드 1 확정 — 이전 "사용 누적 후 시스템이 추천" 규칙은 폐기.)

이 invariant는 핸드오프 #2·#6이 직접 의존:

- **#2 의존**: 자동/수동/직접 작성 어느 경로든, 태그 입력 결과가 빈 리스트라도 최종 저장 직전에 `모든주제`가 자동 포함되므로 "태그 없는 주제"가 만들어지지 않는다. 자동 생성 경로의 Claude 응답은 태그를 포함하지 않는다 (라운드 1).
- **#6 의존**: 사용자가 요일에 `모든주제`를 포함시키면 매칭이 항상 성립 (Topics·Newsletters 측이 모두 `모든주제` 보유).

**코드 상수 메모**: `TagNormalizer.FREE_TOPIC_TAG` 식별자는 본 플랜에서 변경하지 않는다. 리터럴 값(현재 `"모든주제"` → 라운드 1 결정 `"모든주제"`)의 rename은 status.md의 별도 implementer 항목.

## 바로 써야 하는 의존 인터페이스

```kotlin
// KeywordRepository (본 플랜 3~4단계에서 확장)
suspend fun addKeyword(text: String, type: String, tags: List<String>): Unit
// → 확장 후: suspend fun addKeyword(text: String, type: String, tags: List<String>): KeywordUiItem

// TopicRepository (본 플랜 4단계에서 확장)
suspend fun saveTopic(
    title: String,
    priorityType: String,
    sourceKeywordIds: List<String>,
    tags: List<String>  // 내부에서 ensureFreeTopicTag() 적용 후 Notion 전송
)
suspend fun getTodayTopics(): List<TopicUiItem>   // tags 포함 매핑
suspend fun getAllPastTopicTitles(): List<String>

// NewsletterRepository (본 플랜 4단계에서 확장)
suspend fun saveNewsletter(title: String, htmlContent: String, topicIds: List<String>, pageCount: Int, tags: List<String>)
// 신규 (본 플랜 5단계 — #6 이월)
suspend fun listAvailableTagNames(): List<String>  // Newsletters DB multi_select 옵션 풀 조회

// TagNormalizer (본 플랜 2단계 완료, app/src/main/java/com/dailynewsletter/data/tag/)
fun normalizeTag(input: String): String  // trim + lowercase + 공백 단일화
fun ensureFreeTopicTag(input: List<String>): List<String>  // 시드 태그 자동 보충
val FREE_TOPIC_TAG: String  // 라운드 1 후 리터럴 값 = "모든주제" (식별자명은 status.md 후속 implementer rename)

// NotionSetupService (본 플랜 3단계에서 변경)
suspend fun setupDatabases()  // 3개 DB에 Tags multi_select + 시드 "모든주제" 추가

// UI 모델 (tags 필드 추가)
data class KeywordUiItem(val id: String, val text: String, val type: String, val status: String, val tags: List<String>)
data class TopicUiItem(val id: String, val title: String, val priorityType: String, val status: String, val tags: List<String>)
data class NewsletterUiItem(val id: String, val title: String, val status: String, val pageCount: Int, val tags: List<String>)
```

## 파일 지도

| 파일 | 내용 | 참조 시점 |
|---|---|---|
| [01-backend.md](./01-backend.md) | 백엔드 설계 (모델 대안 A/B/C + Notion API + 매칭 쿼리 + Repository 시그니처) | 1~4단계 작업 시만 |
| [02-ui-note.md](./02-ui-note.md) | UI 영향 요약 + #6 UI에 미치는 결정사항 | 설계 배경 파악 시만 |
| [03-checklist.md](./03-checklist.md) | implementer 체크리스트 8단계 | 항상 참조 |

## 진행률

- [x] 1단계: Notion 모델 확장
- [x] 2단계: 정규화 유틸 + `모든주제` 보충 유틸 (이전 이름 `자유주제`는 폐기 — 식별자 rename은 후속)
- [x] 3단계: NotionSetupService Tags 속성 시드
- [x] 4단계: 3개 Repository에 태그 read/write 추가
- [ ] 5단계: 옵션 풀 헬퍼 `listAvailableTagNames` (#6 핸드오프로 이월 결정 — 2026-04-19)
- [x] 6단계: TopicSelectionService / NewsletterGenerationService 태그 전파 (최소 변경)
- [ ] 7단계: 수동 E2E 한 번
- [ ] 8단계: 산출물 상태 갱신

## 사용자 확인 필요 (미해결 3·5)

~~1. 시드 태그 4개로 시작?~~ → **해결(2026-04-19)**: 시드 1개로 단순화. (이름은 라운드 1 확정으로 `모든주제`. 이전 이름 `자유주제`는 폐기.)
~~2. 정규화 정책?~~ → **해결(2026-04-19)**: trim + lowercase + 공백 단일화 그대로.
3. **태그 1개당 페이지 수 제한**: Notion API는 page당 100까지. MVP에서 별도 상한 둘지? 본 플랜은 무제한 가정.
~~4. 자동 생성 경로(주제)에서 태그 부여 책임?~~ → **해결(2026-04-19 라운드 1)**: Claude는 태그에 관여하지 않는다. 자동·수동·직접 작성 모든 경로에서 default = `모든주제` 단독으로 부여되며, 사용자가 이후 수동으로 추가 태그를 붙이고 뗀다.
5. **기존 Notion DB의 처분**: 본 플랜은 "사용자가 직접 휴지통으로 보내고 재 setup" 전제. 자동 reset 버튼은 핸드오프 #1 또는 #6.
~~6. 요일당 태그 1개?~~ → **해결(2026-04-19)**: 다중 허용. OR/AND는 #6에 위임.

## 후속 영향 (planner 메모)

**후속 영향 없음 — 태그 자동 추천 기능은 라운드 1에서 폐기.** (이전에 "신규 기능 '태그 추천'"으로 spec §7 핸드오프 목록에 추가 예정이라 적혀 있던 항목은 라운드 1 결정으로 무효화.)

부수:
- #2는 invariant 덕분에 "태그 빈 입력 처리"를 별도로 고민하지 않아도 됨. Claude 응답은 태그를 포함하지 않는다.
- #6은 "다중 태그 선택 + OR/AND 정책 결정"이 추가. (라운드 1: MVP는 단일 슬롯만 검증, 다중 슬롯은 공개 배포 이월.)
- #7은 발생 빈도 자체가 낮아짐.
