---
updated: 2026-04-29
status: accepted
owner: planner
summary: "Snapshot of what is implemented vs unimplemented after TASK-001~039a. Manual flow Scope A verified end-to-end. Auto-print alarm flow partial (scheduler done, popup pending)."
refs:
  - docs/specs/mvp.md
  - docs/context/project-map.md
  - docs/status.md
---

# Current State

This file is the **planner's working snapshot** of the codebase + planning state on the date in `updated:`. It does not propose technical fixes — it identifies where reality diverges from the MVP intent in [specs/mvp.md](../specs/mvp.md), so that the next planning round can target the gap.

For the **structural map** of the codebase, see [project-map.md](./project-map.md). For **what is currently in flight** at the agent level, see [status.md](../status.md).

## 1. What is implemented (today's reality)

### Built and reachable from the UI (Scope A — manual flow)
- **Notion 3-DB bootstrap** (`NotionSetupService.setupDatabases`) — Keywords/Topics/Newsletters with `Tags multi_select` 시드값 `모든주제`.
- **Keyword CRUD** with overhaul (TASK-030): type 구분 제거, 80자 초과 시 제목 ellipsis + Notion 페이지 본문 paragraph block, 등록 시간(`yyyy-MM-dd HH:mm`), 상단 태그 chip 바(필터/추가/길게눌러 삭제).
- **Topics view** with manual generation (TASK-039a): 주제 탭 우상단 `+` 버튼 → 전체 키워드 + 이력 기반 Gemini 호출 → 새 주제 N개 추가. 날짜 필터 제거 (모든 주제 표시).
- **Newsletter gallery + lazy detail** (TASK-014/033): 2-column LazyVerticalGrid, 카드에 상태 라벨 (`🖨 인쇄됨` / `✓ 생성됨`), 카드 탭 시 본문 lazy fetch + spinner. 상세 화면은 인쇄 버튼 1개(상단 우측) + BackHandler.
- **Manual newsletter generation** — BottomSheet (태그 + 페이지 수) → `NewsletterGenerationService.generateForSlot`:
  - **Single-topic deep dive** (TASK-026): 태그 매칭 pending 주제 중 최신 1건만 → Gemini가 구체적 시나리오·워크플로우·반패턴 포함한 1주제 deep dive.
  - **Mermaid 다이어그램** (TASK-032): 본문에 mermaid syntax 출력 → Notion `code` 블록 (language=mermaid)으로 변환 → 자동 렌더.
  - **Wikimedia 이미지** (TASK-037): `<img-search query="..."/>` 마커 → Wikimedia Commons API → 첫 결과 URL → Notion `image` 블록. 사진 1순위, mermaid는 사진이 어울리지 않는 구조 표현 보조.
- **System print via PrintManager** (TASK-023): 인쇄 버튼 → 시스템 다이얼로그 → 사용자 1탭 → Canon Print Service / Mopria plugin이 변환·송신. raw IPP/HTTP 폐기.
- **Settings**: Notion key, Notion parent page ID, Gemini key, 알람 시간(시:분), 알람 요일(월~일 chip), 페이지 슬라이더 1–5.
- **Gemini robustness**:
  - 4-step exponential backoff with jitter (`1.5/3/6/12s + 0~500ms`) on 502/503/504 (TASK-034/038).
  - Auto-fallback Flash → `gemini-2.5-flash-lite` after primary exhausts (TASK-038).
  - UI 진행 표시: 상시 배너(Running/Retrying), AlertDialog (최종 실패), Snackbar (성공) — "혼잡"/"실패" 워딩 제거 (TASK-035/036).
- **Alarm scheduler** (TASK-024/025): 시간/요일 영속화 + `AlarmManager.setAlarmClock` + `AlarmReceiver` + `BootReceiver`. 디바이스 재부팅 후 자동 재예약. **알람 발사 시 현재는 logcat만** — Service/Activity 미연결 (TASK-027).

