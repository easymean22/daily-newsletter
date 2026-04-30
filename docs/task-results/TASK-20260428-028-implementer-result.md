# Task Result: TASK-20260428-028

Task ID: TASK-20260428-028
Status: IMPLEMENTED

## Changed Files
- `app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterScreen.kt`: removed duplicate bottom print Row, added BackHandler, added navigationIcon with ArrowBack

## Behavior Changed
- Bottom Row (containing "목록으로" TextButton + "프린트" Button with `viewModel.printNewsletter`) removed from detail view.
- `BackHandler(enabled = selectedNewsletter != null)` added; sets `selectedNewsletter = null` on device back press.
- `TopAppBar.navigationIcon` added: shows ArrowBack `IconButton` when `selectedNewsletter != null`; tap sets `selectedNewsletter = null`.
- Print action remains only in the TopAppBar `actions` block (unchanged).

## Tests Added or Updated
None — UI-only change; no ViewModel or repository logic touched.

## Commands Run
- `grep -c "printNewsletter(" ...NewsletterScreen.kt`
  - result: `1` (exactly one call site remains, in IconButton/TopAppBar actions)
- `grep -n "BackHandler|navigationIcon|selectedNewsletter|ArrowBack" ...NewsletterScreen.kt`
  - result: imports at lines 6,14; BackHandler at lines 36-38; navigationIcon at lines 68-74; all selectedNewsletter state usages present; ArrowBack icon at line 71
- `./gradlew :app:assembleDebug`
  - result: SKIPPED_ENVIRONMENT_NOT_AVAILABLE (Bash permission denied in this session)

## Notes for Verifier
- Important file: `app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterScreen.kt`
- Verify: `grep -c "printNewsletter(" app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterScreen.kt` must return `1`
- Verify: No `Row` containing both "목록으로" and a print `Button` should exist in the file
- Suggested manual check: Tap a newsletter card → only one print icon in TopAppBar (top-right); no print button below WebView. Device back or arrow navigates to grid.
- No new dependencies added; `activity-compose` was already in `app/build.gradle.kts` (line 90).
