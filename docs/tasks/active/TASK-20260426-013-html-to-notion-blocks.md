# Task Brief: HTML → Notion 네이티브 블록 변환

Task ID: TASK-20260426-013
Status: active

## Goal
Gemini가 생성한 HTML 본문을 Notion 페이지에 단일 paragraph rich_text로 박지 말고, h1/h2/h3/p/ul→li 태그를 각각 대응되는 Notion 네이티브 블록으로 분해해 `children` 배열로 전달한다. Notion에서 페이지를 열었을 때 헤더·단락·리스트가 정상적으로 렌더링되도록.

## User-visible behavior
- 뉴스레터 수동 생성 후 Notion 페이지 진입 → 제목 헤더 + 섹션 헤더 + 단락 + 불릿 리스트가 Notion 네이티브 스타일로 표시.
- 현재처럼 `<h1>...</h1>` 같은 raw 태그가 그대로 보이는 문제 사라짐.

## Scope
1. `NotionModels.kt`의 `NotionBlock`에 `heading_1: NotionHeadingBlock? = null` 필드 추가 (현재 h2/h3만 정의되어 있음).
2. `NewsletterRepository.saveNewsletter`에서 `htmlContent`를 Notion 블록 트리로 변환하는 private helper `htmlToBlocks(html: String): List<NotionBlock>` 추가.
3. `saveNewsletter`의 `children = listOf(NotionBlock(...단일 paragraph...))`을 `children = htmlToBlocks(htmlContent)`로 교체.
4. 기존 `chunkText` helper는 paragraph block의 텍스트가 1900자 초과할 때 동일 paragraph의 `richText` 배열을 분할하기 위해 재사용 (TASK-012 변경분 보존).

## 매핑 규칙
- `<h1>` → `NotionBlock(type="heading_1", heading_1=NotionHeadingBlock(richText=[...]))` (필드 이름 `heading_1`).
- `<h2>` → `heading_2` 블록.
- `<h3>` → `heading_3` 블록.
- `<p>` → `paragraph` 블록 (텍스트 1900자 초과 시 chunk 적용).
- `<ul>` 안의 `<li>` → 각각 `bulleted_list_item` 블록.
- `<p class="source">`는 일반 paragraph 블록과 동일 처리 (스타일 구분 없음 — Notion 측 시각 차이 미지원, 후속 결정).
- `<style>`, `<head>`, `<body>`, `<!DOCTYPE>`, `<html>`, `<meta>` 등 시각 메타 태그는 무시 (스킵).
- 텍스트 안의 인라인 태그(`<code>`, `<strong>`, 마크다운 잔재)는 일단 plain text로 stripping (예: `<code>foo</code>` → `foo`). 인라인 스타일 보존은 후속 결정.
- HTML entity (`&lt;`, `&gt;`, `&amp;`, `&nbsp;`) 디코딩.

## 파서 전략
- 새 의존성(Jsoup 등) 추가 금지 — 단순 정규식/문자열 처리로 충분.
- 권장: 블록 단위 정규식으로 `<(h1|h2|h3|p)([^>]*)>([\\s\\S]*?)</\\1>` 매칭 + `<ul>...</ul>` 안에서 `<li>([\\s\\S]*?)</li>` 추출.
- 잘리거나 닫는 태그가 없는 입력에 대해 방어적: 마지막 미닫힌 태그가 있어도 빈 블록 1~2개 반환하고 throw하지 않음.

## Out of Scope
- HTML 원문 별도 보존(예: page property `Source HTML`) — 후속 과제로 미룸 (인쇄 단계에서 결정).
- 인라인 스타일/이미지/링크 대응 — 후속.
- Gemini 프롬프트 변경 — 별도 TASK-015에서 다룸.
- NewsletterGenerationService 수정 없음.
- UI 변경 없음.

## Files Owned By This Task
- `app/src/main/java/com/dailynewsletter/data/repository/NewsletterRepository.kt`
- `app/src/main/java/com/dailynewsletter/data/remote/notion/NotionModels.kt` (heading_1 필드 추가만)

## Files Explicitly Not Owned
- `app/src/main/java/com/dailynewsletter/service/NewsletterGenerationService.kt`
- `app/src/main/java/com/dailynewsletter/ui/newsletter/*`
- 그 외 모든 파일.

## Forbidden Changes
- No new dependency (Jsoup 등 절대 금지).
- No DB schema 추가 (Source HTML property 등 후속).
- No prompt 수정.
- TASK-007/008/009/010/011/012 변경분 보존.
- `chunkText` helper 시그니처/동작 보존.
- `NotionBlock`의 기존 필드 제거 금지.

## Acceptance Criteria
- [ ] `NotionBlock`에 `heading_1` 필드 추가 (default null, 기존 필드 유지).
- [ ] `NewsletterRepository`에 `htmlToBlocks` 또는 동등한 private helper 존재.
- [ ] `saveNewsletter`의 `children` 인자가 `htmlToBlocks(htmlContent)` 결과를 사용.
- [ ] 빈 문자열 / 닫힘 태그 누락 입력에 대해 throw 없이 빈/부분 결과 반환.
- [ ] grep `htmlToBlocks` → 정의 1 + 사용 1 = 2 hits.
- [ ] grep `heading_1` → 정의 1 + 사용 1+ hits.
- [ ] 기존 `chunkText` 호출이 paragraph 블록 생성 경로에서 여전히 사용됨.
- [ ] 빌드 성공 또는 SKIPPED_ENVIRONMENT_NOT_AVAILABLE.

## Verification Command Candidates
- `grep -n "htmlToBlocks\|heading_1\|chunkText" app/src/main/java/com/dailynewsletter/data/repository/NewsletterRepository.kt`
- `grep -n "heading_1" app/src/main/java/com/dailynewsletter/data/remote/notion/NotionModels.kt`
- `./gradlew :app:assembleDebug`

## Expected Implementer Output
- 변경 파일 2개.
- `htmlToBlocks` 핵심 로직 인용.
- grep 결과.
- 빌드 결과(또는 SKIPPED).
- 사용자 다음 동작 1줄.

## STOP_AND_ESCALATE
- HTML 인라인 태그 대응이 의미상 필수라고 판단되면 escalate (현재 본 Brief는 plain text strip 채택).
- Notion API가 `heading_1` 필드를 다른 형식으로 받는다고 의심되면 escalate.
