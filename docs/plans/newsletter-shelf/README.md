---
updated: 2026-04-26
status: accepted (Scope A subset)
owner: designer
summary: "뉴스레터 lazy 보충 모델 (프린트 시점 2개 생성) (MVP 핸드오프 #4 + #5 통합) — 라운드 1 어휘 정합 반영. e2e-rehearsal Scope A에 필요한 부분집합(1·2·3·4·6·9·10) accepted, 5·7·8(자동 lazy/IPP)은 이터레이션 B/C 대기."
consumed_by:
  - "2026-04-26 planner: 라운드 1 문서 정합 sweep — '진열대/shelf/풀' 어휘 폐기, '미프린트 Newsletter 집합'으로 대체. 폴더명/ADR 파일명은 sweep scope 밖."
  - "2026-04-26 사용자 응답: Q4=B(프롬프트 가이드 + 응답 검증), Q6=U-A(NewsletterScreen 확장) 채택. Q1·Q2·Q3·Q5는 이미 해결되었거나 이터레이션 B/C/release-hardening으로 이월."
next_action: "implementer 진입(Scope A 범위만): 1·2·3·4·6·9·10단계. 5·7·8단계는 이터레이션 B/C에 대기."
refs:
  # (refs는 배경 확인용 — 작업 전 필수 읽기 아님)
  - docs/specs/mvp.md
  - docs/decisions/0005-newsletter-shelf-lazy-generation.md
  - docs/plans/tag-system/README.md
  - docs/plans/topic-generation-paths/README.md
---

# 뉴스레터 lazy 보충 모델 + 다중 주제 엮기

> **라운드 1 확정 (2026-04-19, 본문 갱신 2026-04-26):** "진열대 / shelf / 풀"은 제품 개념이 아니다. Newsletter 레코드 = 생성된 아티클 1건이며, 아직 프린트되지 않은 레코드의 집합에는 별도 공간명을 부여하지 않는다. 본 플랜의 sub-files(01–05)에 남아 있는 옛 어휘는 후속 정리 대상이며, 결정 사항은 본 README의 라운드 1 callout이 우선한다. 폴더명 `newsletter-shelf/`와 ADR 파일명 `0005-newsletter-shelf-lazy-generation.md`는 본 sweep scope 밖이라 그대로 둔다.

MVP 핸드오프 **#4(lazy 보충 모델)** 와 **#5(다중 주제 엮기 N=auto)** 를 한 플랜에 통합. 두 핸드오프가 같은 생성 함수 `generateForSlot(tag, pageCount)`의 설계 결정을 공유하므로 분리 시 trade-off를 두 번 정리해야 해서 통합.

## 바로 써야 하는 의존 인터페이스

```kotlin
// SettingsRepository (weekday-print-slots 플랜에서 확정 — 본 플랜은 stub으로 시작)
suspend fun getSlotsForDay(day: DayOfWeek): List<Slot>
// stub: Slot("모든주제", getNewsletterPages()) 단일 슬롯 반환 (Q2 추천 A)

// NewsletterRepository (tag-system + 본 플랜 2단계에서 확장)
suspend fun saveNewsletter(
    title: String, htmlContent: String,
    topicIds: List<String>, pageCount: Int, tags: List<String>
): String  // 반환 타입 Unit → String (생성된 Notion page id)
suspend fun findUnprintedByTagAndPages(tag: String, pageCount: Int): List<NewsletterUiItem>
suspend fun getNewsletter(id: String): NewsletterUiItem  // 신규 단건 조회
suspend fun updateNewsletterStatus(id: String, status: String)
suspend fun printNewsletter(id: String)

// TopicRepository (tag-system + topic-generation-paths + 본 플랜 3단계에서 확장)
suspend fun findPendingTopicsByTag(tag: String): List<TopicUiItem>  // 신규
suspend fun markTopicsConsumed(ids: List<String>)                   // 신규

// PrintOrchestrator (본 플랜 5단계에서 신설, service/PrintOrchestrator.kt)
@Singleton
class PrintOrchestrator @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val newsletterRepository: NewsletterRepository,
    private val topicRepository: TopicRepository,
    private val newsletterGenerationService: NewsletterGenerationService,
    private val notifier: NotificationHelper
) {
    suspend fun runForToday()  // weekday-print-slots 플랜에서 runForDay(DayOfWeek)로 교체
}

// NewsletterGenerationService (본 플랜 4단계에서 재작성)
data class GeneratedNewsletter(
    val id: String,
    val title: String,
    val html: String,
    val selectedTopicIds: List<String>,
    val tags: List<String>
)
suspend fun generateForSlot(tag: String, pageCount: Int): GeneratedNewsletter

// Slot (본 플랜 1단계에서 신설)
data class Slot(val tag: String, val pageCount: Int)
```

