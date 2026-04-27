---
updated: 2026-04-19
summary: "백엔드 설계 — 미프린트 Newsletter 집합/lazy/생성단위/consumed 4개 축의 대안·trade-off·추천안"
parent: ./README.md
---

# 백엔드 설계

## 대안 (lazy 보충 모델 — 3개 비교) — ADR-0005 §대안 요약

ADR-0005 §대안 A·B에서 기각한 안들:

- **(A) 미프린트 Newsletter 집합 = 별도 Notion DB로 분리** — 기각. Notion DB 2개 간 이동 비용 + 히스토리 추적 끊김 + spec 의도 불일치.
- **(B) 미프린트 Newsletter 집합 = 로컬 Room 캐시** — 기각. "Notion이 진실의 원천" 원칙 위반.

**추천안: Newsletters DB의 `Status = "generated"` 페이지 집합을 미프린트 Newsletter 집합로 본다** (ADR-0005 §결정 1).

## 대안 (lazy 생성 — 3개 비교)

### 옵션 L-A: 프린트 시점 슬롯별 트리거, 2개 동기 생성 (ADR-0005 추천)

- 프린트 시각 도달 → 오늘 요일 슬롯 루프 → 슬롯 매칭 0건이면 그 자리에서 `generateForSlot(tag, pageCount)` 2회 호출 → 1개 즉시 프린트, 1개 `generated`로 비축.
- Claude 호출 2번을 동기로 실행. 프린트 지연은 생성 1개 시간 × 2.

### 옵션 L-B: 프린트 시점 1개 동기 + 2번째 BG enqueue

- 1개만 프린트 직전에 생성, 2번째는 별도 `ShelfRefillWorker`를 enqueue.
- 체감 프린트 지연 절반.
- 상태 머신 복잡 (`generating` 중간 상태 필요). BG 실패 시 미프린트 Newsletter 집합 영영 비어있을 위험.

### 옵션 L-C: 앱 진입 + 주제 추가 + 프린트 시점 3개 트리거로 분산

- 여러 시점에서 미프린트 Newsletter 집합를 채워 프린트 시점엔 lazy가 돌 필요 없음.
- 호출 빈도 예측 불가 (앱 진입에 종속). Claude API 비용 폭주 가능.

### Trade-off (lazy 생성)

| 축 | L-A (프린트 시점 동기 2개) | L-B (1 동기 + 1 BG) | L-C (3개 트리거 분산) |
| --- | --- | --- | --- |
| **spec 의도 부합** ★ | spec §3 "프린트 시점에 미프린트 Newsletter 집합가 비어 있으면 즉석 2개 생성"과 1:1. | 즉석 2개가 아니게 됨 — "비축"의 약속이 BG에 종속되어 불확실. | spec은 트리거 시점을 "프린트 시점"으로만 명시. 확장은 spec 변경 후에. |
| **상태 머신 단순성** | `generated / printed / failed` 3개 유지. | `generating / generated / printed / failed` 4개 — race condition 위험. | `generated / printed / failed` 유지하나 트리거 로직 분산. |
| **Claude 비용 예측성** | 요일당 lazy 발동 횟수 = 슬롯 중 미스 개수. 상한 명확. | 동일. | 앱 진입 빈도 × 슬롯 수 — 상한 없음. |
| **프린트 지연 체감** | 생성 시간 × 2 (수십 초~수 분 가능). | 생성 시간 × 1. | 거의 0 (사전 채워짐). |
| **실패 격리** | 첫 번째 생성 실패 → 슬롯 스킵. 두 번째 실패 → 비축 실패 알림, 당일 프린트 영향 없음. 명료. | 첫 번째 실패 → 슬롯 스킵. 두 번째 실패 → 다음 회차 다시 lazy → 미프린트 Newsletter 집합 영영 비어있을 위험. | 트리거마다 실패 복구 로직이 달라져 경로 가지치기 ↑. |
| **MVP 1일 E2E 성공 기준** | 사용자가 수 분 대기 감수 가능 (1명 테스트). | 빠른 프린트 체감. | 프린트가 빠르지만 주제 추가 시 예기치 않은 Claude 호출 발생 — 사용자 놀람. |
| **되돌리기 비용** | 중간 — 프린트 경로 재설계. | 큼 — 4-state 상태 머신 걷어내기. | 큼 — 여러 트리거 지점에서 호출 지점 제거. |

