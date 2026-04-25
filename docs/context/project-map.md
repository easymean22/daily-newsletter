---
updated: 2026-04-26
status: accepted
owner: planner
summary: "Live map of the Android codebase — packages, classes, and responsibilities, contrasted with the MVP intent."
refs:
  - docs/specs/mvp.md
  - CLAUDE.md
---

# Project Map

This file is a **live snapshot** of where things actually live in the codebase, written for fast onboarding by any agent. It is not a design proposal — for design decisions see `docs/decisions/` and `docs/plans/`. For "what is built vs unbuilt vs broken" see [current-state.md](./current-state.md).

The codebase is an Android app (Kotlin, JVM 17, Jetpack Compose, Hilt, Room, Retrofit). Application id `com.dailynewsletter`, `minSdk = 26`, `compileSdk = targetSdk = 34`. Tests are absent (`app/src/test`, `app/src/androidTest` directories do not exist).

## 1. Top-level layout

```
app/src/main/java/com/dailynewsletter/
  DailyNewsletterApp.kt   ← Application class + Hilt + WorkManager Configuration.Provider
  MainActivity.kt         ← single Activity, hosts Compose root
  data/
    local/                ← Room (settings k/v only)
    remote/               ← Retrofit clients (Notion + Claude)
    repository/           ← thin wrappers over Notion + local settings
    tag/                  ← TagNormalizer (just-shipped)
  di/                     ← Hilt modules (App + Network)
  service/                ← orchestrators that compose repos + Claude + IPP + PDF
  ui/                     ← Compose screens + ViewModels per feature
  worker/                 ← WorkManager workers + WorkScheduler
```

Notable supporting facts (from `CLAUDE.md`):
- The default `WorkManagerInitializer` is **explicitly removed** in `AndroidManifest.xml` via `tools:node="remove"` so Hilt's `HiltWorkerFactory` can take over. Do not re-add it.
- All workers use `@HiltWorker` + `@AssistedInject`. Enqueue path is `WorkScheduler.scheduleAll()`.

## 2. Application + DI

| File | Responsibility |
|---|---|
| `DailyNewsletterApp.kt` | `Application`, implements `Configuration.Provider`, injects `HiltWorkerFactory`. Declares notification channels (`CHANNEL_TOPICS`, `CHANNEL_PRINT`). |
| `di/AppModule.kt` | Hilt application-level bindings (Room, repos, services). |
| `di/NetworkModule.kt` | Provides two named Retrofit clients: `@Named("notion")` for `https://api.notion.com/`, `@Named("claude")` for `https://api.anthropic.com/`. Shares one `OkHttpClient` with `HttpLoggingInterceptor.Level.BODY` (security concern, see current-state.md). |

There is also a **Compose-level** entry called `DailyNewsletterApp()` in `ui/DailyNewsletterApp.kt` — this is a **different symbol** from the Application class above. Do not confuse them.

## 3. Local data (Room — settings only)

| File | Responsibility |
|---|---|
| `data/local/AppDatabase.kt` | Single Room database. Holds only `settings` table. |
| `data/local/dao/SettingsDao.kt` | DAO for k/v get/set/observe. |
| `data/local/entity/SettingsEntity.kt` | k/v entity. Defines key constants: `KEY_NOTION_API_KEY`, `KEY_NOTION_PARENT_PAGE_ID`, `KEY_CLAUDE_API_KEY`, `KEY_PRINTER_IP`, `KEY_PRINTER_EMAIL`, `KEY_PRINT_TIME_HOUR/MINUTE`, `KEY_NEWSLETTER_PAGES`, `KEY_KEYWORDS_DB_ID`, `KEY_TOPICS_DB_ID`, `KEY_NEWSLETTERS_DB_ID`. |
| `data/repository/SettingsRepository.kt` | Typed accessors over the DAO. Defaults: print time `07:00`, newsletter pages `2`. Notion DB IDs are nullable until first setup. |

Important: Room database has **no other tables**. Keywords / Topics / Newsletters all live remotely in Notion. There is no local cache.

## 4. Remote data — Notion + Claude

### 4.1 Retrofit interfaces

| File | Responsibility |
|---|---|
| `data/remote/notion/NotionApi.kt` | Retrofit interface for Notion: createDatabase, createPage, queryDatabase, updatePage, deleteBlock, getBlockChildren. |
| `data/remote/notion/NotionModels.kt` | DTOs covering pages, properties (title/select/multi_select/relation/date/number/created_time), filter/sort, parent type. Includes `NotionPropertySchema` for DB creation. |
| `data/remote/claude/ClaudeApi.kt` | Retrofit interface for Anthropic Messages API. |
| `data/remote/claude/ClaudeModels.kt` | DTOs for Claude requests/responses. |

### 4.2 Tag normalization (just-shipped)

