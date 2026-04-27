---
updated: 2026-04-26
summary: "백엔드 설계 — multi_select 모델 채택 근거 + 데이터 모델 + 매칭 쿼리 + Repository 시그니처 (라운드 1: `자유주제` → `모든주제` rename, Claude 자동 추천 폐기 반영)"
parent: ./README.md
---

# 백엔드 설계

## 대안 (모델 — 3개 비교)

### 옵션 A: Notion `multi_select` 속성 + 사전 시드 + 자유 등록 자동 활용

- 3개 DB(Keywords/Topics/Newsletters)에 동일 이름 `Tags` multi_select 속성 추가.
- 시드: **`모든주제` 단일 태그만**. 색상은 시드 시점에 회색(`gray`) 또는 기본값으로 지정. (이전 이름 `자유주제`는 폐기.)
- 신규 태그는 페이지 생성 시 multi_select 값으로 그대로 보내면 Notion이 schema에 자동 등록.
- 정규화: 표시는 입력 원형(trim만), 비교는 trim+lowercase+공백 단일화.
- 옵션 풀 동기화는 강제하지 않음 — 매칭은 페이지 값(문자열)을 정규화 키로 비교.

### 옵션 B: 별도 `Tags` Notion DB + 3개 DB가 `relation`으로 참조

- 4번째 DB `Tags`를 setup 시 생성. 옵션 = page 1개.
- 페이지 추가 시 relation으로 ID 연결. rename은 자동 전파(ID 안정).
- 자유 입력 시 매번 Tags DB lookup → 없으면 페이지 생성 → ID 받아 relation 페이로드 구성.

### 옵션 C: Notion `select` (단일) — 페이지당 정확히 1개 태그

- 가장 단순. 요일 매칭이 1:1.
- 콘텐츠가 다축 분류일 때 정보 손실. 다중 태그가 invariant("Topics는 항상 모든주제 + α") 자체를 표현 불가.

## Trade-off

| 축 | A (multi_select 자유) | B (Tags DB + relation) | C (select 단일) |
| --- | --- | --- | --- |
| **spec 의도 부합** ★ | 다축 분류 + 자유 입력 + lazy 매칭 자연. spec §3 그대로. | 다축은 가능하나 자유 입력 흐름이 무거워 "직접 작성 경로"의 마찰 증가. | 다축 손실. "`모든주제` + IT" 같은 invariant 필수 조합 불가. **즉시 기각.** |
| **lazy 매칭 비용** ★ | 단일 쿼리 (multi_select `contains`). 1 라운드트립. | Tags DB lookup → ID 변환 → relation 필터. 2~3 라운드트립. lazy 경로의 지연 비용. | — |
| **신규 태그 등록 비용** | Notion이 자동 등록 (페이지 생성 1회). | Tags DB에 페이지 생성 1회 + 부모 DB에 페이지 생성 1회. 라운드트립 2배. | — |
| **데이터 정합성 (rename)** | 옵션명 변경은 schema PATCH 1회 (3개 DB 각각). 페이지 값은 자동 갱신 (Notion 내부 ID 보존). | 자동 전파. 가장 강함. | — |
| **인지 부담** | DB 3개 유지 (현재와 동일). | DB 4개. setup 화면·디버그 부담 +1. | — |
| **MVP 1일 E2E 차단 위험** | 낮음 — 표준 multi_select. | 중간 — 추가 setup, 추가 마이그레이션 분기. | — |
| **공개 배포 시 부담** | 색상 선택지 등 부수 결정 필요하나 일반적. | 4번째 DB까지 사용자가 알아야 함. 온보딩 무거움. | — |

## 추천안: 옵션 A