## 전제

- tag-system (in-progress) — 태그 모델, Newsletters DB `Tags` multi_select
- topic-generation-paths (review) — `DailyTopicWorker` 제거, `TopicSelectionService` 제거, `TopicRepository.saveTopic(..., tags)` 확장, `deleteTodayTopics` 분해

## 핵심 결정 (ADR-0005에 박음)

1. **미프린트 Newsletter 집합** = Newsletters DB의 `Status = "generated"` 페이지 집합 (별도 엔티티 X).
2. **lazy 2개 생성** = 프린트 시점 슬롯별 트리거, **동기 2개** (1 프린트 + 1 비축).
3. **생성 단위** = `generateForSlot(tag, pageCount)` **슬롯별**.
4. **consumed 전이** = 뉴스레터 저장 직후 (프린트 완료 X).
5. **NewsletterWorker 제거**, **PrintWorker**는 `PrintOrchestrator.runForToday()` 호출로 전환.

## 파일 지도

| 파일 | 내용 | 참조 시점 |
|---|---|---|
| [01-background.md](./01-background.md) | 배경 / 요구사항 / 기존 코드 이슈 4건 | 설계 배경 파악 시만 |
| [02-backend.md](./02-backend.md) | 백엔드 설계 (대안·trade-off·추천안 상세) | 1~8단계 작업 시만 |
| [03-ui.md](./03-ui.md) | UI 영향 (U-A 기존 화면 확장) | 9~10단계 작업 시만 |
| [04-infra.md](./04-infra.md) | 크로스커팅 / 인프라 (Worker 처분 요약) | 7~8단계 작업 시만 |
| [05-checklist.md](./05-checklist.md) | implementer 체크리스트 12단계 | 항상 참조 |

## 진행률

- [x] 1단계: Notion 모델 확장 (getPage + Topics.Status consumed) — TASK-20260426-005
- [x] 2단계: NewsletterRepository 신규 메서드 — TASK-20260426-005
- [x] 3단계: TopicRepository 신규 메서드 — TASK-20260426-005
- [x] 4단계: generateForSlot 재작성 — TASK-20260426-005
- [ ] 5단계: PrintOrchestrator 신설 (이터레이션 B)
- [x] 6단계: NotificationHelper 분리 — TASK-20260426-005
- [ ] 7단계: PrintWorker 변경 (이터레이션 B/C)
- [x] 8단계: NewsletterWorker 파일 삭제 + WorkScheduler scheduleNewsletterGeneration 제거 — TASK-20260426-005 (파일 삭제 + 스케줄 제거 완료)
- [x] 9단계: NewsletterViewModel 수동 생성 orchestration — TASK-20260426-005
- [x] 10단계: NewsletterScreen UI 변경 — TASK-20260426-005
- [ ] 11단계: 수동 E2E 검증 (사용자 검증 영역)
- [x] 12단계: 산출물 상태 갱신 — TASK-20260426-005

## 사용자 확인 6개 — 처리 결과 (2026-04-26)