### 추천안 (lazy 생성): **옵션 L-A**

근거:

1. **spec §3 "프린트 시점에 미프린트 Newsletter 집합가 비어 있으면 즉석 생성으로 보충"과 1:1** — 트리거 시점이 spec에 하나로 명시됨. L-B/L-C는 사용자 의도를 추측해 확장.
2. **MVP 1일 E2E 성공이 기준** — 본인 1명이 수 분 프린트 대기를 감수하면 충분. 체감 속도 최적화는 공개 배포 마일스톤에서 재고.
3. **상태 머신 단순** — Status가 3개로 고정 → 디버깅·검증이 쉬움. race condition 여지 없음.
4. **비용 예측성** — 슬롯 수 × 2가 일 최대 호출. 사용자가 "이 앱은 하루 X번 이상 호출하지 않는다"를 정확히 알 수 있음.
5. **실패 격리 명료** — "프린트는 1차 생성 성공에, 비축은 2차 생성 성공에"로 분리. spec §6 엣지 ("뉴스레터 생성 실패: 알림만, 스킵")의 의도와 합치.

ADR-0005 §결정 2에 박음.

## 대안 (생성 단위 — 2개 비교)

### 옵션 G-A: `generateForSlot(tag: String, pageCount: Int)` — 슬롯 단위

- 슬롯 1개의 태그·장수 조건에 맞춰 주제를 고르고 엮음.
- lazy 2개 생성도 같은 함수 2번 호출.
- 수동 생성 버튼도 이 함수 호출 (사용자가 태그·장수 선택).

### 옵션 G-B: `generateForToday()` — 오늘 전역 단위 (기존 코드 흐름)

- 오늘의 pending 주제 전부 + 설정의 전역 장수로 뉴스레터 1개 생성.
- 슬롯 매칭 로직이 생성과 분리되어 매칭 실패 시 재생성할 방법이 없음.

### Trade-off (생성 단위)

| 축 | G-A (슬롯 단위) | G-B (전역 단위, 기존) |
| --- | --- | --- |
| **spec 의도 부합** ★ | spec §3 슬롯·태그·장수가 생성 단위의 핵심 — 1:1. | spec이 슬롯 × 태그 × 장수 모델인데 전역은 이 모델을 무시. |
| **lazy 보충 자연스러움** | 매칭 미스 슬롯에 정확히 필요한 것만 생성. | 오늘 전체를 매번 다시 만들어야 매칭 확보 — 낭비. |
| **수동 생성 버튼 설계** | 사용자가 태그·장수를 선택 → 함수 1회 호출. 재사용. | 전역이라 수동 버튼과 매칭되지 않음. |
| **consumed 전이 경계 명확** | 슬롯의 selected topics만 consumed. 명확. | 오늘의 모든 주제를 consumed 처리? 과소비 위험. |
| **Claude 프롬프트 설계** | 태그 + 장수 + 후보 주제 목록 3개 입력. 프롬프트가 작고 일관. | "오늘의 주제"를 통째로 던지고 N과 분량을 다 맡김 — 제어 약함. |

### 추천안 (생성 단위): **옵션 G-A**

근거는 모든 축에서 G-A가 우세. G-B는 "슬롯 묶음"을 도입한 spec §3 재정의와 구조적으로 충돌.

## consumed 전이 시점 (2개 비교)

### 옵션 C-A: 뉴스레터 저장 직후 consumed (ADR-0005 추천)

- Newsletters createPage 성공 → 해당 topics 일괄 `consumed` 업데이트.
- 이후 어떤 슬롯이 같은 주제를 다시 뽑을 수 없다.

### 옵션 C-B: 프린트 완료 시점에 consumed

