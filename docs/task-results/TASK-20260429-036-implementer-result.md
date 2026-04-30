## Implementation Summary

Task ID: TASK-20260429-036
Status: IMPLEMENTED

## Changed Files

- `app/src/main/java/com/dailynewsletter/service/GeminiRetry.kt`: Changed IllegalStateException message from "Gemini가 일시적으로 혼잡합니다. 잠시 후 다시 시도해 주세요" to "잠시 후 다시 시도해 주세요".
- `app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterViewModel.kt`: Changed Retrying message from "혼잡으로 재시도 중입니다. 잠시만 기다려 주세요 (N/M)" to "잠시 후 다시 시도하고 있어요 (N/M)". Changed notification title from "생성 실패" to "생성 미완료".
- `app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterScreen.kt`: Four changes:
  1. Removed Failed/Retrying branches from LaunchedEffect Snackbar handler; Success Snackbar now shows "뉴스레터가 추가되었습니다".
  2. Added AlertDialog (title "생성을 마치지 못했어요", text "잠시 후 다시 시도해 주세요") shown when manualGenStatus is Failed; dismissed via viewModel.clearManualGenStatus().
  3. Added persistent banner (Surface + Row with 16dp CircularProgressIndicator) at top of Scaffold content Column — visible during Running ("뉴스레터 생성 중...") and Retrying (status.message) regardless of BottomSheet state.
  4. BottomSheet generate button label changed from "재시도 중..." to "잠시만요..." when retryMessage is present.

## Behavior Changed

- 503 retry progress is now shown in a persistent banner at the top of NewsletterScreen, not only inside the BottomSheet Snackbar.
- Final exhausted-retry failure shows a modal AlertDialog instead of a Snackbar that could be missed.
- No negative wording ("혼잡", "실패") remains in user-visible strings for the newsletter generation flow.

## Tests Added or Updated

None — no new test files added. Existing unit tests unaffected (ViewModel logic unchanged; only string literals updated).

## Commands Run

- `grep -rn "혼잡" ...` → 0 results
- `grep -rn "실패" ... | grep -v comments` → 0 results
- `grep -n "AlertDialog|생성 중|잠시 후|생성을 마치지" NewsletterScreen.kt` → 5 matches confirming all required strings present
- `./gradlew :app:assembleDebug` → SKIPPED_ENVIRONMENT_NOT_AVAILABLE (permission denied)

## Notes for Verifier

- Important files:
  - `/app/src/main/java/com/dailynewsletter/service/GeminiRetry.kt` (line 37)
  - `/app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterViewModel.kt` (lines 74, 158)
  - `/app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterScreen.kt` (lines 51-72 for LaunchedEffect+AlertDialog, lines 113-140 for banner)
- Suggested checks:
  - Build with `./gradlew :app:assembleDebug`.
  - Confirm `혼잡` and `실패` (user-facing) are 0 in the three owned files.
  - Confirm banner appears above the grid and detail view (both branches inside Column).
  - Confirm AlertDialog title/text strings present.
- Known limitations:
  - Banner is placed inside `Modifier.padding(padding)` Column, so it sits below the TopAppBar as intended.
  - The `background` import remains in the file from the original code (used in NewsletterCard) — not removed.
