# Task Result: TASK-20260426-009 Claude → Gemini API Migration

Task ID: TASK-20260426-009
Status: IMPLEMENTED

## Changed Files

### Created (3)
- `app/src/main/java/com/dailynewsletter/data/remote/gemini/GeminiApi.kt`
  - Retrofit interface: `@POST("v1beta/models/{model}:generateContent")` with `@Header("x-goog-api-key")` and `@Path("model")`
- `app/src/main/java/com/dailynewsletter/data/remote/gemini/GeminiModels.kt`
  - `GeminiRequest(contents, generationConfig)`, `GeminiContent(role, parts)`, `GeminiPart(text)`, `GeminiGenerationConfig(maxOutputTokens, temperature)`, `GeminiResponse(candidates)`, `GeminiCandidate(content, finishReason)`
- `app/src/main/java/com/dailynewsletter/service/GeminiTopicSuggester.kt`
  - Same signature as old ClaudeTopicSuggester: `suggest(pendingKeywords, pastTopicTitles): List<SuggestedTopic>`
  - Reads `settingsRepository.getGeminiApiKey()`, calls `geminiApi.generateContent(apiKey, model="gemini-2.5-flash", request)`, extracts `candidates[0].content.parts[0].text`

### Modified (7)
- `app/src/main/java/com/dailynewsletter/di/NetworkModule.kt`: replaced `@Named("claude")` Retrofit (base `https://api.anthropic.com/`) + `provideClaudeApi()` with `@Named("gemini")` Retrofit (base `https://generativelanguage.googleapis.com/`) + `provideGeminiApi()`
- `app/src/main/java/com/dailynewsletter/data/local/entity/SettingsEntity.kt`: `KEY_CLAUDE_API_KEY = "claude_api_key"` → `KEY_GEMINI_API_KEY = "gemini_api_key"`
- `app/src/main/java/com/dailynewsletter/data/repository/SettingsRepository.kt`: `getClaudeApiKey()` → `getGeminiApiKey()`
- `app/src/main/java/com/dailynewsletter/ui/settings/SettingsViewModel.kt`: `SettingsUiState.claudeApiKey` → `geminiApiKey`, `KEY_CLAUDE_API_KEY` → `KEY_GEMINI_API_KEY` (×2 in combine block), `isSetupComplete` guard updated
- `app/src/main/java/com/dailynewsletter/ui/settings/SettingsScreen.kt`: section title "Claude AI 연동" → "Gemini AI 연동", field label "Claude API Key" → "Gemini API Key", key constant updated
- `app/src/main/java/com/dailynewsletter/service/NewsletterGenerationService.kt`: injected `GeminiApi` instead of `ClaudeApi`, calls `geminiApi.generateContent(...)` with Gemini request/response model, reads `getGeminiApiKey()`
- `app/src/main/java/com/dailynewsletter/ui/keyword/KeywordViewModel.kt`: injected `GeminiTopicSuggester` instead of `ClaudeTopicSuggester`, error string "Claude API 키" → "Gemini API 키"

### Deleted (3)
- `app/src/main/java/com/dailynewsletter/data/remote/claude/ClaudeApi.kt` — removed
- `app/src/main/java/com/dailynewsletter/data/remote/claude/ClaudeModels.kt` — removed
- `app/src/main/java/com/dailynewsletter/service/ClaudeTopicSuggester.kt` — removed
- `app/src/main/java/com/dailynewsletter/data/remote/claude/` directory — removed

### Comment-only fix (not-owned file, brief-permitted)
- `app/src/main/java/com/dailynewsletter/ui/topics/TopicsViewModel.kt` line 61: TODO comment "ClaudeTopicSuggester" → "GeminiTopicSuggester" (required for grep-0 criterion)

## Behavior Changed
- Settings screen shows "Gemini AI 연동" section with "Gemini API Key" field
- Topic suggestion and newsletter generation now call Gemini API (`gemini-2.5-flash`) via `x-goog-api-key` header
- `isSetupComplete` guard now checks `KEY_GEMINI_API_KEY` instead of `KEY_CLAUDE_API_KEY`
- Error snackbar shows "Gemini API 키를 먼저 설정해주세요" when key is missing

## Tests Added or Updated
None. No existing tests for these files; brief marks test creation optional.

## Commands Run
- Build: SKIPPED_ENVIRONMENT_NOT_AVAILABLE (Bash permission denied for gradlew)

## Grep Acceptance Results
1. `grep -r ClaudeApi app/src/main` → 0 matches
2. `grep -r claude_api_key app/src/main` → 0 matches
3. `grep -r ClaudeTopicSuggester app/src/main` → 0 matches (after TODO comment fix)
4. `grep -rn "x-goog-api-key" app/src/main` → 1 match in `GeminiApi.kt:12`
5. New files present: `GeminiApi.kt`, `GeminiModels.kt`, `GeminiTopicSuggester.kt` — confirmed
6. Deleted files absent: `ClaudeApi.kt`, `ClaudeModels.kt`, `ClaudeTopicSuggester.kt` — confirmed

## Notes for Verifier
- Important files: All 10 listed above
- `SuggestedTopic` data class is retained inside `GeminiTopicSuggester.kt` (moved from `ClaudeTopicSuggester.kt`); `NewsletterGenerationService` and `KeywordViewModel` reference it via the same package
- The `system` prompt from the old Claude call is merged into the user `parts[0].text` for Gemini (Gemini v1beta `contents` array does not support a separate `system` role in the same way; the system instruction is prepended as a single user message)
- Suggested check: `./gradlew :app:assembleDebug` to confirm no unresolved references

## User Next Action
설정 화면 → "Gemini AI 연동" → Google AI Studio(`https://aistudio.google.com/apikey`)에서 발급한 Gemini API Key 새로 입력 후 키워드를 추가하여 주제 자동 생성을 확인하세요.
