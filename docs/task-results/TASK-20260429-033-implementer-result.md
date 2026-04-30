## Implementation Summary

Task ID: TASK-20260429-033
Status: IMPLEMENTED

## Changed Files
- `app/src/main/java/com/dailynewsletter/data/repository/NewsletterRepository.kt`: Removed `getBlockChildren` + `blocksToHtml` calls from `getNewsletters()`. List items now returned with `htmlContent = null`. `getNewsletter(id)` unchanged.
- `app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterViewModel.kt`: Added `loadingDetailIds: Set<String> = emptySet()` to `NewsletterUiState`. Added `loadNewsletterContent(id)` — noop if already loaded or in-progress; fetches via `getNewsletter(id)` and patches the matching item's `htmlContent` in state.
- `app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterScreen.kt`: Introduced `currentItem` derived from `state.newsletters.find { it.id == sel.id }` to avoid stale reference. Card tap calls `viewModel.loadNewsletterContent(newsletter.id)` before setting `selectedNewsletter`. Detail view shows `CircularProgressIndicator` + "본문 로드 중..." when `htmlContent == null`, WebView otherwise. Print `IconButton` has `enabled = item.htmlContent != null`. BackHandler and navigationIcon from TASK-028 preserved.

## Behavior Changed
- Grid load: single GET (no block fetches) — fast list display.
- Card tap: triggers background `getNewsletter(id)` fetch; detail shows spinner until HTML arrives; re-tap on already-loaded card is instant (noop guard in ViewModel).
- Print button: disabled while `htmlContent` is null.

## Tests Added or Updated
- None added (no existing unit test infrastructure for ViewModel in this module; Brief did not require test additions).

## Commands Run
- Grep: `grep -n "getBlockChildren" .../NewsletterRepository.kt`
  - result: lines 164, 215, 264 only — all in `getNewsletter()`, `findUnprintedByTagAndPages()`, `findUnprintedNewsletterByTag()`. Zero hits in `getNewsletters()`. Acceptance criterion met.
- Grep: `grep -rn "loadNewsletterContent\|loadingDetailIds" .../newsletter/`
  - result: `NewsletterViewModel.kt` lines 43, 95, 98, 100, 108, 116 and `NewsletterScreen.kt` line 154. All references present. Acceptance criterion met.
- Build: `./gradlew :app:assembleDebug` — SKIPPED_ENVIRONMENT_NOT_AVAILABLE (Bash permission denied in this session).

## Notes for Verifier
- Important files:
  - `app/src/main/java/com/dailynewsletter/data/repository/NewsletterRepository.kt` (lines 37–64)
  - `app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterViewModel.kt` (lines 38–43, 95–120)
  - `app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterScreen.kt` (lines 64–158)
- Suggested checks:
  - Run `./gradlew :app:assembleDebug` to confirm compilation.
  - Verify `getBlockChildren` is called 0 times within `getNewsletters()` specifically (not just overall count).
  - Verify `currentItem` replaces all former `selectedNewsletter` references in Scaffold/detail branch.
- Known limitations:
  - `loadingDetailIds` is maintained in ViewModel state but not surfaced in the UI (brief did not require per-card grid indicators; spinner is in detail view only).
  - Cache is in-memory only (per brief: Room cache is out of scope).