| Q | 영역 | 처리 |
|---|---|---|
| Q1 후보 복수 Claude 추천 | 단계 5 (PrintOrchestrator) | **이월** — Scope A는 자동 lazy 미사용. 이터레이션 B/C 또는 #7 IPP plan에서. |
| Q2 `getSlotsForDay` stub | 단계 5 | **이월** — Scope A는 스케줄러 미사용. weekday-print-slots에서 처리. |
| Q3 Claude API 비용 안내 | release-hardening | **이월** — release-hardening plan으로. |
| **Q4 N(주제 수) 상한** | 단계 4 | **(2026-04-26 사용자 채택) B 권장안** — 프롬프트에 "N ≤ pageCount × 2" 가이드 추가 + 응답 초과 시 가벼운 경고 로그. 코드 강제(C)는 html과 ID 불일치 위험으로 기각. |
| Q5 consumed 전이 시점 | 단계 4 | **이미 해결** — ADR-0005 §결정 4 "C-A 저장 직후" |
| **Q6 수동 생성 진입 UI** | 단계 10 | **(2026-04-26 사용자 채택) U-A 권장안** — 기존 `NewsletterScreen`에 TopAppBar `+` 액션(BottomSheet) + 카드 상태 뱃지. 별도 화면/탭 분리는 채택하지 않음. |

아래 결정 근거 섹션은 배경 정보 — Scope A 진입에는 위 표만으로 충분.

## 결정 근거 (배경 필요 시만)

> 📂 결정 근거 (배경 필요 시만 참고 — 코딩에 불필요)

> 각 질문에 A/B/C로 답해주세요. 추천안으로 가도 된다면 "추천대로"만으로 충분합니다.

### Q1. 후보 복수 시 Claude 추천 호출을 언제 구현할까

미프린트 Newsletter 집합에 같은 슬롯 조건(tag+pageCount) 매칭이 2건 이상일 때 1건을 고르는 로직. spec §3은 "Claude가 1건 추천"이 MVP IN으로 명시되어 있으나, lazy 2개 생성이 정착되기 전엔 후보 복수 상황 자체가 드물다 (첫 실행일은 항상 후보 0 → 2개 생성 → 1 프린트/1 비축, 두 번째 실행일부터 후보 1).

- **A. 본 플랜에서 `ClaudeNewsletterRecommender.pick()` 실제 Claude 호출까지 함께 구현** — `PrintOrchestrator.handleSlot()`의 `candidates.size >= 2` 분기에서 Claude API 호출 (후보 title/pageCount/tags + slot 조건을 프롬프트로). 04-infra.md의 Claude 호출 상한이 "슬롯당 최대 3회"로 확정. 장점: spec §3을 1단계에서 완성. 단점: `ClaudeNewsletterRecommender` 프롬프트 설계 + 응답 파싱 + 실패 fallback 경로 3개가 5단계에 추가되어 체크리스트 항목 수 ↑. MVP 1일 E2E에서 후보 복수 상황이 발생하지 않으면 Claude 호출이 한 번도 안 돌아 검증 공백.
- **B. 본 플랜은 "후보의 첫 번째" fallback stub만, Claude 호출은 핸드오프 #7로 분리** — 05-checklist.md 5단계 현재 상태. `ClaudeNewsletterRecommender`는 `@Singleton` 껍질 + `pick(candidates, tag): NewsletterUiItem? = candidates.firstOrNull()`만. 장점: 1단계 검증 범위 축소, lazy 2개 생성 + consumed 전이 검증에 집중. 단점: spec §3 "Claude 추천" 항목은 미충족 상태로 1단계 종료. 04-infra.md의 "슬롯당 최대 3회" 안내를 "슬롯당 최대 2회 (현재)"로 임시 기재해야 함.
- **추천: B**. 1일 E2E에서 후보 복수는 2일차부터 발생하므로 1단계 acceptance 경로(05-checklist.md 11단계)에 추천 호출이 등장하지 않는다. spec 완성도는 #7에서 회수 가능하며, 분리 시 lazy/consumed 검증이 오염되지 않는다.

### Q2. 슬롯 설정 UI(#6)가 아직 없는 동안 `getSlotsForDay()` stub을 어떻게 채울까

`PrintOrchestrator.runForToday()`는 `settingsRepository.getSlotsForDay(today): List<Slot>`을 호출하는데, 이 API의 실제 구현(요일별 슬롯 묶음 설정 UI)은 핸드오프 #6. 본 플랜에서는 stub으로 버텨야 함.