| File | Responsibility |
|---|---|
| `data/tag/TagNormalizer.kt` | `normalize(input)` = trim + lowercase + whitespace squash. `ensureFreeTopicTag(list)` enforces invariant ("every Topic must include the seed tag"). **Currently still hard-codes `"자유주제"` (`FREE_TOPIC_TAG`) — the rename to `"모든주제"` is queued in status.md.** |

### 4.3 Repositories

All three are thin Notion wrappers — **no local cache**, all reads go to Notion every time. They depend on `SettingsRepository` for the API key + DB ID.

| File | Responsibility | Notable surface |
|---|---|---|
| `data/repository/KeywordRepository.kt` | Keywords CRUD against the Notion `Keywords` DB. Holds an in-memory `MutableStateFlow<List<KeywordUiItem>>` that is refreshed on every mutation. Soft-delete via `Status = "deleted"`. | `observeKeywords()`, `refreshKeywords()`, `addKeyword(text, type)`, `deleteKeyword(id)`, `toggleResolved(id)`, `getPendingKeywords()`, `cleanupResolvedKeywords()`. |
| `data/repository/TopicRepository.kt` | Topics CRUD against the Notion `Topics` DB. **Today's topics** filter on `Date == today`. **Past topics** sort by `Date desc, pageSize=100` — used by Claude prompt for de-duplication. Has a **manual setter** `setTopicRepository(...)` injected via `TopicSelectionService` to break a circular DI dep — see §6. | `getTodayTopics()`, `getAllPastTopicTitles()`, `regenerateTopics()`, `updateTopicTitle()`, `deleteTopic()`, `saveTopic(title, priorityType, sourceKeywordIds)`. |
| `data/repository/NewsletterRepository.kt` | Newsletters CRUD against the Notion `Newsletters` DB. `printNewsletter(id)` resolves HTML → calls `PrintService.printHtml(...)` → flips status to `printed`. | `getNewsletters()`, `saveNewsletter(title, html, topicIds, pageCount)`, `updateNewsletterStatus(id, status)`, `printNewsletter(id)`. |

Notes for redesign awareness:
- `TopicRepository.saveTopic` does **not** accept tags today. Tag-system plan adds the parameter and routes through `TagNormalizer.ensureFreeTopicTag(...)`.
- `TopicRepository` directly depends on `TopicSelectionService` (via `regenerateTopics()`), which is itself the seed of the cycle.
- `NewsletterRepository.getNewsletters()` does N+1 `getBlockChildren` calls — one per page — for HTML preview. Performance note, not yet a bug.
- Keywords have no tag column; only Topics + Newsletters DB schemas have the tag-system axis (per ADR-0003).

## 5. Notion DB schema — what `NotionSetupService` actually creates

`service/NotionSetupService.setupDatabases()` creates exactly 3 sibling DBs under a parent page:

| DB | Properties (created today) | Comment vs. spec |
|---|---|---|
| Keywords | `Title (title)`, `Type (select: keyword/memo)`, `Status (select: pending/resolved/deleted)`, `Resolved Date (date)`, `Created At (created_time)` | No tag column. ADR-0003 says Keywords also gets `Tags multi_select`, not yet wired. |
| Topics | `Title`, `Source Keywords (relation→Keywords)`, `Priority Type (select: direct/prerequisite/peripheral)`, `Status (select: selected/read/modified)`, `Date (date)` | Topic statuses do **not yet include `consumed`** that the lazy-newsletter ADR requires. No `Tags multi_select`. |
| Newsletters | `Title`, `Date`, `Topics (relation→Topics)`, `Status (select: generated/printed/failed)`, `Page Count (number)` | No `Tags multi_select`. |

Implication: tag-system plan steps 3+ rewrite `NotionSetupService` to seed `Tags` multi_select on all three DBs (with `자유주제` → `모든주제` after rename).

## 6. Service layer (orchestrators)

| File | Responsibility | Dependencies |
|---|---|---|
| `service/NotionSetupService.kt` | One-shot — creates the 3 DBs, persists their IDs into `SettingsEntity.KEY_*_DB_ID`. Idempotent: skips if `KEY_KEYWORDS_DB_ID` is already set. | NotionApi, SettingsRepository |
| `service/TopicSelectionService.kt` | Builds a Claude prompt from pending Keywords + past Topic titles, parses JSON `[{title, priorityType, sourceKeywordIds, reason}]`, saves each via `TopicRepository.saveTopic`. | ClaudeApi, KeywordRepository, SettingsRepository, **TopicRepository (set later via `setTopicRepository`)** |
| `service/NewsletterGenerationService.kt` | Loads today's Topics → builds Claude HTML prompt with target char count (`pages × 1800`) → wraps response in a fixed CSS shell → `NewsletterRepository.saveNewsletter(...)`. | ClaudeApi, TopicRepository, NewsletterRepository, SettingsRepository |
| `service/PdfService.kt` | HTML → PDF on-device. Used before any print path. | (Android PrintAttributes / WebView) |
| `service/PrintService.kt` | Reads `printerIp` / `printerEmail` from settings. If IP set → IPP over HTTP (manually-assembled IPP request bytes — no IPP library in dependencies). If email set → throws `UnsupportedOperationException` (ePrint stub). | PdfService, SettingsRepository |