근거:
1. **spec §3과 1:1 매칭** — "키워드·주제·뉴스레터가 공통으로 가지는 분류 축" → 3개 DB에 같은 multi_select 속성.
2. **#4 (lazy 매칭) 비용 최소** — multi_select `contains` 필터로 단일 쿼리.
3. **#2 (주제 직접 작성 경로)와 잘 맞음** — 사용자가 새 태그를 자유 입력해도 라운드트립 1번에 끝.
4. **#6 (요일별 설정 UI) 셀렉터 출처가 단순** — Newsletters DB의 multi_select 옵션을 그대로 가져오면 됨.
5. 옵션 B의 rename 자동 전파라는 강점은 MVP가 본인 1명·기존 데이터 없음 상황에서 가치 없음.
6. **invariant 강제가 자연스럽다** — multi_select라 N개 태그 가능, 정규화 단계에서 `모든주제`를 append하기만 하면 끝.

세부 결정은 ADR-0003에 기록.

## 데이터 모델 변경 상세

### Notion DB 스키마 (3개 모두에 추가)

```
"Tags": multi_select {
  options: [
    { name: "모든주제", color: "gray" }
  ]
}
```

추가 옵션은 사용자가 키워드/주제/뉴스레터를 만들면서 수동으로 새 태그를 입력할 때 자연스럽게 등록된다. **시스템(Claude 포함)이 옵션을 자동 추천·자동 분류하지 않는다** (라운드 1 확정).

### 정규화 함수 (신규 유틸)

```kotlin
fun normalizeTag(input: String): String =
    input.trim()
         .lowercase()
         .replace(Regex("\\s+"), " ")
```

- 비교는 항상 정규화 키로.
- 저장(=Notion에 보내는 multi_select 옵션 이름)은 사용자 입력의 trim() 결과(원형 보존).
- 신규 태그가 기존 옵션의 정규화 키와 충돌하면 → 앱이 기존 옵션의 표시 형태를 재사용 (옵션 중복 방지). 이를 위해 페이지 생성 직전에 해당 DB의 현재 옵션 목록을 한 번 조회하는 헬퍼가 필요.

### 시드 태그 자동 보충 로직 (TopicRepository에서만 적용)

```kotlin
internal fun ensureFreeTopicTag(input: List<String>): List<String> {
    val normalized = input.map { it.trim() }.filter { it.isNotEmpty() }
    val hasFree = normalized.any { normalizeTag(it) == normalizeTag(FREE_TOPIC_TAG) }
    return if (hasFree) normalized else normalized + FREE_TOPIC_TAG
}
// FREE_TOPIC_TAG: 식별자 그대로, 라운드 1 후 리터럴 값 = "모든주제" (식별자명 rename은 status.md 후속)
```