- **A. `Slot("모든주제", settingsRepository.getNewsletterPages())` 단일 슬롯 하드코드** — 05-checklist.md 5단계 현재 문구. 기존 `KEY_NEWSLETTER_PAGES` 값 + 태그 문자열 "모든주제" 하나로 Slot 1개만 반환. 장점: 기존 `SettingsRepository.getNewsletterPages()` 재사용으로 0 코드 변경. lazy 2개/consumed 검증에 필요한 최소 슬롯 개수(1)를 만족. 단점: "#IT" 등 다른 태그 기반 슬롯 동작 검증 불가. stub 함수명이 `getSlotsForDay`라 #6이 들어올 때 시그니처만 맞고 내부는 전면 교체.
- **B. `Settings`에 임시 JSON 필드 `KEY_TEMP_SLOTS_JSON` 추가, 직접 편집으로 여러 슬롯 설정** — `SettingsEntity`의 k/v에 `[{"tag":"모든주제","pages":2},{"tag":"IT","pages":1}]` 같은 JSON 문자열. `getSlotsForDay`가 이를 파싱. 장점: 다중 슬롯 E2E 테스트 가능. 단점: #6이 들어오면서 이 필드가 사라지므로 Room 마이그레이션 1회 낭비. 편집 UI 없으면 adb로 SharedPreferences 주입이나 개발자용 debug 화면 필요 — MVP scope 밖.
- **C. `List<Slot>` 대신 본 플랜에선 `getSlotsForDay()` 도입을 미루고 `runForToday()`가 직접 `getNewsletterPages()`를 읽어 단일 슬롯 루프** — stub 함수 없이 동작. 장점: #6 들어올 때까지 불필요한 시그니처 없음. 단점: #6에서 `PrintOrchestrator.runForToday()` 내부 구조를 다시 뜯어야 함 — 본 플랜의 "슬롯 루프" 구조 자체가 스텁에 맞춰 왜곡됨.
- **추천: A**. #6이 같은 시그니처(`getSlotsForDay: List<Slot>`)로 진입할 것이므로 `PrintOrchestrator.runForToday()`의 슬롯 루프 구조를 지금부터 올바르게 박아두는 게 맞다. 다중 슬롯 검증 공백은 #6에서 바로 해소.

### Q3. Claude API 호출 비용 안내 문구/화면을 언제 어디에 넣을까

04-infra.md 배포 전 체크리스트에 "슬롯당 최대 3회 (Q1이 B면 최대 2회), 하루 최대 3N회"가 기재됨. 현재 앱은 개인용이나 공개 배포 예정 — Claude API Key는 사용자가 직접 제공하고 비용도 사용자 부담.

- **A. 본 플랜에서 설정 화면(Claude API Key 입력 근처)에 정보 텍스트 추가** — "이 앱은 프린트 시각마다 슬롯당 최대 2~3회 Claude API를 호출합니다. 하루 슬롯 N개 × M회 호출 예상." 같은 한국어 안내. `SettingsScreen`에 Text 컴포저블 1개. 장점: 사용자가 Key 입력 시점에 비용 구조를 인지. 단점: 숫자가 #6의 슬롯 묶음 도입 시 바뀌므로 문구 1회 수정 필요. UI-라벨 영역이라 본 플랜 UI 섹션(03-ui.md)에 한 줄 추가.
- **B. `release-hardening` 트랙으로 위임, 본 플랜은 04-infra.md 메모로만 존치** — 현재 상태. 공개 배포 시점에 별도 플랜에서 설정 화면 안내 + 비용 추정 문서 + README 안내까지 패키지로 처리. 장점: 개인용 단계(본인 1명)에선 안내 불필요. MVP 1일 E2E scope 유지. 단점: 본 플랜이 실제로는 `HttpLoggingInterceptor.Level.BODY` 같은 보안 이슈도 함께 04-infra.md에 적재해둔 상태라 "release-hardening이 실체로 언제 시작되느냐"가 비어있음 — 누군가 플랜 하나를 따로 열어야 함.
- **추천: B**. spec §배포 체크에 "공개 배포 전 필수" 항목으로 이관하는 게 본 플랜의 lazy 검증 scope를 해치지 않는다. 단, 이 응답을 받은 뒤 planner에게 "release-hardening 플랜을 열어 04-infra.md의 메모들(API 키 로깅, Claude 비용 안내, Notion 토큰 로깅)을 모아야 한다"를 숨은 결정(아래)으로 넘긴다.

