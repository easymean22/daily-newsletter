---
updated: 2026-04-26
status: accepted
owner: designer
summary: "미프린트 Newsletter 집합 = Newsletters DB 미프린트 집합 + lazy 2개 동기 + consumed 저장 직후 + NewsletterWorker 제거. 라운드 1: '진열대/shelf/풀' 어휘 폐기."
consumed_by:
  - "2026-04-26 planner: 라운드 1 supersedes — '진열대/shelf/풀' 어휘 폐기, '미프린트 Newsletter 집합'으로 통일. lazy 2개 생성·consumed 시점·Worker 제거 등 결정 본체는 유지."
next_action: "implementer 진입 (newsletter-shelf 플랜 체크리스트 1~12)"
refs:
  - docs/plans/newsletter-shelf/README.md
  - docs/specs/mvp.md
---

# ADR-0005: 뉴스레터 lazy 보충 모델 (프린트 시점 2개 생성)

**상태:** accepted (라운드 1 어휘 정합 후 2026-04-26 accepted)
**날짜:** 2026-04-19 (proposed) / 2026-04-26 (라운드 1 어휘 정합 + accepted)
**결정자:** designer · planner (라운드 1)

## Round-1 vocabulary update (2026-04-26)

라운드 1 결정에 따라 본 ADR의 모든 본문에서 "진열대 / shelf / Shelf / 풀" 어휘는 폐기되고, "미프린트 Newsletter 집합" 또는 "Newsletters DB의 `Status = generated` 페이지 집합" 같은 중립 표현으로 대체된다. 결정 1~5(미프린트 집합 = `generated` 페이지 집합 / lazy 2개 동기 / 슬롯별 생성 / consumed 저장 직후 / NewsletterWorker 제거)의 본체는 변경 없음. 파일명 `0005-newsletter-shelf-lazy-generation.md`는 sweep scope 밖이라 그대로 둔다. supersedes 체계가 필요하면 별도 ADR로 박지 않고 본 절을 참조한다.

## 번호 할당 메모

ADR-0003(태그)의 후속 "ADR-0004 API 키 저장소 보안 강화" 및 "ADR-0005 공개 레포 이관"은 release-hardening 트랙이 착수 전이라 아직 작성되지 않았다. 본 ADR은 MVP 1일 E2E를 막고 있는 lazy 보충 모델 결정을 더 시급한 것으로 판단해 번호 0005를 사용한다. release-hardening 트랙이 본 ADR을 참조해 남은 ADR 번호를 재배치해도 된다 (0004=API 키 저장소, 0006=공개 레포 이관).

## 컨텍스트

`docs/specs/mvp.md` §3에서 planner는 뉴스레터 파이프라인을 **"미프린트 Newsletter 집합(Newsletters DB 미프린트 집합)"** 모델로 재정의했다.

- 뉴스레터는 평소에 사용자가 수동으로 생성하거나, **프린트 시점 lazy 보충**으로 쌓여 있는 풀에서 꺼내 쓴다.
- 프린트 시점에 슬롯(요일 × 태그 × 장수)에 매칭되는 미프린트 뉴스레터가 없으면 **그 시점에 2개를 생성**한다 (1개 즉시 소비, 1개 미프린트 Newsletter 집합 비축).
- 1개 뉴스레터는 **주제 N개를 Claude가 자동으로 엮어** 만든다. 사용자는 슬롯의 장수만 지정.
- 사용된 주제는 `consumed` 처리되어 이후 뉴스레터 생성에 재사용 불가.

현재 코드는 이 모델과 충돌한다:

- `NewsletterWorker`가 T−30m에 `NewsletterGenerationService.generateAndSaveNewsletter()`를 호출 → 태그/슬롯 고려 없이 "오늘의 주제 전체"로 HTML 1개 생성.
- `NewsletterRepository.printNewsletter(id)`는 호출자가 ID를 넘겨야 함 — 슬롯 매칭 로직 없음.
- 주제 consumed 개념이 Topics DB에 없음 (Status는 selected/read/modified).

지금 결정해야 하는 이유:

