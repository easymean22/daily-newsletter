## Implementation Summary

Task ID: TASK-20260428-032
Status: IMPLEMENTED

## Changed Files

- `app/src/main/java/com/dailynewsletter/data/remote/notion/NotionModels.kt`: Added `code: NotionCodeBlock?` field to `NotionBlock`. Added new `NotionCodeBlock` data class with `@SerializedName("rich_text")` richText field and `language` field.
- `app/src/main/java/com/dailynewsletter/data/repository/NewsletterRepository.kt`: Added mermaid extraction pass in `htmlToBlocks` — extracts `<pre><code class="language-mermaid">...</code></pre>` before the strip-tags pass, produces `NotionBlock(type="code", code=NotionCodeBlock(..., language="mermaid"))` entries with position preserved, then removes those regions from HTML before the regular h1/h2/h3/p/ul/li pass. Updated `ul` and fallback passes to use `processedHtml` instead of `html`.
- `app/src/main/java/com/dailynewsletter/service/NewsletterGenerationService.kt`: Added rule 8 to the deep-dive prompt (after existing rule 7 about `</body></html>`) with full mermaid guidance: diagram types, format (`<pre><code class="language-mermaid">...</code></pre>`), max 2 per article, skip if unsure of syntax.

## Behavior Changed

- Gemini is now instructed to emit mermaid diagrams using `<pre><code class="language-mermaid">` for architecture/flow/state/ER scenarios.
- `htmlToBlocks` now detects those blocks and converts them to Notion `code` blocks with `language: "mermaid"`, which Notion renders as live diagrams.
- Position of mermaid blocks relative to surrounding content is preserved (sorted by HTML offset together with h1/h2/h3/p/ul).

## Tests Added or Updated

None — no existing unit tests for `htmlToBlocks` in the repo; the extraction logic is straightforward regex and follows the existing indexed-block pattern.

## Commands Run

- grep verification (NewsletterGenerationService): "mermaid" appears 3 times (lines 80, 85, 88).
- grep verification (data/): `NotionCodeBlock` defined at NotionModels.kt:168; `language-mermaid` in NewsletterRepository.kt:343; `type = "code"` at NewsletterRepository.kt:360; `code = NotionCodeBlock(...)` at line 361.
- `./gradlew :app:assembleDebug`: SKIPPED_ENVIRONMENT_NOT_AVAILABLE (Bash tool denied).

## Notes for Verifier

- Important files:
  - `app/src/main/java/com/dailynewsletter/data/remote/notion/NotionModels.kt` lines 158-171
  - `app/src/main/java/com/dailynewsletter/data/repository/NewsletterRepository.kt` lines 339-366
  - `app/src/main/java/com/dailynewsletter/service/NewsletterGenerationService.kt` lines 79-89
- Suggested checks:
  - Build and confirm no compile errors.
  - Manually verify that a newsletter generation with a suitable topic emits `<pre><code class="language-mermaid">` in the Gemini HTML response, and that the resulting Notion page shows a rendered diagram.
- Known limitations:
  - Notion mermaid rendering depends on the workspace plan and feature availability; the code produces the correct block structure.
  - The prompt instructs Gemini to skip mermaid when syntax is uncertain, so some newsletters may have 0 diagrams — this is expected and correct.

## User next action

Rebuild the app → generate a new newsletter on a structural topic (e.g., Kubernetes, Kafka, TCP/IP) → open the Notion page and verify that a mermaid diagram block renders as a graphic diagram.
