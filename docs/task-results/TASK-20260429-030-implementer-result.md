# Task Result: TASK-20260429-030 Keyword Overhaul

Task ID: TASK-20260429-030
Status: IMPLEMENTED

## Changed Files

- `app/src/main/java/com/dailynewsletter/data/repository/KeywordRepository.kt`
  - `addKeyword(text, type, tags)` → `addKeyword(text, tags)` — type param removed, hardcoded "keyword" sent to Notion
  - `buildTitle()` helper: truncates to 50 chars + "..." when text > 80 chars
  - `buildBodyBlocks()` helper: returns a single paragraph NotionBlock for long text; null for short text
  - `addKeyword` passes `children = buildBodyBlocks(text)` in CreatePageRequest
  - `refreshKeywords` maps `page.createdTime` to `KeywordUiItem.createdTime`
  - `getAllTags()`: union of tags from cached keywords + comma-separated list in SettingsRepository under key `all_tags`
  - `persistTag(tag)`: stores new tag name in SettingsRepository
  - `removeTagFromAllKeywords(tag)`: removes from settings + patches all affected Notion pages via updatePage

- `app/src/main/java/com/dailynewsletter/ui/keyword/KeywordViewModel.kt`
  - `KeywordUiItem` gains `createdTime: String? = null` field
  - `KeywordUiState` gains `availableTags: Set<String>` and `selectedTagFilter: String?`
  - `addKeyword(text, type, tags)` → `addKeyword(text, tags)` — type param removed
  - New functions: `loadTags()`, `selectTagFilter(tag?)`, `addNewTag(tag)`, `removeTag(tag)`
  - `loadKeywords` applies `selectedTagFilter` client-side when building the keyword list

- `app/src/main/java/com/dailynewsletter/ui/keyword/KeywordScreen.kt`
  - `TypeSelector` composable removed; `newType` state removed from add-sheet
  - `TagFilterBar` composable added: `LazyRow` of `FilterChip` with `combinedClickable` for long-press delete, trailing `IconButton` for add
  - Tag filter bar placed below TopAppBar inside `Column`
  - Add-sheet: `TypeSelector` call removed; `TagInput` extended with quick-select chips from `availableTags`
  - `KeywordCard`: shows `formatKeywordTime(keyword.createdTime)` below title; shows tag chips (up to 3); removed type chip
  - Two new `AlertDialog`s: add-tag and delete-tag confirmation
  - `formatKeywordTime()` helper: parses ISO-8601 OffsetDateTime → "yyyy-MM-dd HH:mm"

## Behavior Changed

- Type selection (keyword vs memo) removed from add dialog and card display.
- Long text (>80 chars) stored with truncated title in Notion and full text in page body paragraph block.
- Keyword cards show creation time.
- Tag filter bar appears at top of screen — click to filter, long-press to delete with confirmation dialog.
- "+" icon opens add-tag dialog that persists tag to SettingsRepository immediately (no phantom keywords).

## Tests Added or Updated

None — no test files exist for this module in the current codebase. Unit test coverage could be added for `buildTitle`, `buildBodyBlocks`, `getAllTags`, `removeTagFromAllKeywords`.

## Commands Run

- Build: SKIPPED_ENVIRONMENT_NOT_AVAILABLE (Bash permission denied)
- Grep verifications: PASSED — all acceptance criteria symbols confirmed present

## Notes for Verifier

- `NotionPage.createdTime` already existed in `NotionModels.kt` line 115 — no model change needed.
- `SettingsEntity.kt` not changed — `KEY_ALL_TAGS = "all_tags"` is defined locally as a private const in KeywordRepository.
- `FilterChip` with `combinedClickable` modifier: the `onClick` in FilterChip and in `combinedClickable` are both set to the same handler — this is intentional; `combinedClickable` intercepts long-press while regular click still flows through.
- `selectTagFilter` triggers a `refreshKeywords()` coroutine to re-apply the new filter; the filter is applied client-side in the `collect` lambda within `loadKeywords`.
- Suggested build check: `./gradlew :app:assembleDebug`
- Suggested grep check: `grep -n "getAllTags\|removeTagFromAllKeywords\|availableTags\|selectedTagFilter\|createdTime" app/src/main/java/com/dailynewsletter/{data/repository/KeywordRepository.kt,ui/keyword/KeywordViewModel.kt,ui/keyword/KeywordScreen.kt}`