1. MVP 핸드오프 #4·#5가 결정에 의존. 슬롯 매칭(#6) + 프린트 실기(#7) + 1일 E2E(#8)도 모두 본 결정 위에 쌓인다.
2. "lazy 2개 생성의 트리거 시점"과 "생성 단위(슬롯별 vs 전역)"는 Notion 데이터 모델과 Worker 구조를 동시에 흔드는 결정 — 구현 시작 후 뒤집으면 Worker 재작성과 Notion 스키마 수정이 같이 일어난다.
3. Claude API 호출은 비용·지연이 크다 (프롬프트 + 응답 8K 토큰). 2개를 동기로 생성하면 프린트 지연 수 분. 비동기로 1개만 쓰고 다른 하나를 BG에서 만들면 실패 격리 정책이 달라진다. 이 축을 먼저 확정해야 한다.

## 결정

### 1. 미프린트 Newsletter 집합 = Notion `Newsletters` DB의 미프린트 페이지 풀 (새 엔티티 도입하지 않음)

- "미프린트 Newsletter 집합"는 개념적 명칭일 뿐, 데이터는 기존 `Newsletters` DB에 `Status = "generated"`(= unprinted)로 저장된 페이지 집합이다.
- 별도 DB/엔티티를 도입하지 않는다 — spec §3의 "Notion Newsletters DB에 미프린트 상태로 쌓여 있는 풀" 문장과 1:1 매핑.
- `Newsletters.Status` select 옵션은 기존 `generated | printed | failed`를 그대로 사용. 시각적으로는 `generated`가 미프린트 Newsletter 집합 비축분을 뜻하도록 **UI/문서에서만 "미프린트 Newsletter 집합"로 부른다**.
- 매칭 쿼리는 `Status = "generated"` AND `Tags multi_select contains <슬롯 태그>` AND `Page Count = <슬롯 장수>`.

### 2. lazy 2개 생성은 **슬롯별 트리거**, **동기 2개**

트리거 시점: **프린트 시각 도달 시, 슬롯 하나를 처리하는 루프의 첫 단계에서 매칭 후보가 0건이면**.

- 앱 진입 시 / 주제 추가 시 / 프린트 소비 직후 등 다른 시점에는 lazy를 돌리지 않는다.
- 사용자 행동으로 뉴스레터가 만들어지는 유일한 비-lazy 경로는 **수동 생성 버튼**.

생성 단위: **2개 동기 생성** (1개 소비, 1개 비축).

- 2개 모두 `Newsletters` DB에 저장 후, 1개를 즉시 프린트·`printed` 전환. 남은 1개는 `generated`로 풀에 남음.
- "1개만 동기 생성 + 2번째는 BG에서 늦게" 안은 기각 (§대안 C 참조).
- 2개가 동일 슬롯(태그·장수)을 타깃으로 하므로 두 생성 모두 같은 슬롯 조건을 만족해야 한다.

예외:

- 첫 번째 생성 성공 + 두 번째 생성 실패 → 1개는 프린트, 비축 실패. 사용자에게 알림 (spec §6 엣지의 "뉴스레터 생성 실패: 알림만, 스킵"의 완화 적용 — 슬롯 당일 성공 + 다음 회차는 다시 lazy).
- 첫 번째 생성 실패 → 슬롯 스킵, 사용자 알림. 두 번째는 시도하지 않음.
- 주제 풀 자체가 비어 생성 불가 → 슬롯 스킵, 사용자 알림 (spec §3 "주제 풀도 비어 생성 자체가 불가하면 그 슬롯은 건너뛰고 사용자에게 알림").

### 3. 다중 주제 엮기: **Claude가 N 결정, 슬롯 단위로 주제 consumed**

- `NewsletterGenerationService.generateForSlot(tag: String, pageCount: Int): NewsletterUiItem` 신규 메서드가 **슬롯 1개**에 대응한다.
  - 입력: 슬롯 태그(단일) + 장수.
  - Claude에게 "이 태그를 가진 pending 주제들 중에서 N개를 골라 장수에 맞게 엮어라"라고 지시.
  - Claude 응답: `{ "selectedTopicIds": [...], "html": "..." }` — 선택된 주제 ID들과 완성 HTML.
