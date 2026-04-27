# TASK-20260426-003 Implementer Result

Task ID: TASK-20260426-003
Status: IMPLEMENTED (build SKIPPED_ENVIRONMENT_NOT_AVAILABLE)

## Changed Files

- `app/src/main/java/com/dailynewsletter/service/NotionSetupService.kt` (+18 lines): Added `Tags` multi_select property with seed option `모든주제` (gray) to all 3 DB creation calls.
- `app/src/main/java/com/dailynewsletter/data/repository/KeywordRepository.kt` (+14 lines): Extended `addKeyword` to `addKeyword(text, type, tags: List<String>)`; added `Tags` multi_select to createPage payload; added `tags` mapping in `refreshKeywords`.
- `app/src/main/java/com/dailynewsletter/data/repository/TopicRepository.kt` (+11 lines): Added `import TagNormalizer`; extended `saveTopic` to `saveTopic(title, priorityType, sourceKeywordIds, tags: List<String>)`; `ensureFreeTopicTag(tags)` applied before Notion payload; `tags` mapped in `getTodayTopics`.
- `app/src/main/java/com/dailynewsletter/data/repository/NewsletterRepository.kt` (+50 lines): Extended `saveNewsletter` to `saveNewsletter(title, htmlContent, topicIds, pageCount, tags: List<String>)`; added `Tags` multi_select to createPage payload; `tags` mapped in `getNewsletters`; added new `findUnprintedNewsletterByTag(tagName: String): NewsletterUiItem?`.
- `app/src/main/java/com/dailynewsletter/ui/keyword/KeywordViewModel.kt` (+3 lines): Added `tags: List<String> = emptyList()` to `KeywordUiItem`; ViewModel `addKeyword` now passes `emptyList()` with TODO comment.
- `app/src/main/java/com/dailynewsletter/ui/topics/TopicsViewModel.kt` (+1 line): Added `tags: List<String> = emptyList()` to `TopicUiItem`.
- `app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterViewModel.kt` (+1 line): Added `tags: List<String> = emptyList()` to `NewsletterUiItem`.
- `app/src/main/java/com/dailynewsletter/service/TopicSelectionService.kt` (+3 lines): Added `tags = emptyList()` to `saveTopic` call + TODO comment.
- `app/src/main/java/com/dailynewsletter/service/NewsletterGenerationService.kt` (+3 lines): Added `emptyList()` positional arg to `saveNewsletter` call + TODO comment.
- `docs/plans/tag-system/README.md`: Steps 3, 4, 6 marked `[x]`; `consumed_by` entry added; `next_action` updated.
- `docs/status.md`: tag-system line updated to reflect completion + SKIPPED build status.

## Behavior Changed

### Step 3 (NotionSetupService seed)
All 3 `createDatabase` calls now include `"Tags"` as a `multi_select` property with one seed option: name=`모든주제`, color=`gray`.

### Step 4 (Repository tags read/write)
- `KeywordUiItem`, `TopicUiItem`, `NewsletterUiItem` each gain `tags: List<String> = emptyList()`.
- `KeywordRepository.addKeyword(text, type, tags)` — writes `Tags` multi_select to Notion on create; `refreshKeywords` reads `Tags` multi_select back.
- `TopicRepository.saveTopic(title, priorityType, sourceKeywordIds, tags)` — applies `TagNormalizer.ensureFreeTopicTag(tags)` before writing; `getTodayTopics` reads `Tags` back.
- `NewsletterRepository.saveNewsletter(title, htmlContent, topicIds, pageCount, tags)` — writes `Tags` multi_select; `getNewsletters` reads `Tags` back.
- `NewsletterRepository.findUnprintedNewsletterByTag(tagName)` — queries Notion with `multi_select.contains = tagName` AND `Status.equals = "generated"`, returns first result or null.

### Step 6 (call site compile fixes)
- `TopicSelectionService.selectAndSaveTopics` passes `tags = emptyList()` to `saveTopic` with TODO comment.
- `NewsletterGenerationService.generateAndSaveNewsletter` passes `emptyList()` as `tags` to `saveNewsletter` with TODO comment.
- `KeywordViewModel.addKeyword` passes `emptyList()` to `keywordRepository.addKeyword` with TODO comment.

## Acceptance Criteria Check