**Circular-dep workaround** lives between `TopicSelectionService` and `TopicRepository`. The current code uses a manual setter `TopicSelectionService.setTopicRepository(...)` to defer wiring. The topic-generation-paths plan proposes deleting `TopicSelectionService` entirely and replacing it with a stateless `ClaudeTopicSuggester` + ViewModel orchestration (cycle dissolved).

## 7. Workers + scheduling

`worker/WorkScheduler.scheduleAll()` enqueues four `UniquePeriodicWork` items, all with `ExistingPeriodicWorkPolicy.UPDATE` and `NetworkType.CONNECTED`. Initial delays are derived from a **single** print time stored in `SettingsEntity` (`KEY_PRINT_TIME_HOUR/MINUTE`):

| Worker | When | Calls | Notification |
|---|---|---|---|
| `DailyTopicWorker` | T − 2h | `TopicSelectionService.selectAndSaveTopics()` | `CHANNEL_TOPICS` (id 1001) |
| `NewsletterWorker` | T − 30m | `NewsletterGenerationService.generateAndSaveNewsletter()` | none |
| `PrintWorker` | T (print time) | `NewsletterRepository.printNewsletter(newsletterId)` (id from `inputData`) | `CHANNEL_PRINT` (id 1002) |
| `CleanupWorker` | 00:00 | `KeywordRepository.cleanupResolvedKeywords()` | none |

Friction with the spec (see [current-state.md](./current-state.md) for full risk list):
- The spec re-defines the pipeline as **user-action driven** (keyword input / manual generate / direct write) plus **lazy generation at print time**. The current 4-worker time chain is a different pipeline. `DailyTopicWorker` and `NewsletterWorker` are slated for removal; `PrintWorker` is slated to dispatch via a new `PrintOrchestrator.runForToday()`.
- `PrintWorker` reads `newsletter_id` from `inputData` but `WorkScheduler` schedules it as a `PeriodicWorkRequest` that **never sets `newsletter_id`**, so the worker today returns `Result.failure()` immediately. This means the print path has never been exercised end-to-end via the scheduler — manual `printNewsletter` from the UI is the only working path.
- `CleanupWorker` is explicitly out of MVP scope per spec §7.

## 8. UI (Compose)

Single `MainActivity` hosts `DailyNewsletterApp()` (Compose). Bottom-bar Navigation with four destinations.

| Feature dir | Screen | ViewModel | What it does |
|---|---|---|---|
| `ui/keyword/` | `KeywordScreen` | `KeywordViewModel` | List + add (BottomSheet) + swipe-delete + toggle resolved + filter chips (all/pending/resolved). Type chooser: keyword / memo. |
| `ui/topics/` | `TopicsScreen` | `TopicsViewModel` | Today's topics list, per-card edit + delete, top-bar refresh icon → `regenerateTopics()`. |
| `ui/newsletter/` | `NewsletterScreen` | `NewsletterViewModel` | List of newsletters; tap a card → WebView preview; print button → `printNewsletter(id)`. |
| `ui/settings/` | `SettingsScreen` | `SettingsViewModel` | Notion key + parent page ID, Claude key, printer IP / ePrint email, single daily print time (TimePicker dialog), pages slider (1–5). All values persisted via `SettingsViewModel.updateSetting(key, value)`. |
| `ui/theme/` | `Theme.kt` | — | Material 3 theme. |
| `ui/DailyNewsletterApp.kt` | Compose entry — Scaffold + NavigationBar | — | Starts at `Keywords`. |

User-facing strings are Korean (per CLAUDE.md).

## 9. Cross-cutting / infra

- **Logging**: `OkHttpClient` has `HttpLoggingInterceptor` at `Level.BODY`. This logs Notion + Claude API keys + full payloads. Flagged for the (still-unstarted) `release-hardening` track.
- **Error UX**: throwing `IllegalStateException` strings are exposed via ViewModel state. There is no centralized error handling.
- **No tests**: `app/src/test` and `app/src/androidTest` directories do not exist.

## 10. Build

```
./gradlew assembleDebug
./gradlew installDebug
./gradlew lint     # AGP lint only — no ktlint/detekt
./gradlew clean
```

`local.properties → sdk.dir` must point to a valid Android SDK. ktlint/detekt are not configured.

## 11. How this map evolves

Update this file when any of the following changes:
- A package, class, or DI binding is added / removed / renamed.
- A Notion DB schema property is added or removed.
- A Worker is added, removed, or its trigger changes.
- A Compose screen is added, removed, or renamed.
- The build / DI / WorkManager wiring rules change.

When updating, also bump `updated:` and, if the change shifts overall posture, mirror the impact in [current-state.md](./current-state.md).
