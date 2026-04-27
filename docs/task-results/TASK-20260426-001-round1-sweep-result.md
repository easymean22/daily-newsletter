# Task Result: Round-1 reflection sweep across 4 active plans + ADRs

Task ID: TASK-20260426-001
Status: completed (2026-04-26)
Owner: planner (executed directly in main session — no agent delegation)

## Result summary

- 4개 plan README에 라운드 1 callout 삽입 (tag-system / topic-generation-paths / newsletter-shelf / weekday-print-slots).
- ADR-0003 v3 (라운드 1 supersedes) — 기존 본문에 이미 반영되어 있어 추가 편집 없음.
- ADR-0005 → status `proposed → accepted`, 어휘 정합 (진열대/shelf/풀 → 미프린트 Newsletter 집합).
- ADR-0006 — 단일 슬롯 scope 명확화 subsection 추가.
- topic-generation-paths/README.md 전면 재작성: `SuggestedTopic.tags`, `availableTagPool` 제거. 모든 경로 default 태그 = `모든주제`.
- newsletter-shelf/* 어휘 sweep (sed): `진열대 모델 → lazy 보충 모델`, `진열대 → 미프린트 ...`.
- 폴더명 `newsletter-shelf/`, ADR 파일명 `0005-newsletter-shelf-lazy-generation.md`는 의도적으로 유지.
- sub-files(01-XX) 옛 어휘 잔여는 후속 정리 — 결정 사항은 README의 라운드 1 callout이 우선한다고 명문화.
- status.md / current-state.md / INDEX.md cross-cutting 갱신.

Original task brief follows below.

---


## Goal

Apply the **2026-04-19 라운드 1 상위 규칙** (defined in `docs/specs/mvp.md` §3 leading callout) to the bodies of the 4 active plans and 3 product ADRs so that they no longer contradict the spec. After this sweep, the planner can move each plan from `review`/`in-progress` toward `accepted` without further vocabulary debt.

## User-visible behavior

None directly. This is a documentation alignment task. Result: plan/ADR bodies match spec round-1 rules; status.md and current-state.md reflect the sweep.

## Round-1 rules to enforce (from specs/mvp.md §3 leading block)

1. **MVP slot construction = single slot** `[(모든주제, N장)]`. Multi-slot support is **deferred to public-release milestone**. Plan bodies must say so explicitly when discussing multi-slot.
2. **"진열대/Shelf/풀" is NOT a product concept.** Newsletter records that have not yet been printed must be referred to as "미프린트 Newsletter 레코드" (or equivalent neutral phrase). Remove "진열대", "shelf", "Shelf", "풀" wherever they refer to this concept.
3. **Claude does NOT participate in the tag system.** Remove all references to:
   - Claude auto-recommending tags
   - Claude auto-classifying topics into tags
   - Claude proposing tags in `SuggestedTopic.tags`
   - "새 태그 동의 프롬프트" (this rule is abolished)
   - "사용 누적 후 시스템이 추천" (auto-recommendation is out)
   Tag generation and tag attachment to a Topic are **entirely user-manual**.
4. **`자유주제` → `모든주제` rename.** Same concept, same invariant (every Topic must include this seed tag, user cannot edit/delete it). Rename in:
   - Korean prose
   - Quoted JSON/code examples (`"tag": "자유주제"` → `"tag": "모든주제"`)
   - Constant value (`"자유주제"` → `"모든주제"`) — but **leave the Kotlin constant identifier `FREE_TOPIC_TAG` unchanged in plan text**; the constant rename is a separate implementer item already in status.md.
5. **Topic creation → default tag is `모든주제` only.** No auto-recommendation, no auto-classification, no retroactive classification.

## Scope (files to edit)

Touch all and only the files below. Skim-read each before editing.

### A. tag-system/ + ADR-0003

- `docs/plans/tag-system/README.md`
- `docs/plans/tag-system/01-backend.md`
- `docs/plans/tag-system/02-ui-note.md`
- `docs/plans/tag-system/03-checklist.md`
- `docs/decisions/0003-tag-system-data-model.md`

Required changes:
- `자유주제` → `모든주제` everywhere in body text and code examples (preserve historical mentions of the rename if the file already states "이전 이름 `자유주제`는 폐기" — those stay).
- Remove invariant 4 in tag-system/README.md ("다른 태그들은 사용 누적 후 시스템이 추천하고 사용자가 채택하는 방식으로 자라난다") — replace with: "다른 태그들은 사용자가 수동으로 만들고 부여한다. 시스템(Claude 포함)은 태그를 자동 추천하지 않는다."
- Remove "신규 기능 '태그 추천'" follow-up note (`후속 영향 (planner 메모)` section in README) — replace with a neutral "후속 영향 없음 — 태그 자동 추천 기능은 라운드 1에서 폐기" line.
- Update Q5 acknowledgement: per round-1, Claude does not assign tags to auto-generated topics. Q4/Q5 in tag-system/README must be re-marked as resolved or rewritten.
- ADR-0003: bump status to `accepted v3` if currently older. Add a "Round-1 supersedes" note explaining: rename, Claude removal, manual-only tag attachment.
- `FREE_TOPIC_TAG` constant: leave the identifier in code samples but state once in README that the constant value is now `"모든주제"` and rename of the identifier itself is tracked in status.md.

### B. topic-generation-paths/

- `docs/plans/topic-generation-paths/README.md`
- `docs/plans/topic-generation-paths/01-background.md`
- `docs/plans/topic-generation-paths/02-backend.md`
- `docs/plans/topic-generation-paths/03-ui.md`
- `docs/plans/topic-generation-paths/04-checklist.md`

Required changes:
- `자유주제` → `모든주제`.
- Remove Claude-set tags from path table: in README, the path table must say (a) and (b) Claude calls produce only **title + sourceKeywordIds**, NOT tags. Default tag for all generated topics is `모든주제` (single tag), assigned in `TopicRepository.saveTopic` via `ensureFreeTopicTag` invariant. User attaches additional tags manually afterward.
- `SuggestedTopic.tags` field: remove from the data class in README and 02-backend.md. The `availableTagPool` parameter of `ClaudeTopicSuggester.suggest` must also be removed.
- Q5 (자동 생성 경로의 태그 부여 책임): mark resolved as "round-1 결정: Claude는 태그에 관여하지 않는다. 모든 경로에서 default = `모든주제` only".
- Direct-write path (c): keep "사용자가 태그를 수동 입력" — that's already round-1 compliant, but verify the UI section in 03-ui.md doesn't show Claude autocomplete/suggestion for tags.
- Update checklist 04-checklist.md to drop any Claude-tag steps.

### C. newsletter-shelf/ + ADR-0005

- `docs/plans/newsletter-shelf/README.md`
- `docs/plans/newsletter-shelf/01-background.md`
- `docs/plans/newsletter-shelf/02-backend.md`
- `docs/plans/newsletter-shelf/03-ui.md`
- `docs/plans/newsletter-shelf/04-infra.md`
- `docs/plans/newsletter-shelf/05-checklist.md`
- `docs/decisions/0005-newsletter-shelf-lazy-generation.md`

Required changes:
- Remove "진열대 / shelf / Shelf / SHELF / 풀" concept everywhere in body text. Replace with "미프린트 Newsletter 레코드" or "Newsletters DB의 미프린트 레코드 집합".
- Plan title: rename README heading `# 뉴스레터 진열대 모델 + 다중 주제 엮기` → `# 뉴스레터 lazy 보충 모델 + 다중 주제 엮기`.
- README summary frontmatter: `summary: "뉴스레터 진열대 모델 + 프린트 시점 lazy 2개 생성 ..."` → drop "진열대" wording.
- DO NOT rename the folder path `newsletter-shelf/` — that is too invasive for this sweep; leave the folder name and update only body vocabulary. Note this decision at the top of README.
- DO NOT rename ADR-0005 filename. Update title and body vocabulary only.
- Q6 of README: vocabulary fix; the Q itself becomes "미프린트 Newsletter 목록을 기존 NewsletterScreen에 확장할지, 별도 화면으로 분리할지" — option text follows.
- `자유주제` → `모든주제` in code examples (e.g., `Slot("자유주제", ...)` → `Slot("모든주제", ...)`).
- `Status = "generated"` (Notion property value) is a code-level identifier and may stay, but do **not** call the set "shelf"; call it "미프린트 집합" or "`generated` 상태 페이지".
- ADR-0005: bump status from `proposed` to `accepted` (planner accepts post-sweep). Add a "Round-1 vocabulary update" section noting "진열대/shelf/풀" removal and `자유주제` → `모든주제` rename.

### D. weekday-print-slots/ + ADR-0006

- `docs/plans/weekday-print-slots/README.md`
- `docs/plans/weekday-print-slots/01-background.md`
- `docs/plans/weekday-print-slots/02-data.md`
- `docs/plans/weekday-print-slots/03-scheduler.md`
- `docs/plans/weekday-print-slots/04-ui.md`
- `docs/plans/weekday-print-slots/05-checklist.md`
- `docs/decisions/0006-weekday-print-scheduling.md`

Required changes:
- `자유주제` → `모든주제` in code examples and prose.
- Add a leading callout in README and 01-background.md: "라운드 1 확정: MVP는 단일 슬롯 `[(모든주제, N장)]` 1경로로 검증한다. 다중 슬롯 (요일 × 서로 다른 태그 여러 개) 격리·순서·매칭 검증은 공개 배포 마일스톤으로 이월한다."
- Where data model / scheduler discusses multi-slot capability, **keep the structure** (요일 × 태그 유일성 등) but mark each multi-slot acceptance criterion as "공개 배포 이월 — MVP 검증 대상 아님".
- 05-checklist.md: any acceptance step that requires multiple slots in one weekday → mark as "이월" with a note.
- ADR-0006: stays `accepted`. Add a "Round-1 scope clarification" subsection: "MVP 판정 = 단일 슬롯. 다중 슬롯 코드 경로는 구조상 살아 있지만 MVP 1일 E2E 리허설에서는 검증되지 않는다."

### E. Cross-cutting updates (do these LAST, after A–D land)

- `docs/status.md`: change all 6 `검토대기` items in §"현재 진행 중" to either:
  - completed (move to `docs/status/archive.md`) if the sweep fully addresses them, OR
  - keep with updated wording and date `2026-04-26`.
  Specifically the ADR-0003 / ADR-0005 / plans/newsletter-shelf-rename / plans/tag-system-update / TagNormalizer-rename items: mark "round-1 doc sweep done 2026-04-26 — 코드 상수 rename은 implementer 후속" and shift the implementer code-rename line to remain active.
- `docs/context/current-state.md`: in §6 #1, mark candidate as "**done 2026-04-26**". Add a one-liner: "라운드 1 문서 정합 완료. 다음 후보는 #2 PrintWorker 입력 데이터 미스매치."
- Bump `updated:` frontmatter on every file touched to `2026-04-26`.

## Out of Scope

- Rewriting plans from scratch.
- Changing acceptance criteria beyond marking multi-slot items as 이월.
- Renaming folders or ADR filenames.
- Renaming Kotlin constants (`FREE_TOPIC_TAG`, etc.) — that lives in a separate implementer task already declared in status.md.
- Editing app code.
- Creating new ADRs.

## Forbidden Changes

- No source code edit (`app/**`).
- No new dependency.
- No folder rename.
- No ADR file rename.
- No removal of historical "이전 이름 `자유주제`는 폐기" mentions in spec/ADR rationale (those are deliberate history markers).
- No reduction of plan content beyond what round-1 explicitly invalidates.

## Acceptance Criteria

- [ ] `grep -r "자유주제" docs/` finds occurrences only in deliberately-historical contexts (rename markers, ADR rationale citing the prior name).
- [ ] `grep -ri "진열대\|shelf" docs/plans docs/decisions` returns zero matches in body prose (folder path `newsletter-shelf/` and filename `0005-newsletter-shelf-lazy-generation.md` are exceptions and may remain).
- [ ] No plan or ADR body claims "Claude 태그 자동 추천" / "Claude 자동 분류" / "사용 누적 후 시스템이 추천" as a live mechanism.
- [ ] `docs/plans/weekday-print-slots/README.md` contains a callout stating MVP = single slot, multi-slot deferred.
- [ ] `docs/status.md` reflects the sweep in dated entries.
- [ ] `docs/context/current-state.md` §6 #1 is marked done with date.
- [ ] All edited files have `updated: 2026-04-26` in their frontmatter.

## Verification Command Candidates

```sh
grep -rn "자유주제" docs/
grep -rni "진열대\|shelf" docs/plans docs/decisions
grep -rn "Claude.*태그.*추천\|태그.*자동.*추천\|자동 분류" docs/plans docs/decisions
grep -rn "updated: 2026-04-26" docs/
```

## Expected Implementer Output

- Changed file list.
- Per-file one-line summary (what kind of edit: rename / vocabulary / Claude-removal / scope clarification).
- Final grep verification output proving acceptance criteria.
- Any forbidden change attempted and skipped.
- Notes for verifier on edge cases encountered.
