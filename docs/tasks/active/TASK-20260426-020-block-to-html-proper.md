# Task Brief: Notion Block → HTML 정식 변환 + getBlockChildren 타입 정정

Task ID: TASK-20260426-020
Status: active

## Goal
TASK-019의 임시 NPE 방어를 떼어내고 정식으로 처리. `NotionApi.getBlockChildren`의 반환 타입을 정확히 잡고, `NewsletterRepository.blocksToHtml`을 실제 block 구조(`heading_1`/`heading_2`/`heading_3`/`paragraph`/`bulleted_list_item`)에서 HTML로 변환하도록 재작성. 결과: 뉴스레터 카드 탭 → WebView가 본문을 실제로 보여줌.

## User-visible behavior
- 뉴스레터 그리드의 카드를 탭하면 WebView 상세 화면이 뉴스레터 본문(헤더/단락/리스트)을 정상 렌더.
- 그리드 자체는 TASK-019 이전 상태로 동일하게 표시.
- 인쇄 흐름은 본 변경 영향 없음(htmlContent 사용처 동일 보존).

## Scope
1. **`NotionModels.kt`**:
   - `NotionBlocksResponse` 데이터 클래스 신설:
     ```kotlin
     data class NotionBlocksResponse(
         val results: List<NotionBlock>,
         @SerializedName("has_more") val hasMore: Boolean = false,
         @SerializedName("next_cursor") val nextCursor: String? = null
     )
     ```
   - `NotionBlock`의 기존 필드(`paragraph`, `heading_1`, `heading_2`, `heading_3`, `bulleted_list_item`) 그대로 활용.
   - `NotionParagraphBlock`/`NotionHeadingBlock`/`NotionListItemBlock`이 모두 `richText: List<NotionRichText>` 필드를 가짐 — 변경 불필요.

2. **`NotionApi.kt`**:
   - `getBlockChildren`의 반환 타입을 `NotionQueryResponse` → `NotionBlocksResponse`로 교체.
   - 다른 method(`queryDatabase` 등) 변경 금지.

3. **`NewsletterRepository.kt`**:
   - `blocksToHtml(response: NotionQueryResponse): String` 시그니처를 `blocksToHtml(response: NotionBlocksResponse): String`으로 변경.
   - 본문을 실제 block 처리로 재작성:
     - `block.type` 분기:
       - `"heading_1"` → `<h1>${plainText(block.heading_1.richText)}</h1>`
       - `"heading_2"` → `<h2>...</h2>`
       - `"heading_3"` → `<h3>...</h3>`
       - `"paragraph"` → `<p>...</p>`
       - `"bulleted_list_item"` → `<li>...</li>` (연속된 bulleted_list_item을 `<ul>`으로 감싸기)
       - 그 외 type → 무시.
     - `plainText(rich: List<NotionRichText>)`: rich_text 배열의 `text.content` join.
   - 기존 HTML 래퍼(스타일 + body) 유지 — 본문(body)만 새 변환 결과로 채움.
   - **TASK-019 변경분 처리**:
     - `getNewsletters()`의 `try { blocksToHtml(it) } catch ... { null }` 호출지 wrapping은 유지하거나 제거 가능. **권장: 유지** (방어 다층).
     - 함수 내부 `page.properties?.entries?...` 부분은 새 구현으로 교체되므로 자연스럽게 사라짐.
   - 4개 call-site (line 51, 143, 194, 243)는 NotionApi의 새 반환 타입으로 인해 자동 호환 (`NotionBlocksResponse`로 받게 됨). 호출 코드는 그대로 컴파일 통과.

## Out of Scope
- inline 스타일(`<strong>`, `<code>`, `<a>`) 보존 — 후속.
- `<ol>` numbered list 지원 — 후속.
- 표(table), 이미지, 코드 블록 처리 — 후속.
- ViewModel/Screen 변경 없음.
- TopicRepository/KeywordRepository 영향 없음 (queryDatabase는 그대로 NotionQueryResponse 반환).

## Files Owned By This Task
- `app/src/main/java/com/dailynewsletter/data/remote/notion/NotionModels.kt`
- `app/src/main/java/com/dailynewsletter/data/remote/notion/NotionApi.kt`
- `app/src/main/java/com/dailynewsletter/data/repository/NewsletterRepository.kt`

## Files Explicitly Not Owned
- `app/src/main/java/com/dailynewsletter/ui/newsletter/*`
- `app/src/main/java/com/dailynewsletter/data/repository/TopicRepository.kt`
- `app/src/main/java/com/dailynewsletter/data/repository/KeywordRepository.kt`
- `app/src/main/java/com/dailynewsletter/service/*`
- 그 외 모든 파일.

## Forbidden Changes
- No new dependency.
- No DB 스키마 변경.
- TopicRepository/KeywordRepository의 NotionQueryResponse 사용 그대로 유지 (queryDatabase는 변경 안 함).
- TASK-007~019 변경분 보존 (특히 saveNewsletter의 chunkText/htmlToBlocks 사용, OffsetDateTime, 제목 형태).

## Acceptance Criteria
- [ ] `NotionModels.kt`에 `NotionBlocksResponse` 신설.
- [ ] `NotionApi.getBlockChildren`의 반환 타입이 `NotionBlocksResponse`.
- [ ] `NewsletterRepository.blocksToHtml`이 `NotionBlocksResponse`를 받아 block.type별로 분기해 HTML 생성.
- [ ] 연속된 `bulleted_list_item`을 `<ul>` 한 묶음으로 감싸는 로직 존재.
- [ ] grep `NotionBlocksResponse` → 정의 1 + 사용 2(API + Repository) = 3 hits.
- [ ] grep `heading_1\|heading_2\|heading_3\|paragraph\|bulleted_list_item` in NewsletterRepository.kt → block.type 분기 줄들 존재.
- [ ] TopicRepository/KeywordRepository 미수정.
- [ ] 빌드 성공 또는 SKIPPED_ENVIRONMENT_NOT_AVAILABLE.

## Verification Command Candidates
- `grep -rn "NotionBlocksResponse" app/src/main`
- `grep -n "heading_1\|heading_2\|heading_3" app/src/main/java/com/dailynewsletter/data/repository/NewsletterRepository.kt`
- `./gradlew :app:assembleDebug`

## Expected Implementer Output
- 변경 파일 3개.
- `NotionBlocksResponse` 정의 인용.
- 새 `blocksToHtml` 핵심 로직(type 분기 + ul 감싸기) 인용.
- `NotionApi.getBlockChildren` 시그니처 변경 인용.
- grep 결과.
- 빌드 결과.
- 사용자 다음 동작 1줄 (재빌드 + 카드 탭 → WebView 본문 확인).

## STOP_AND_ESCALATE
- `NotionBlock.heading_1` 등의 필드명이 Notion JSON과 다르다고 의심되면 escalate (현재 컨벤션상 Gson `@SerializedName` 없이 snake_case 필드명을 직접 매핑해 둠 — 동일 컨벤션 유지).
- `NotionParagraphBlock`/`NotionHeadingBlock`/`NotionListItemBlock`이 `richText: List<NotionRichText>` 필드를 갖지 않는다면 escalate.