### Q4. 단일 뉴스레터의 최대 주제 수 N에 강제 상한을 둘까

`NewsletterGenerationService.generateForSlot(tag, pageCount)`는 Claude에게 "N과 각 주제 분량은 자유롭게 결정"으로 위임 (02-backend.md §generateForSlot 설계). 상한 없음 = 후보 주제가 10개면 Claude가 10개 다 엮을 수도 있음.

- **A. 프롬프트 가이드만, 코드 상한 없음 (현재 플랜 가정)** — "장수 = pageCount, 목표 글자수 = pageCount × 1800, N은 자유". 장점: Claude가 분량에 맞춰 자연스럽게 N을 조절. 코드 단순. 단점: 1장 뉴스레터에 주제 6개가 들어가 각각 2문장씩 껍데기로 엮이는 실패 모드 가능. consumed 전이(C-A, 저장 직후)와 결합하면 "한 슬롯이 오늘 pending 주제 전부를 빨아들여 다른 슬롯이 주제 부족"되는 리소스 고갈 위험.
- **B. 프롬프트에 명시적 상한 추가: "N ≤ pageCount × 2" (1장 2개, 2장 4개 등)** — 02-backend.md §generateForSlot Claude 프롬프트에 한 줄 추가. 장점: 겉핥기 뉴스레터 방지 + 주제 풀 과소비 방지. 단점: Claude가 상한을 엄격히 따르지 않을 수 있음 — 응답 파싱 후 `selectedTopicIds.size > pageCount * 2`면 앞에서 자르는 코드 방어 필요 (1줄).
- **C. 코드 레벨 강제 상한: `selectedTopicIds.take(pageCount * 2)` 후처리** — `NewsletterGenerationService.generateForSlot`에서 Claude 응답 파싱 후 강제로 자름. 잘린 주제는 consumed 전이 제외. 장점: 결정적. 단점: Claude가 "5개를 엮어 1편의 글"로 구성한 경우 뒤 3개를 잘라내면 html 본문은 5개 기준인데 selectedTopicIds만 2개 → 저장 후 불일치.
- **추천: B**. 프롬프트 가이드 + (응답이 상한 초과일 때만) 가벼운 경고 로그로 충분. C는 html-id 불일치 리스크가 커 받아들이기 어렵고, A는 주제 풀 고갈 실패 모드 하나를 열어둔다.

### Q5. `consumed` 전이를 뉴스레터 저장 직후에 할지, 프린트 완료 시점에 할지

ADR-0005 §결정 4는 "저장 직후"(C-A)로 박았고, 02-backend.md §consumed 전이 시점에서 C-A/C-B trade-off 표로 근거를 제시. 사용자 최종 확인이 필요.

- **A. 저장 직후 consumed (ADR-0005 C-A, 추천)** — `NewsletterGenerationService.generateForSlot`이 `newsletterRepository.saveNewsletter(...)` 성공 직후 `topicRepository.markTopicsConsumed(selectedTopicIds)` 호출. 비축분(`Status = "generated"`)의 주제도 이미 consumed. 장점: 비축 단계 중복 방지 자연스러움 — 2번째 lazy 호출에서 같은 주제가 뽑힐 수 없음. "사용된 = 엮여서 만들어진" 멘탈 모델. 단점: 프린트 실패 → 뉴스레터 `failed`로 남고 주제는 consumed 그대로. "한 번 소비됐으나 못 써서 버림" 정책 수용 필요.
- **B. 프린트 완료 시점 consumed (C-B)** — `NewsletterRepository.printNewsletter(id)` 성공 직후 해당 뉴스레터의 `Topics relation`을 순회해 각 topic을 `markTopicsConsumed`. 비축분(`generated`) 상태의 주제는 pending 유지 → 2번째 lazy 호출 + 다음날 lazy 호출에서 같은 주제 재사용 가능. 장점: "사용된 = 프린트된" 멘탈 모델. 실패 뉴스레터의 주제가 버려지지 않음. 단점: 같은 주제가 비축분 2개에 동시 포함될 수 있어, `generateForSlot` 내부에서 "현재 `generated` 상태 뉴스레터들이 참조하는 topicIds"도 제외 후보로 추가 조회 필요 — `findPendingTopicsByTag`가 Notion 쿼리 1개 → 2개(+`generated` Newsletters 조회)로 늘어남. Notion rate limit 관리 영역 진입.
- **추천: A**. C-A는 `TopicRepository.findPendingTopicsByTag`가 단일 필터로 끝나고, 비축분 중복도 Notion 쿼리 추가 없이 자연 방지. "프린트 실패 주제 복구"는 사용자가 Notion UI에서 `consumed → selected`로 되돌리는 수동 경로가 있어 MVP 범위에서 수용 가능. ADR-0005 §결정 4에 이미 박혀 있으므로 변경 시 ADR 상태도 동반 갱신 필요.