### Built but not reachable from the user-facing happy path
- **Topic regenerate** (`TopicRepository.regenerateTopics()`) — 어딘가에서 호출되지만 새 generateTopicsManually flow와 중복. 추후 정리.
- **`AutoGenStatus`** sealed class + `clearAutoGenStatus()` in `KeywordViewModel` — 자동 주제 생성 제거 후 dead code (TASK-030 follow-up).

## 2. What is unimplemented (or stubbed) — current backlog

| Capability | State | Notes |
|---|---|---|
| 알람 시점 자동 인쇄 흐름 | **구현 완료, 디바이스 검증 대기** | TASK-027: AlarmService + AlarmActivity + AlarmReceiver 연결 + 자동 생성 흐름 통합. 사용자 검증 후 본 줄 제거. |
| 설정 화면 기본 프롬프트 | 미시작 | TASK-029: 사용자 정의 프롬프트가 Gemini 호출 시 추가 지시문으로 합쳐짐. |
| 주제 우선순위 시스템 | 미시작 | TASK-031: Notion `Priority` Number 속성 추가 + fractional indexing(1000-step) + 드래그드롭 + AI 자동 우선순위. 가장 큰 작업. |
| 주제 상세 화면 | 미시작 | TASK-031: 탭 시 진입, source keywords 표시, ready/consumed 상태, "특히 다루었으면 하는 부분" 입력. |
| 주제에 태그 시스템 (키워드와 동일) | 미시작 | TASK-031: chip 바 + 필터 + 추가/삭제. |
| Consumed 주제 최하단 + 회색 | 미시작 | TASK-031. |
| "추천이유" 한국어 매핑 | 미시작 | TASK-031: Notion 영문 속성 그대로, UI만 한국어 라벨. |
| 키워드 길게 눌러 주제 생성 | 미시작 | TASK-039b: KeywordScreen long-press → 해당 키워드 focus + 전체 컨텍스트로 생성. |
| Notion 토큰 logcat 노출 | 4회+ 발생 | release-hardening 후속: OkHttp log level NONE/BASIC. 사용자에게 토큰 회전 권장. |
| User-driven topic generation triggers (input keyword → auto, manual button, direct-write text) | **None of the three paths exists.** | Only the worker-driven path + `regenerateTopics` button exists today. |
| Tag CRUD UI (create / attach / detach) | **None.** | spec §3 requires fully-manual user flows. |
| Per-day print configuration (time + slot bundle + on/off toggle) | **None.** | UI today supports a single global print time only. |
| Newsletter `unprinted` pool semantics + lazy 2-at-print-time generation | **None.** | Newsletters DB only has `generated / printed / failed` and `Page Count`. |
| Topic `consumed` status | **Not in Topics schema** (`selected/read/modified` only). | Required by ADR-0005. |
| Multi-topic-per-newsletter with Claude-decided N | Partially — current `NewsletterGenerationService` already passes a topic list to Claude, but it pulls "today's topics", not slot-matched topics. | Requires slot-aware repository methods. |
| Claude recommender for picking 1-of-N newsletter candidates | **None.** | Stub deferred to handoff #7 per newsletter-shelf Q1. |
| ePrint email print path | Stubbed — throws `UnsupportedOperationException`. | Spec §7 marks this OUT for MVP. |
| Tests (unit / instrumented) | None. | MVP acceptance is "manual 1-day E2E", per spec. |
| Onboarding / error UX hardening / secret hygiene | None. | `release-hardening` track declared in `status.md` but `docs/plans/release-hardening.md` is not created. |

## 3. Risk hotspots / dangerous design points

These are places where the current code will **silently misbehave** if used as-is, or where a refactor is brittle. Listing here so the next plan acknowledges them.

1. **Pipeline shape mismatch.** The spec (§3 "파이프라인 구동축") redefines the pipeline as **user-action driven + print-time lazy fill-up**, but the running code is still on the old T-2h / T-30m / T / 00:00 chain. Three of the four workers and two of the three services no longer match the intended trigger model. The `newsletter-shelf` and `topic-generation-paths` plans both propose tearing out workers + services, but they are still in `review` / `in-progress`.