- 적용 지점: **`TopicRepository.saveTopic(...)` 단 한 곳**. 다른 Repository(Keyword/Newsletter)는 적용하지 않음.
- 단, **NewsletterGenerationService가 주제로부터 뉴스레터의 태그를 합집합으로 계산하는 경로**(핸드오프 #4 영역)에서는 자연스럽게 `모든주제`가 뉴스레터에도 따라 들어오게 됨.

### Notion API 모델 확장

`data/remote/notion/NotionModels.kt` 변경:

- `NotionPropertySchema`에 `multiSelect: NotionMultiSelectSchema?` 필드 추가 (`@SerializedName("multi_select")`).
- `NotionMultiSelectSchema(options: List<NotionSelectOption>)` 신설 (또는 기존 `NotionSelectOptions` 재사용).
- `NotionPropertyValue`에 `multiSelect: List<NotionMultiSelectValue>?` 필드 추가.
- `NotionMultiSelectValue(name: String? = null, id: String? = null)` — name 또는 id 중 하나로 참조.
- `NotionFilter`에 `multiSelect: NotionMultiSelectFilter?` 추가.
- `NotionMultiSelectFilter(contains: String? = null, doesNotContain: String? = null)` — `contains` 연산자 사용.

### 매칭 쿼리 (Newsletters DB)

```
filter = and(
  property="Status", select.equals="generated",   // 미프린트
  property="Tags",   multi_select.contains=<요일 태그 표시명>
)
```

- 정규화 차이를 흡수하기 위해 클라이언트가 후처리 필터링도 한 번 더 한다 (Notion `contains`는 옵션 이름 정확 매칭 — 옵션 풀 안에서는 충돌이 없을 것이지만 사용자 변형 케이스 대비).

#### 매칭 함의 (사용자 답변 #1 반영)

invariant "Topics는 항상 `모든주제` 포함" + (NewsletterGenerationService가 주제 합집합으로 뉴스레터 태그 계산) 가정 하에:

- **사용자가 요일별 설정에서 태그를 비워두거나 `모든주제`만 선택하면, 매칭은 항상 성립한다.** 그날 생성된/누적된 모든 미프린트 뉴스레터가 후보.
- 결과적으로 **핸드오프 #7 (매칭 0건 엣지)의 발생 조건이 크게 좁아진다.**
  - 0건이 발생하려면 사용자가 "구체 태그 X 만"을 선택했고 + 그 태그를 가진 미프린트 뉴스레터가 0건이고 + 자동 lazy 생성도 실패해야 함.
  - "안전망"으로 `모든주제`를 항상 후보에 둘지(= 0건 시 fallback)는 #7의 결정 — 본 플랜은 강제하지 않음.

### Repository 시그니처 변경

| 메서드 | 변경 |
| --- | --- |
| `KeywordRepository.addKeyword(text, type)` | `+ tags: List<String>` |
| `KeywordRepository.refreshKeywords()` | UI 매핑에 tags 포함 |
| `TopicRepository.saveTopic(title, priorityType, sourceKeywordIds)` | `+ tags: List<String>` — 내부에서 `ensureFreeTopicTag()` 적용 후 Notion에 전송 |
| `TopicRepository.getTodayTopics()` / `getAllPastTopicTitles()` | tags 포함하도록 매핑 |
| `NewsletterRepository.saveNewsletter(title, htmlContent, topicIds, pageCount)` | `+ tags: List<String>` |
| `NewsletterRepository` 신설 | `findUnprintedNewsletterByTag(tagName: String): NewsletterUiItem?` — 핸드오프 #4의 lazy 매칭 진입점 |

UI 모델(`KeywordUiItem`, `TopicUiItem`, `NewsletterUiItem`)에도 `tags: List<String>` 추가.

### `NotionSetupService.setupDatabases()` 변경

- 3개 DB의 properties 맵에 `Tags` multi_select 추가 (시드 `모든주제` 1개).
- 기존 "이미 keywordsDbId가 있으면 skip" 분기 유지.
- 본 ADR 적용 후 처음 setup하는 사용자는 정상. 기존 (개인 테스트로 만든) DB가 있다면 reset 경로 필요 — 본 플랜 scope 밖.

## 에러 / 빈 상태 (백엔드가 보장해야 할 계약)

- **Notion API 호출 실패**: 기존 패턴(throw IllegalStateException) 유지. UI가 잡아 사용자에게 한국어 메시지로 알림.
- **태그 입력이 빈 리스트 (Topics)**: invariant에 의해 `모든주제`가 자동 부착되어 저장된다. 사용자에게 별도 알림 없음.
- **태그 입력이 빈 리스트 (Keywords/Newsletters 직접 호출)**: 허용. multi_select 빈 배열을 보낸다. 단 lazy 매칭 시 빈 태그 뉴스레터는 어떤 요일 태그와도 매칭되지 않는다 (의도된 동작).
- **신규 태그 입력에 쉼표 포함**: 입력 검증 단계에서 거부 (Notion multi_select 제약).
- **정규화 충돌 (기존 옵션의 다른 표기 입력)**: 앱이 조용히 기존 표시명으로 매핑. 사용자에게 별도 알림 없음 (마찰 최소화).