- `Status = "generated"` 상태 뉴스레터의 주제는 아직 pending — 비축분이 프린트되기 전엔 다른 뉴스레터 생성에 재사용 가능.
- 같은 주제가 여러 비축 뉴스레터에 동시에 들어갈 수 있어 소비 일관성 깨짐.

### Trade-off (consumed 전이)

| 축 | C-A (저장 직후) | C-B (프린트 시) |
| --- | --- | --- |
| **spec 의도 부합** ★ | spec §3 "사용된 주제는 소비(consumed) 처리되어 이후 뉴스레터 생성에 다시 뽑히지 않는다" — "사용된"의 의미가 "엮여서 만들어진 시점". | "사용된"을 "프린트된 시점"으로 해석 — 가능하나 비축 단계에서 같은 주제가 중복 사용되는 사이드이펙트. |
| **비축 단계 중복 방지** | 비축분 생성 시 이미 consumed라 재사용 불가 — 자연 방지. | 수동 방지 로직 필요 (예: `generated` 뉴스레터가 참조하는 주제도 후보에서 제외). |
| **프린트 실패 롤백** | consumed는 이미 되어 있음. 뉴스레터는 `failed`. 주제는 "한 번 소비됐다"로 기록 남음. 다음 뉴스레터에 다시 뽑히지 않음 → 다음 슬롯은 lazy. | consumed 미전이 → 다음 슬롯이 같은 주제를 다시 뽑아 생성 가능. "한 번 뽑혔으나 실패한 주제"는 영영 다음 회차에 재소비. |
| **사용자 멘탈 모델** | "이 주제로 뉴스레터를 만들었다 = 소비됐다" 일치. | "프린트까지 됐어야 소비" — Notion에서 `generated` 상태 뉴스레터의 주제들이 여전히 pending으로 보여 혼란. |

### 추천안 (consumed 전이): **옵션 C-A**

근거: spec "사용된 주제"를 "엮여서 만들어진 시점"으로 해석. 비축 단계의 자연 방지 효과가 크다. 프린트 실패 시 "한 번 소비됐으나 못 써서 버린다" 정책은 MVP 범위에서 수용 가능 (spec §6 "생성 실패 시 알림만, 자동 재시도 없음" 정책과 일관). 주제 풀이 일회성 리소스라는 성질은 자동 주제 생성이 수시로 채우는 것으로 상쇄.

**예외: 뉴스레터 `failed` 상태 뉴스레터**는 주제 consumed 전이 없이 실패 처리. 즉 **Newsletters createPage 성공 후에만** consumed 전이. 생성 중(Claude 호출) 실패는 아무 상태 변경 없음.

ADR-0005 §결정 4에 박음.

## 추천안 상세 설계

### 신규 / 변경 컴포넌트 맵

```
[사용자 행동 트리거]                [스케줄 트리거]
   │                                    │
   │  NewsletterScreen                  │  PrintWorker (요일별 프린트 시각)
   │  └ "수동 생성" FAB                 │  └ PrintOrchestrator.runForToday()
   │                                    │       ├ 오늘 요일 슬롯 목록 조회 (SettingsRepository)
   │                                    │       └ 각 슬롯별 처리 루프
   │                                    │          1. findUnprintedNewsletterByTagAndPages()
   │                                    │          2. 없으면 generateForSlot(tag, pages) × 2
   │                                    │             (첫번째 → 프린트, 둘째 → 비축)
   │                                    │          3. Claude 추천으로 1건 선정 (후보 ≥ 2일 때)
   │                                    │          4. printNewsletter(id) → printed
   │                                    │
   ▼                                    ▼
NewsletterViewModel            PrintOrchestrator (신규)
   └ composeManualNewsletter(tag, pages)     ├ 사용: NewsletterGenerationService.generateForSlot
      └ NewsletterGenerationService            ├ 사용: NewsletterRepository.findUnprintedByTag...
         .generateForSlot(tag, pages)          ├ 사용: NewsletterRepository.printNewsletter
            │                                  ├ 사용: ClaudeNewsletterRecommender.pick (후보 복수 시)
            ├ TopicRepository.findPendingTopicsByTag(tag)
            ├ ClaudeApi.createMessage (N 결정 + 본문 작성)
            ├ NewsletterRepository.saveNewsletter(..., tags, topicIds, pageCount)
            └ TopicRepository.markTopicsConsumed(selectedIds)
```