### Q6. 미프린트 Newsletter 집합 UI를 기존 `NewsletterScreen`에 확장할지, 별도 화면으로 분리할지

03-ui.md에서 U-A/U-B/U-C 세 안 비교 후 U-A 추천. Newsletters DB가 미프린트(`Status=generated`) + 프린트 이력(`Status=printed`)을 모두 담는 단일 DB라 한 화면에서 상태 뱃지로 구분하는 게 자연스럽다는 판단.

- **A. U-A: 기존 `NewsletterScreen`에 TopAppBar `+` 액션(수동 생성 BottomSheet) 추가 + 카드 상태 뱃지 문구 조정(`generated` → "미프린트 Newsletter 집합", `printed` → "프린트 완료")** — 현재 플랜 가정. `NewsletterScreen.kt` + `NewsletterViewModel.kt`만 수정, Navigation 변경 없음. 장점: MVP 단순성 최대. 사용자가 "뉴스레터 전체 = 한 화면" 멘탈 모델. 단점: 프린트 이력이 쌓이면 미프린트 Newsletter 집합(`generated`) 카드가 위쪽에 섞여 스크롤로 묻힐 수 있음 — 필터 칩은 후속.
- **B. U-B: 상단에 "미프린트(N) / 프린트 이력" 탭 2개. 미프린트 Newsletter 집합 탭에만 `+` FAB.** — `NewsletterScreen`에 TabRow 추가, 탭별로 `newsletters.filter { it.status == "generated" }` vs `printed`. 장점: 미프린트 가시성 높음. 단점: 사용자가 탭 전환 학습 필요. spec §3은 "Newsletters DB 한 풀"로 정의되어 있어 탭 분리는 사용자 의도 추측.
- **C. U-C: `ShelfScreen` 신설, Navigation에 별도 라우트 추가, 기존 `NewsletterScreen`은 프린트 이력 전용** — `ui/shelf/ShelfScreen.kt` + `ShelfViewModel.kt` 신설, `DailyNewsletterApp.kt` Navigation graph에 라우트 추가. 장점: 미프린트 Newsletter 집합 개념이 네비게이션 최상위에 드러남. 단점: spec이 요구하지 않은 구조 변경. 파일 2개 신설 + Navigation 수정 → 본 플랜 범위 확장.
- **추천: A**. spec §3은 "Newsletters DB 한 풀"로 미프린트 Newsletter 집합를 개념적 명칭으로만 쓴다. MVP 1일 E2E 검증(1명, 하루 1~2 프린트)에서 프린트 이력이 쌓이지 않아 스크롤 묻힘 문제 자체가 발생하지 않음. 구조 변경은 공개 배포 마일스톤에서 재고.

## 후속 플랜 (본 플랜이 출발점)

- 핸드오프 #6 (요일별 프린트 설정 UI — 슬롯 묶음 모델) — `SettingsRepository.getSlotsForDay` 실제 구현.
- 핸드오프 #7 (IPP 프린트 E2E + 후보 복수 시 Claude 추천).
- 핸드오프 #8 (1일 E2E 리허설).
