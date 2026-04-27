---
updated: 2026-04-26
status: accepted
owner: planner
summary: "Snapshot of what is implemented vs unimplemented vs broken, the open intent questions, and the next planning candidates. (Round-1 plan sweep done 2026-04-26.)"
refs:
  - docs/specs/mvp.md
  - docs/context/project-map.md
  - docs/status.md
---

# Current State

This file is the **planner's working snapshot** of the codebase + planning state on the date in `updated:`. It does not propose technical fixes — it identifies where reality diverges from the MVP intent in [specs/mvp.md](../specs/mvp.md), so that the next planning round can target the gap.

For the **structural map** of the codebase, see [project-map.md](./project-map.md). For **what is currently in flight** at the agent level, see [status.md](../status.md).

## 1. What is implemented (today's reality)

### Built and reachable from the UI
- Notion 3-DB bootstrap (`NotionSetupService.setupDatabases`) — Keywords, Topics, Newsletters siblings under a parent page.
- Keyword CRUD (add / list / soft-delete / toggle resolved / filter) via `KeywordScreen` + `KeywordRepository`.
- Today's-topics view (`TopicsScreen`) with manual edit / delete / "regenerate" (deletes today's topics + re-runs Claude topic selection).
- Newsletter list (`NewsletterScreen`) with WebView preview and a manual `Print` button that hits `NewsletterRepository.printNewsletter(id)` → `PrintService` → IPP.
- Settings (`SettingsScreen`): Notion key, Notion parent page ID, Claude key, printer IP, printer email, single daily print time, pages slider 1–5.
- IPP-over-HTTP print path (`PrintService.printViaIpp`) — manually-assembled IPP request bytes, no IPP library. Output target is Canon G3910.
- HTML → PDF on-device (`PdfService`).
- Tag normalization utility (`TagNormalizer.normalize` + `ensureFreeTopicTag`) — shipped Apr 19, but the seed string is still `"자유주제"` (rename pending in status.md).

### Built but not reachable from the user-facing happy path
- Time-chain workers (`DailyTopicWorker @ T-2h`, `NewsletterWorker @ T-30m`, `PrintWorker @ T`, `CleanupWorker @ 00:00`) and the orchestrator that enqueues them (`WorkScheduler.scheduleAll`). They wire up but the chain is mismatched with the spec (see §3).
- `TopicSelectionService.selectAndSaveTopics()` — Claude-driven topic batch generator. Today this is invoked by `DailyTopicWorker` and by `TopicRepository.regenerateTopics()`. The plan `topic-generation-paths` proposes deleting it.
- `NewsletterGenerationService.generateAndSaveNewsletter()` — single newsletter generator from "today's topics". The plan `newsletter-shelf` proposes replacing it with a slot-aware `generateForSlot(tag, pageCount)`.

## 2. What is unimplemented (or stubbed)

| Capability | State | Notes |
|---|---|---|
| Tag axis on Keywords / Topics / Newsletters DBs | **None in Notion schema yet.** | ADR-0003 requires `Tags multi_select`; tag-system plan steps 3+ do this. |
| `자유주제` → `모든주제` rename | Constant + literal still old. | implementer task queued in status.md. |
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

Recommendations are ordered by **how much they unblock the 1-day E2E**, not by code size. Each item is a planning candidate; **none of these are accepted spec items yet** — the user picks which ones become specs, in which order.

| # | Candidate | Why it earns priority |
|---|---|---|
| 1 | ~~**Round-1 reflection sweep on the 4 active plans**~~ → **done 2026-04-26**: 4개 plan README + ADR-0003 v3 / ADR-0005 (accepted) / ADR-0006 (단일 슬롯 scope 명확화) 라운드 1 정합 완료. sub-files(01-XX 등) 옛 어휘 잔여는 README의 라운드 1 callout으로 갈음. 폴더명 `newsletter-shelf/`와 ADR 파일명 `0005-newsletter-shelf-lazy-generation.md`는 sweep scope 밖이라 유지. 다음 후보는 #2 (PrintWorker 입력 데이터 미스매치). | Unblocks 4 plans in one motion. |
| 2 | ~~**`PrintWorker` enqueue-vs-input-data mismatch**~~ → **done 2026-04-26** (TASK-002): `WorkScheduler.schedulePrint` 제거(22줄). spec §3 사용자 행동 트리거 모델 채택. 자동 주기 인쇄는 newsletter-shelf + weekday-print-slots plan이 `PrintOrchestrator` 도입 시점에 재배선. 빌드 검증은 `SKIPPED_ENVIRONMENT_NOT_AVAILABLE`(로컬 Java 미설치) — 다른 환경에서 verifier 재시도 권장. | Removes a silent failure mode that would derail any 1-day E2E rehearsal. |
| 3 | **MVP "reset Notion DBs / re-run setup" flow as a spec item** — required to land tag-system schema migrations cleanly. Currently no flow; users have to manually trash pages. | Migration ergonomics for tag-system + any future schema move. |
| 4 | **`release-hardening` plan creation** — collect the 4 known infra notes (full-body logging, Claude cost guidance, Notion token logging, onboarding errors) into one plan with a defined trigger ("public release milestone"). | Keeps the MVP scope clean while preventing these from rotting in plan footnotes. |
| 5 | ~~**1-day E2E rehearsal spec**~~ → **draft 2026-04-26**: `docs/plans/e2e-rehearsal/README.md` 1차 작성. 8단계 시나리오 + 선행 plan 매핑 + 실패 롤백 매핑 + 사용자 확인 5개. 선행 4개 plan(tag-system/topic-generation-paths/newsletter-shelf/weekday-print-slots)의 핵심 단계 완료 후 1차 시도. | Acts as the integration acceptance for the round-1 plan rewrites. |
| 6 | **CleanupWorker fate** — spec §3 says it is "outside MVP judgment scope" but the worker is still scheduled and could run during the 1-day rehearsal and surprise the user. Decide: leave wired, leave wired but disabled, or remove. | Removes a wildcard during E2E. |
| 7 | **Multi-slot promotion criterion** — spec deferred multi-slot to "공개 배포 마일스톤". A short note on what evidence promotes it (e.g. "the single-slot E2E has been observed to succeed 7 days in a row") would prevent ambiguity later. | Makes the future trigger explicit. |

## 7. How this snapshot evolves

Refresh this file when:
- A plan's `status` changes (draft ↔ review ↔ accepted ↔ in-progress ↔ consumed).
- A new gap or risk is uncovered that should bias the next planning round.
- The user picks a candidate from §6 — strike it, add a pointer to the new spec/plan.
- The pipeline shape in code actually matches the spec (then most of §3 collapses).

When updating, also bump `updated:` and, if a structural shift happened, mirror it in [project-map.md](./project-map.md).