- [x] 3개 UI 모델에 `tags: List<String>` 필드가 있다.
- [x] `KeywordRepository.addKeyword`는 `tags: List<String>` 매개변수를 받는다.
- [x] `TopicRepository.saveTopic`은 `tags: List<String>` 매개변수를 받고, 내부에서 `ensureFreeTopicTag()`를 호출한 결과를 Notion에 보낸다.
- [x] `NewsletterRepository.saveNewsletter`는 `tags: List<String>` 매개변수를 받는다.
- [x] `NewsletterRepository.findUnprintedNewsletterByTag(tagName)` 메서드가 존재하고 `multi_select.contains` + `Status.equals("generated")` 쿼리를 사용한다.
- [x] `NotionSetupService.setupDatabases`가 3개 DB 모두 `Tags multi_select` + 시드 `모든주제`(gray)를 등록한다.
- [x] 6단계 임시 처리: `TopicSelectionService`·`NewsletterGenerationService` 호출이 `emptyList()` 전달 + TODO 주석.
- [x] ViewModel `addKeyword` 호출 지점도 `emptyList()` 전달 + TODO 주석 (TopicsViewModel/NewsletterViewModel은 `saveTopic`/`saveNewsletter`를 직접 호출하지 않으므로 추가 변경 불필요).
- [x] `docs/plans/tag-system/README.md` 진행률 3·4·6 `[x]` + `consumed_by` 항목 추가.
- [x] `docs/status.md` tag-system 항목 갱신.
- [ ] `./gradlew :app:assembleDebug`: **BUILD: SKIPPED_ENVIRONMENT_NOT_AVAILABLE** — Bash execution permission denied.
- [ ] `./gradlew :app:testDebugUnitTest`: **SKIPPED_ENVIRONMENT_NOT_AVAILABLE**

## Static Verification Grep Results

```
grep -n "ensureFreeTopicTag" TopicRepository.kt
→ 114:        val normalizedTags = TagNormalizer.ensureFreeTopicTag(tags)

grep -n "Tags" NotionSetupService.kt
→ 48:  "Tags" to NotionPropertySchema(  [Keywords DB]
→ 88:  "Tags" to NotionPropertySchema(  [Topics DB]
→ 121: "Tags" to NotionPropertySchema(  [Newsletters DB]

grep -rn "tags = emptyList()" service/ ui/
→ TopicSelectionService.kt:106:  tags = emptyList()
→ (NewsletterGenerationService uses positional emptyList())

grep -n "emptyList()" NewsletterGenerationService.kt
→ 134: newsletterRepository.saveNewsletter(title, fullHtml, topicIds, pages, emptyList())

grep -n "findUnprintedNewsletterByTag" NewsletterRepository.kt
→ 134: suspend fun findUnprintedNewsletterByTag(tagName: String): NewsletterUiItem?
```

## Forbidden Changes

- `data/tag/TagNormalizer.kt` — not modified (only imported in TopicRepository).
- `worker/*.kt` — not modified.
- `data/remote/notion/*.kt` — not modified (existing models `NotionMultiSelectValue`, `NotionMultiSelectFilter`, `NotionMultiSelectSchema` were already present from step 1).
- `build.gradle.kts`, `AndroidManifest.xml` — not modified.
- No new dependencies introduced.

## Notes for Verifier

**Step 7 manual E2E checks:**
1. Delete existing Notion DBs (or reset them), then trigger `NotionSetupService.setupDatabases()`. Confirm all 3 new DBs have a `Tags` column with `모든주제` as a pre-populated gray option.
2. Call `KeywordRepository.addKeyword("테스트키워드", "keyword", listOf("신규태그"))` — confirm Notion Keywords DB page has `Tags = [신규태그]`.
3. Call `TopicRepository.saveTopic("테스트주제", "direct", emptyList(), emptyList())` — confirm Notion Topics DB page has `Tags = [모든주제]` (invariant auto-fill).
4. Call `NewsletterRepository.findUnprintedNewsletterByTag("모든주제")` after generating a newsletter — should return first `generated` newsletter that has the `모든주제` tag.

**Key files for verifier:**
- `/app/src/main/java/com/dailynewsletter/data/repository/TopicRepository.kt` — lines 110–148 (saveTopic with ensureFreeTopicTag)
- `/app/src/main/java/com/dailynewsletter/data/repository/NewsletterRepository.kt` — lines 134–179 (findUnprintedNewsletterByTag)
- `/app/src/main/java/com/dailynewsletter/service/NotionSetupService.kt` — lines 44–56, 84–96, 117–129 (Tags seed in each DB)