### `NewsletterGenerationService.generateForSlot(tag, pageCount)` 설계

```kotlin
data class GeneratedNewsletter(
    val id: String,              // Notion page id
    val title: String,
    val html: String,
    val selectedTopicIds: List<String>,
    val tags: List<String>       // 선택 주제들의 태그 합집합 (invariant에 의해 모든주제 포함 가능)
)

suspend fun generateForSlot(tag: String, pageCount: Int): GeneratedNewsletter
```

- **주제 후보 조회**: `topicRepository.findPendingTopicsByTag(tag)` — `Status != consumed` AND `Tags contains tag`.
- **빈 후보 처리**: 반환 타입을 nullable로 두지 않고 `IllegalStateException("주제 풀 부족")` throw → Orchestrator가 catch해 알림.
- **Claude 프롬프트**:
  - 후보 주제 목록 (title + id + priorityType) 전달.
  - 장수 = `pageCount` 명시 → 목표 글자 수 `pageCount * 1800` 가이드 (기존 공식).
  - N과 각 주제 분량은 Claude 자동 결정.
  - 응답 스키마:
    ```json
    {
      "selectedTopicIds": ["topicId1", "topicId2"],
      "titleSuffix": "eBPF와 XDP 깊게 보기",
      "html": "<h1>...</h1>..."
    }
    ```
- **응답 파싱 실패**: `IllegalStateException` → Orchestrator가 슬롯 스킵 + 알림.
- **태그 합집합 계산**: `selectedTopicIds`에 해당하는 TopicUiItem들의 tags 합집합 + slot tag. 정규화 키로 dedupe.
- **Newsletters 저장**: `saveNewsletter(title, html, selectedTopicIds, pageCount, tags)`. Status는 기본 `generated`.
- **consumed 전이**: 저장 성공 후 `topicRepository.markTopicsConsumed(selectedTopicIds)` 호출. 전이 실패는 best-effort — 실패 로그만 남기고 뉴스레터 생성 자체는 성공 처리 (spec §6 "생성 실패는 알림만"과 정합).

### `PrintOrchestrator.runForToday()` 설계

```kotlin
@Singleton
class PrintOrchestrator @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val newsletterRepository: NewsletterRepository,
    private val topicRepository: TopicRepository,
    private val newsletterGenerationService: NewsletterGenerationService,
    private val notifier: NotificationHelper  // 신규 또는 기존 PrintWorker 로직 분리
) {
    suspend fun runForToday() {
        val today = LocalDate.now().dayOfWeek  // MONDAY..SUNDAY
        val slots = settingsRepository.getSlotsForDay(today)
          // 신규 API — #6 핸드오프 영역. 본 플랜은 "List<Slot>을 받는다"까지만.

        if (slots.isEmpty()) {
            notifier.notifyInfo("오늘은 설정된 슬롯이 없습니다")
            return
        }

        for (slot in slots) {
            try {
                handleSlot(slot)
            } catch (e: Exception) {
                // 슬롯 간 실패 격리
                notifier.notifyError("슬롯 [${slot.tag}, ${slot.pageCount}장] 처리 실패: ${e.message}")
            }
        }
    }

    private suspend fun handleSlot(slot: Slot) {
        // 1. 미프린트 Newsletter 집합에서 매칭
        val candidates = newsletterRepository.findUnprintedByTagAndPages(slot.tag, slot.pageCount)

        val toPrint: NewsletterUiItem = when {
            candidates.isEmpty() -> {
                // 2. lazy 2개 생성
                val first = newsletterGenerationService.generateForSlot(slot.tag, slot.pageCount)
                // 비축분은 실패해도 프린트는 진행 (실패 격리)
                runCatching {
                    newsletterGenerationService.generateForSlot(slot.tag, slot.pageCount)
                }.onFailure { e ->
                    notifier.notifyWarning("비축분 생성 실패: ${e.message}. 프린트는 계속 진행합니다.")
                }
                first.toUiItem()
            }
            candidates.size == 1 -> candidates.first()
            else -> {
                // 3. Claude 추천으로 1건 선정 — 본 플랜은 "첫 번째" fallback 허용
                claudeNewsletterRecommender.pick(candidates, slot.tag) ?: candidates.first()
            }
        }

        // 4. 프린트
        newsletterRepository.printNewsletter(toPrint.id)
        notifier.notifyInfo("프린트 완료: ${toPrint.title}")
    }
}
```