2. **`PrintWorker` is non-functional via the scheduler.** `WorkScheduler.schedulePrint(...)` enqueues it as a periodic work with **no input data**, but `PrintWorker.doWork()` reads `inputData.getString("newsletter_id")` and returns `Result.failure()` if absent. The scheduler never supplies that key, so the daily auto-print path has **never worked end-to-end**. The only working print today is the manual button on `NewsletterScreen`.

3. **`TopicSelectionService` ↔ `TopicRepository` cycle.** Hand-resolved with a setter (`setTopicRepository`). If anyone re-introduces constructor injection without first deleting the cycle (which the topic-generation-paths plan proposes by removing `TopicSelectionService` outright), Hilt graph compilation will break.

4. **`OkHttpClient` logs full bodies (Notion + Claude API keys + payloads) at `Level.BODY`.** The newsletter-shelf 04-infra.md notes this. No plan owns the fix yet — `release-hardening` track is declared in `status.md` but its plan file is missing.

5. **`NotionSetupService.setupDatabases()` is one-shot and skips on resume.** It bails out if `KEY_KEYWORDS_DB_ID` is already set. There is no "reset / re-setup" flow. If the user wants to start over (or if a tag-system migration adds new properties to existing DBs), they must manually trash the Notion pages and re-run setup. Newsletter-shelf Q5 in `tag-system/README.md` already flags this.

6. **`NewsletterRepository.getNewsletters()` is N+1 over Notion.** It calls `getBlockChildren` for every page in the result. With small data this is a small problem; with growth it becomes Notion rate-limit territory.

7. **`NewsletterGenerationService` writes the entire HTML payload as a single Notion paragraph block.** A single `richText.text.content` does not enforce Notion's 2000-character limit per rich-text segment. With 2-page (≈3600 char) outputs this is quietly already over the wire-level limit.

8. **`KeywordRepository._keywords: MutableStateFlow` is the only "fresh state" notion.** `getPendingKeywords()` calls `refreshKeywords()` from inside, so any caller sees a Notion round-trip. There is no in-process invalidation contract — concurrent UI + Worker calls can stomp on the flow.

9. **Single global print time via `KEY_PRINT_TIME_HOUR/MINUTE`.** The spec wants per-weekday time + slot bundles + on/off toggle. Adding the per-weekday model touches: schema, `WorkScheduler`, `PrintWorker` input data, `SettingsScreen` UI, and migration of the existing single time. The `weekday-print-slots` plan covers this but is still `draft`.

## 4. Unresolved intent questions (planner's queue)

These are the open questions waiting on user confirmation across the four design plans. They block the design plans from moving from `review` / `draft` / `in-progress` to `accepted`.

### Plan: tag-system (in-progress)
- Q3: Do we cap tags-per-page (Notion allows ≤100)?
- Q4: For automatic topic generation, who attaches non-seed tags? (After 2026-04-19 round 1, the answer is "no one — Claude does not assign tags." This may now be answered; needs verification in the plan body.)
- Q5: How do we handle existing Notion DBs when the tag-system rolls in? (Manual trash + re-setup vs automatic reset button.)

### Plan: topic-generation-paths (review) — 5 open
- Auto-path call frequency / debounce / toggle.
- Whether the auto path flips a keyword to `resolved`.
- Manual button when pending == 0: disabled or guidance dialog.
- Tag option-pool autocomplete UI scope.
- Tag-attachment policy in the auto path. (Round 1 says "Claude does not attach tags" — most of these may now resolve to "no Claude tag involvement" but the plan body has not been re-edited yet.)

### Plan: newsletter-shelf (review) — 6 open
- Q1: Implement Claude recommender for N candidates now or defer to handoff #7.
- Q2: How to stub `getSlotsForDay` until the weekday-print-slots plan lands.
- Q3: Where / when to surface Claude API cost guidance.
- Q4: Cap on N (topics-per-newsletter).
- Q5: `consumed` transition timing — at save vs at print.
- Q6: Newsletter pool UI shape (single screen with badges vs tabs vs separate route). After round 1 dropped the "shelf" vocabulary, Q6 needs re-reading.