- 선택된 주제는 `Topics.Status = "consumed"`로 전환 (§4).
- `pageCount` → 목표 글자 수 변환은 기존 `pages * 1800` 공식 유지 (검증은 핸드오프 #8 수동 E2E).
- 다중 태그(#6에서 OR/AND 결정될 예정)는 본 ADR scope 밖 — 본 ADR은 **슬롯 태그가 단일 정규화 키**라는 최소 가정만. #6이 다중 태그 슬롯을 도입하면 본 메서드 시그니처를 `List<String>`으로 확장.

### 4. Topics 상태에 `consumed` 추가 + 재사용 차단

- `Topics.Status` select 옵션에 `consumed` 추가. 기존 `selected | read | modified`는 유지 (의미 정리는 후속 #2 플랜이 이미 진행 중 — 본 ADR은 `consumed`만 추가).
- consumed 처리 시점: 뉴스레터 저장 커밋 직후(Claude 응답 → Newsletters createPage 성공 → 해당 topics 일괄 `consumed` 업데이트).
- 뉴스레터 `Topics` relation은 consumed 주제를 그대로 가리킨다 — 히스토리 보존.
- 생성 시 주제 후보 쿼리: `Status != consumed` AND `Tags contains <슬롯 태그>`.
  - Notion 필터에 `does_not_equal`이 있으므로 단일 필터로 표현 가능. 태그 매칭은 ADR-0003의 정규화 키로 후처리 한 번 더.

### 5. 기존 Worker 처분

- **`NewsletterWorker` 제거.** T−30m 스케줄 삭제.
  - 근거: spec §3 재정의로 "뉴스레터 사전 생성"이 시간축 트리거에서 "사용자 행동 + lazy" 트리거로 이동. T−30m 스케줄은 의도 위배.
  - 미프린트 Newsletter 집합가 평소 비어 있는 것이 기본 가정 → lazy가 1차 방어선. 스케줄된 사전 생성은 불필요.
- **`PrintWorker` 유지하되 입력 방식 변경.** 현재는 `inputData.newsletter_id` 1개를 받아 `printNewsletter(id)` 호출. 변경 후:
  - 입력 없이 실행 (단일 periodic work). 내부에서 `PrintOrchestrator.runForToday()` 호출 → 오늘 요일의 모든 슬롯을 순차 처리.
  - 슬롯별 매칭 + (필요 시) lazy 생성 + 프린트 + 상태 전환까지 책임.
- 슬롯 단위 WorkRequest로 쪼개지 **않는다** — 프린트는 물리 프린터 1대이고 동시 출력 의미가 없으며, Worker 쪼갬은 실패 격리 이득보다 배선 복잡도가 크다. 슬롯 간 실패 격리는 Orchestrator 내부 try/catch 루프로 충분.

### 6. 스케줄은 요일별 프린트 시각 1개 (MVP 최소)

- 본 ADR은 "프린트 시각 = 요일 × 단일 시각" 가정 유지. spec §3의 "각 요일은 (프린트 시각, 슬롯 묶음)으로 정의"와 일치.
- 요일별 **시각이 다른 경우**의 스케줄링(7개 각각 다른 시각)은 **핸드오프 #6**에서 결정. 본 ADR은 "WorkScheduler가 요일마다 독립 스케줄을 걸 수 있도록 구조는 열어둔다"까지만.
- 현재 코드는 단일 시각으로 매일 같은 시간에 돌린다 → 본 ADR 구현 단계에서도 유지. 확장은 #6이.

## 대안

### (A) 미프린트 Newsletter 집합를 별도 Notion DB로 분리 — 기각

- `Newsletters`는 printed 이력, `NewsletterShelf`는 미프린트 풀로 나눠 운영.
- 기각 사유:
  1. spec §3은 "Newsletters DB에 미프린트 상태로 쌓여 있는 풀"로 명시 — 두 DB 분리를 요구하지 않는다.
  2. DB 2개 간 이동(미프린트 → printed 전환)이 Notion createPage + deletePage 2 라운드트립. 현재의 status update 1회보다 비싼 연산.
  3. 히스토리 추적이 끊김 — "이 뉴스레터가 언제 진열됐다 언제 프린트됐나" 같은 시계열 분석이 어려워짐.
  4. MVP는 본인 1명, DB 수를 늘리는 게 인지 부담만 키움 (ADR-0003 태그의 옵션 B 기각 논리와 동일).

### (B) 미프린트 Newsletter 집합를 로컬 Room에 캐시 — 기각

- 빠른 매칭 쿼리를 위해 미프린트 뉴스레터 메타를 Room에 복제.
- 기각 사유: "Notion이 진실의 원천" 원칙(CLAUDE.md "데이터 계층") 위반. 단말 리셋 시 캐시 손실, 다른 디바이스와 불일치. MVP에서 매칭 1회/요일 빈도는 1 Notion 쿼리로 충분.

### (C) lazy 1개 + BG 비동기 2번째 — 기각

- 프린트 시점에 1개만 동기 생성 → 즉시 프린트. 2번째 비축분은 별도 WorkRequest로 BG에서 나중에 생성.
- 장점: 프린트 체감 지연 최소.
- 기각 사유:
  1. "실패 격리"가 도리어 악화. BG 2번째 생성이 실패하면 다음 회차에 또 lazy가 돌면서 1개 생성, 즉 미프린트 Newsletter 집합는 영영 비어 있는 상태. spec §3의 "비축해 둔다"라는 의도가 무력화.
  2. 상태 전이가 복잡해짐 (`generated`만으로 부족 — `generating`, `generated`, `printed`, `failed` 4개 상태 필요).
  3. Claude 호출의 실제 병목은 응답 대기(수 초~수십 초). 프롬프트만 사용자 의도에 맞게 만들면 2개 연속 호출도 수용 가능 (spec §6 엣지 "당일 1회 재시도" 정책 하에서).
  4. BG 작업의 WorkManager 신뢰성은 공개 배포 마일스톤의 "장기 신뢰성" 이슈 — MVP는 본인 1명 1일 E2E이 기준.
- MVP가 아닌 공개 배포 마일스톤에서 체감 속도가 문제되면 재고 가능 (superseding ADR로).

### (D) "N=auto"를 뉴스레터 생성 루프 단계로 — 기각

- Claude에게 "N과 분량 결정만 먼저 → 두 번째 호출로 본문" 두 단계 호출.
- 장점: 단계별 실패 복구가 쉬움 (N 결정 실패 시 빠르게 포기).
- 기각 사유: 호출 2배 비용 + 레이턴시 2배. Claude는 단일 호출에서 N 결정 + 본문 동시에 해결 가능 (프롬프트 설계 — 핸드오프 #5 영역).

### (E) 트리거 시점을 "사용자가 앱 진입" + "주제 추가" + "프린트 시점" 세 개로 — 기각

- 미프린트 Newsletter 집합가 더 자주 채워지는 효과. 사용자가 앱을 열면 비축량을 자동 복구.
- 기각 사유:
  1. 호출 비용 예측 불가 (앱 진입 빈도에 종속). Claude API 비용이 사용자 행동으로 폭주 가능.
  2. spec §3의 "**프린트 시점에 미프린트 Newsletter 집합가 비어 있으면 즉석 생성으로 보충**"이 단일 트리거로 명시. 확장은 spec 변경 후에.
  3. "사용자 행동 트리거"는 spec에서 주제 생성에만 적용 (§3 재정의). 뉴스레터는 **수동 생성 버튼** + **프린트 시점 lazy**, 두 개만이 명시된 트리거.

## 영향

### 데이터 모델 변경 (Notion 측)

- **`Topics.Status` select에 `consumed` 추가** — `NotionSetupService.setupDatabases()`에 옵션 추가. 기존 DB는 Notion UI에서 옵션 추가하거나 reset 경로(기존 DB 삭제 후 재생성)로.
- **`Newsletters.Page Count` 매칭 필수** — 기존 필드 그대로. 쿼리에 `number.equals` 추가.
- **`Newsletters.Tags` multi_select** — ADR-0003에서 추가 예정. 본 ADR은 그 위에 쌓임.

### 코드 변경 범위 (실제 작업은 implementer — 핸드오프 #4·#5 플랜에서 상세)

- `data/repository/NewsletterRepository.kt`:
  - `findUnprintedNewsletterByTag(tagName: String, pageCount: Int): NewsletterUiItem?` 신설 (ADR-0003 plan §4단계의 변형 — `pageCount` 추가).
  - `saveNewsletter`는 ADR-0003에서 `tags` 추가. 본 ADR은 `selectedTopicIds`를 `topicIds`로 그대로 사용.
  - 슬롯 매칭 + lazy 생성 + 프린트 + 상태 전환을 포괄하는 고수준 메서드 `consumeForSlot(tag: String, pageCount: Int)` 추가 (또는 service 레이어로 이동 — 플랜 결정).
- `service/NewsletterGenerationService.kt`:
  - 기존 `generateAndSaveNewsletter()`는 제거 또는 `generateForSlot(tag, pageCount)`로 재작성.
  - 프롬프트 변경 (N=auto, 주제 선택, 태그·장수 가이드).
  - 응답 파싱 강화 (선택 주제 ID + HTML).
- `service/PrintOrchestrator.kt` (신규):
  - `runForToday()` — 오늘 요일의 슬롯 목록 조회 → 각 슬롯에 대해 매칭 → 없으면 lazy 2개 생성 → 1개 프린트 + 다른 1개 비축 → Topics consumed 전환.
  - 슬롯 간 try/catch로 실패 격리.
- `data/repository/TopicRepository.kt`:
  - `findPendingTopicsByTag(tagName: String): List<TopicUiItem>` 추가 (`Status != consumed` AND `Tags contains tag`).
  - `markTopicsConsumed(ids: List<String>)` 추가.
- `worker/NewsletterWorker.kt` **삭제**.
- `worker/PrintWorker.kt`:
  - `inputData.newsletter_id` 제거. `PrintOrchestrator.runForToday()` 호출로 대체.
- `worker/WorkScheduler.kt`:
  - `scheduleNewsletterGeneration()` + 호출 제거.
  - 업데이트 시 1회 `cancelUniqueWork("newsletter_generation")` 필요 (같은 방법이 topic-generation-paths 플랜에도 있음 — DailyTopicWorker 제거).

### UI 영향

- **수동 뉴스레터 생성 버튼** 추가 — 뉴스레터 화면 TopAppBar 또는 FAB. 사용자가 태그·장수를 선택해 즉시 생성 → 미프린트 Newsletter 집합에 추가. 구체 UI는 플랜에서.
- **미프린트 Newsletter 집합 상태 뱃지** — 뉴스레터 카드에 `미프린트 Newsletter 집합(generated)` / `프린트됨(printed)` / `실패(failed)` 구분. 현재 UI가 이미 `status`를 표시 중 — 문구만 조정 ("생성됨" → "미프린트 보관중").
- **별도 "미프린트 Newsletter 집합 화면"은 만들지 않는다** — 기존 `NewsletterScreen`이 Newsletters DB 전체를 보여주므로 미프린트 Newsletter 집합 = 해당 화면에서 `generated` 상태 필터. spec §3에도 미프린트 Newsletter 집합 전용 화면 요구 없음.

### MVP 1일 E2E에 미치는 영향

spec §4 acceptance 체크리스트의 7번 ("프린트 시점 도달 — 미프린트 Newsletter 집합에 매칭 뉴스레터 없을 경우 즉석 2개 생성")이 본 ADR의 직접 검증 지점. 1일 E2E 성공이 본 ADR의 수용 기준.

### 되돌리기 비용

- lazy 보충 모델(§1) 되돌리기: 중간 — Newsletters DB 스키마는 유지되므로 데이터 이전 없이 코드만 재작성 가능.
- lazy 2개 동기(§2) 되돌리기: 큼 — `generating` 상태 도입 + BG Worker 추가 등 상태 머신이 크게 달라짐. 이 축을 ADR로 박는 주된 이유.
- consumed 전이(§4) 되돌리기: 중간 — Notion Status select에 옵션만 추가된 상태라 옵션 제거는 가능하나, 이미 consumed로 전환된 주제 레코드가 있으면 되돌릴 판단 기준이 모호.

## 의도적으로 본 ADR이 결정하지 않는 것 (핸드오프로 위임)

- 요일별 슬롯 묶음 데이터 모델(SettingsEntity 저장 형태) + UI — **핸드오프 #6**.
- 다중 태그 슬롯의 OR/AND 매칭 — **핸드오프 #6**.
- 후보 뉴스레터가 여러 개일 때 Claude 추천으로 1건 선정 — **핸드오프 #7**.
- 뉴스레터 생성 실패 당일 재시도/알림 정책 상세 — **핸드오프 #4·#7** (본 ADR은 기본 원칙만 §결정 2 예외).
- 주제 Status의 `selected / read / modified` 의미 정리 — **핸드오프 #2** (진행 중, topic-generation-paths.md).
- Claude 프롬프트 구체 설계 — **핸드오프 #5** (본 ADR은 스키마만).
- ePrint 경로 — MVP OUT.

## 관련

- [ADR-0003](0003-tag-system-data-model.md) — 태그 데이터 모델 (본 ADR의 전제)
- spec: [`docs/specs/mvp.md`](../specs/mvp.md) §3 "뉴스레터 관리 — lazy 보충 모델", §3 "프린트 (슬롯별 매칭 + lazy 보충)", §7 핸드오프 #4·#5
- 플랜: [`docs/plans/newsletter-shelf-model.md`](../plans/newsletter-shelf-model.md) — 본 ADR의 실행 단계
- 전제 플랜: [`docs/plans/tag-system.md`](../plans/tag-system.md), [`docs/plans/topic-generation-paths.md`](../plans/topic-generation-paths.md)