- **슬롯 모델** (`Slot(tag, pageCount)`): 본 플랜은 **단일 태그 가정**으로 설계. 다중 태그 슬롯은 #6이 도입하며 그 시점에 `List<String> tags` + OR/AND 정책이 추가됨 → `generateForSlot`은 `List<String>`을 받는 오버로드 추가 (본 플랜 scope 밖).
- **`ClaudeNewsletterRecommender`**: 후보 복수 시 Claude가 1건 추천. 본 플랜은 **스텁으로 fallback "첫 번째"** 정책만 포함 — Claude 추천 로직은 후속 (spec §3 "후보 1개 이상이면 Claude가 추천한 1건 출력"이 MVP IN이나, 구현 부담을 낮추기 위해 1단계에서는 "첫 번째"로 두고 사용자 검증 후 Claude 호출 추가). **사용자 확인 필요 #1** 참조.
- **알림**: 기존 `PrintWorker`의 `showNotification` 로직을 `NotificationHelper`로 추출. 알림 채널은 `CHANNEL_PRINT` 유지.

### `NewsletterRepository` 변경

| 메서드 | 변경 |
| --- | --- |
| `getNewsletters()` | 유지. UI 목록용. |
| `saveNewsletter(title, html, topicIds, pageCount)` | `+ tags: List<String>` (ADR-0003 / tag-system). 반환 타입 `Unit → String`으로 변경 (생성된 페이지 id 반환). `generateForSlot`이 반환값 사용. |
| `updateNewsletterStatus(id, status)` | 유지. |
| `printNewsletter(id)` | 로직 변경 — `getNewsletters()` 전체 조회 대신 **단건 조회 API** `getNewsletter(id): NewsletterUiItem` 신설 후 사용. htmlContent 없으면 예외. |
| **신규** `getNewsletter(id)` | `notionApi.getPage(id)` 호출 (신규 엔드포인트 추가 필요 — `@GET("v1/pages/{id}")`). 블록도 함께 조회. |
| **신규** `findUnprintedByTagAndPages(tag, pageCount): List<NewsletterUiItem>` | `Status.equals "generated"` AND `Tags.multi_select.contains tag` AND `Page Count.number.equals pageCount` 필터. 클라이언트 후처리로 태그 정규화 키 재검증. |

**태그 정규화 후처리 주석**: tag-system이 명시한 대로 Notion `contains`는 옵션 이름 정확 매칭이라 대부분 1회 쿼리로 충분하나, 사용자가 Notion UI에서 옵션명을 수정한 경우 대비 클라이언트 정규화 키 비교를 한 번 더.

### `TopicRepository` 변경

| 메서드 | 변경 |
| --- | --- |
| `getTodayTopics()` | 유지. UI 목록용. |
| `saveTopic(..., tags)` | topic-generation-paths에서 이미 확장. 본 플랜 변경 없음. |
| **신규** `findPendingTopicsByTag(tag): List<TopicUiItem>` | `Status.does_not_equal "consumed"` AND `Tags.multi_select.contains tag` 필터. `saveTopic` 결과의 title/priorityType/tags도 함께 매핑. |
| **신규** `markTopicsConsumed(ids: List<String>)` | 각 id에 대해 `updatePage(Status = "consumed")`. 병렬 호출 가능 (N≤5 정도일 것). |
| `deleteTodayTopics()` | topic-generation-paths에서 분해 예정. 본 플랜 변경 없음. |