### Plan: weekday-print-slots (draft) — 5 open
- Q1: Where does the slot-bundle JSON live (k/v vs new Room table)?
- Q2: How does the scheduler handle 7 weekdays (7 unique periodic works vs 1 + filter)?
- Q3: UX shortcut for "all weekdays same time".
- Q4: Slot-uniqueness violation feedback.
- Q5: Whether the printer-IP UI is rewritten here or deferred to handoff #7.

### Cross-cutting (no plan owns them yet)
- Whether MVP needs a "reset Notion DBs / re-run setup" flow.
- Whether `release-hardening` (full-body logging + cost guidance + onboarding) gets its own plan now or stays as scattered notes.
- Whether the spec § "round 1 re-confirmation" implies that the plans below should be **re-edited in place** before proceeding, or whether designer should publish a v2 of each.

## 5. Risk that the user has not yet seen a working E2E

Per the spec's own emphasis: "사용자는 1~8번을 한 번도 끝까지 돌려본 적이 없다." MVP success is **a paper hitting the user's hand**, not "code compiles". Until the four plans (`tag-system`, `topic-generation-paths`, `newsletter-shelf`, `weekday-print-slots`) land in coordinated form and the round-1 re-confirmation is reflected, every path forward should be evaluated by "does this make the 1-day E2E shorter?" rather than "does this finish a sub-feature?".

## 6. Next planning candidates

순서는 **자동 인쇄 흐름 완성도**와 **사용자 가치**로 정렬. 사용자 확정 순서.

| # | Task | Files | 비고 |
|---|---|---|---|
| ~~1~~ | ~~**TASK-027 — 알람 팝업 + 사운드 + 자동 생성**~~ | 8 파일 | **구현 완료, 검증 대기** (2026-04-29). |
| 1 | **TASK-029 — 설정 화면 기본 프롬프트 추가 지시문** | `SettingsEntity.kt`, `SettingsRepository.kt`, `SettingsScreen.kt`, `SettingsViewModel.kt`, `NewsletterGenerationService.kt` (프롬프트 합치기) | 옵션 a (additional instructions) 채택. |
| 2 | **TASK-031 — 주제 오버홀** (가장 큼) | `TopicRepository.kt`, `TopicsViewModel.kt`, `TopicsScreen.kt`, `NotionSetupService.kt` (Priority 속성 추가), `GeminiTopicSuggester.kt` (AI priority 출력), `NewsletterGenerationService.kt` (top-priority pick) | Priority Number(fractional indexing) + 드래그드롭 라이브러리 `composereorderable` + 상세 화면 + 태그 시스템 + consumed 회색. |
| 3 | **TASK-039b — 키워드 길게 눌러 주제 생성** | `KeywordScreen.kt`, `KeywordViewModel.kt`, `GeminiTopicSuggester.kt` (focus 인자 변형) | TASK-030/039a 끝난 뒤 후속. |

### Follow-up (out-of-scope MVP, 시간 나면)

- KeywordViewModel dead code 정리 (`AutoGenStatus`, 사용 안 되는 의존성).
- `release-hardening`: OkHttp log level release-build NONE/BASIC. Notion 토큰 노출 차단.
- CleanupWorker 운명 결정 (남길지/제거).

## 7. How this snapshot evolves

Refresh this file when:
- A plan's `status` changes (draft ↔ review ↔ accepted ↔ in-progress ↔ consumed).
- A new gap or risk is uncovered that should bias the next planning round.
- The user picks a candidate from §6 — strike it, add a pointer to the new spec/plan.
- The pipeline shape in code actually matches the spec (then most of §3 collapses).

When updating, also bump `updated:` and, if a structural shift happened, mirror it in [project-map.md](./project-map.md).
