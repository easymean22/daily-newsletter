# Task Result: TASK-20260429-035 retry-ui-feedback

Task ID: TASK-20260429-035
Status: IMPLEMENTED

## Changed Files

- `app/src/main/java/com/dailynewsletter/service/GeminiRetry.kt`: Added `sealed class RetryEvent { data class Retrying(...) }`, `MutableSharedFlow<RetryEvent>` with exposed `val events: SharedFlow<RetryEvent>`, and `_events.tryEmit(...)` call before `delay()` in the retry loop.
- `app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterViewModel.kt`: Added `data class Retrying(val message: String) : ManualGenStatus()` variant; added `init` coroutine collecting `GeminiRetry.events` and updating state to `ManualGenStatus.Retrying(msg)` when current status is Running or Retrying. Added imports for `GeminiRetry` and `RetryEvent`.
- `app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterScreen.kt`: Added `is ManualGenStatus.Retrying -> { snackbarHostState.showSnackbar(status.message) }` branch in `LaunchedEffect`; updated `GenerateNewsletterSheet` call site to pass `isGenerating = Running || Retrying` and `retryMessage`; updated composable signature to accept `retryMessage: String? = null`, rendering it as a `Text` above the action row and changing button label to "재시도 중..." when `retryMessage != null`.

## Behavior Changed

- During manual newsletter generation, if Gemini returns a 503/502/504 and `GeminiRetry` retries, the UI now:
  1. Shows a Snackbar with "혼잡으로 재시도 중입니다. 잠시만 기다려 주세요 (1/3)" (or 2/3, 3/3).
  2. Shows the same message as inline text inside the open `GenerateNewsletterSheet` bottom sheet (if it's still open).
  3. Changes the button label from "생성 중..." to "재시도 중...".
- On final success/failure the existing Running→Success/Failed path overwrites Retrying as before.
- All TASK-033 lazy loading UI, TASK-028 BackHandler/navigationIcon, and all existing `ManualGenStatus` variants are preserved.

## Tests Added or Updated

None. The Task Brief did not request unit tests for this change, and the logic is integration-level (SharedFlow + coroutine) that requires a test coroutine scope. No new test files added.

## Commands Run

- `grep -n "RetryEvent|MutableSharedFlow|class Retrying" GeminiRetry.kt` — all 3 identifiers found at lines 5, 10, 11, 16, 23, 24, 42.
- `grep -rn "ManualGenStatus.Retrying|GeminiRetry.events" ui/newsletter/` — found in both ViewModel (lines 67, 72, 75) and Screen (lines 60, 168, 171).
- `./gradlew :app:assembleDebug` — SKIPPED_ENVIRONMENT_NOT_AVAILABLE (Bash permission denied).

## Notes for Verifier

- Important files:
  - `app/src/main/java/com/dailynewsletter/service/GeminiRetry.kt`
  - `app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterViewModel.kt`
  - `app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterScreen.kt`
- Suggested checks:
  - Run `./gradlew :app:assembleDebug` to confirm compilation.
  - Confirm `ManualGenStatus.Retrying` is handled in all `when` exhaustive branches elsewhere (none found — `ManualGenStatus` is only consumed in `NewsletterScreen`).
  - The `LaunchedEffect(state.manualGenStatus)` reacts to each Retrying state change because the `message` string includes the attempt number, making each emission a new distinct state.
- Known limitations:
  - The Snackbar `showSnackbar` for Retrying is a suspending call; if the queue is backed up, the message may be delayed. This is acceptable for the current scope.
  - `GeminiRetry.events` is a singleton SharedFlow — if multiple viewmodels collect simultaneously, all would receive the event. Only `NewsletterViewModel` currently collects it.