**Topics Status select 옵션 추가**: `NotionSetupService.setupDatabases()`의 Topics DB properties `Status` select에 `NotionSelectOption("consumed", "default")` 추가 (색상은 중립 — Notion UI에서 사용자가 자유 변경).

### `NewsletterGenerationService` 변경

- **`generateAndSaveNewsletter()` 삭제** (기존 T−30m 호출 흐름 제거).
- **`generateForSlot(tag, pageCount): GeneratedNewsletter` 신설** (§"generateForSlot 설계").
- 내부에서 `topicRepository.findPendingTopicsByTag(tag)` + Claude 호출 + `newsletterRepository.saveNewsletter(..., tags, topicIds, pageCount)` + `topicRepository.markTopicsConsumed(...)`.
- **컴파일 실패 대응**: 기존 `NewsletterWorker`가 `generateAndSaveNewsletter`를 호출 → 워커 삭제와 함께 해결.

### Worker / Scheduler 변경

- **`NewsletterWorker.kt` 삭제**.
- **`WorkScheduler.scheduleNewsletterGeneration()` 삭제** + `scheduleAll()`에서 호출 제거.
- **`WorkScheduler.scheduleAll()` 진입부에 기존 enqueued work cancel**:
  ```
  workManager.cancelUniqueWork("newsletter_generation")
  workManager.cancelUniqueWork("daily_topic_selection")   // topic-generation-paths에서 이미 추가
  ```
  앱 업데이트 시 기존 periodic work 정리. 중복 cancel은 no-op이라 안전.
- **`PrintWorker.kt` 변경**:
  - `inputData.newsletter_id` 분기 제거.
  - `newsletterRepository.printNewsletter(id)` 직접 호출 제거 → `printOrchestrator.runForToday()` 호출.
  - 알림 로직은 `NotificationHelper`로 분리 (Orchestrator가 사용).

## 에러 / 빈 상태 (백엔드 보장 계약)

| 상황 | 정책 |
| --- | --- |
| 슬롯 처리 시 주제 후보 0개 | 슬롯 스킵 + 알림 "슬롯 [태그X, N장] 주제 부족 — 키워드/주제를 추가해주세요". 다음 슬롯 계속. |
| Claude API Key 미설정 | 모든 슬롯 스킵 + 알림 "Claude API 키를 먼저 설정해주세요" + [설정으로]. |
| Claude 호출 실패 (네트워크/rate limit) | 해당 슬롯 스킵 + 알림 "뉴스레터 생성 실패: <메시지>". 다음 슬롯 계속. |
| Claude 응답 파싱 실패 | 동일 — 슬롯 스킵 + 알림 "뉴스레터 응답 형식 오류". |
| 첫 번째 생성 성공, 두 번째(비축) 실패 | 첫 번째 프린트는 진행. 알림 "비축분 생성 실패 — 다음 회차에 다시 시도합니다". |
| 프린트 실패 (네트워크/프린터) | spec §6에 따라 **당일 1회 재시도**. 재시도도 실패 시 슬롯 실패 알림. 다음 슬롯 계속. 재시도 구현은 본 플랜 외 — PrintService 책임 (핸드오프 #7). |
| 후보 복수 시 Claude 추천 호출 실패 | **첫 번째 fallback** (본 플랜 §"PrintOrchestrator 설계" 주석). 로그만 남김. |
| consumed 전이 실패 | 뉴스레터 생성 자체는 성공 처리. 로그만 남김. 다음 프린트에서 같은 주제가 뽑혀 중복 소비 가능성 → MVP 범위에서 수용 (수동 검증 시 본인이 Notion에서 교정). |
| 슬롯 설정 없음 (요일 OFF or 슬롯 0개) | Orchestrator 진입부에서 "오늘은 설정된 슬롯이 없습니다" 알림 후 종료. |
