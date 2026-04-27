# Task Brief: Notion rich_text 2000자 제한 회피 (HTML chunking)

Task ID: TASK-20260426-012
Status: active

## Goal
뉴스레터 수동 생성 시 Notion 페이지 저장이 HTTP 400으로 실패하는 문제를 해결한다. Notion API 제약: `rich_text.text.content.length ≤ 2000`. 현재는 13KB HTML 통째를 단일 rich_text 토막으로 보내 거부됨. 동일 paragraph 블록 안에서 rich_text 배열에 여러 토막으로 쪼개면 통과(블록당 100 토막까지 가능 = 약 200,000자).

## User-visible behavior
- 뉴스레터 화면에서 수동 생성 버튼 → 성공 스낵바 + Notion Newsletters DB에 1건 추가.
- 페이지 본문에는 HTML이 여러 paragraph 토막으로 나뉘어 저장(시각상 한 단락처럼 보이지만 내부 segment만 다수). Notion에 들어가서 보면 정상 텍스트.

## Root cause (확인된 응답)
```
{"object":"error","status":400,"code":"validation_error",
 "message":"body failed validation: body.children[0].paragraph.rich_text[0].text.content.length should be ≤ 2000, instead was 6994.",
 "request_id":"..."}
```

해당 코드: `app/src/main/java/com/dailynewsletter/data/repository/NewsletterRepository.kt:127`
```kotlin
richText = listOf(NotionRichText(text = NotionTextContent(htmlContent)))
```

## Scope
- `NewsletterRepository.saveNewsletter` 안에서 `htmlContent`를 1900자(여유분) 이하 chunk로 분할.
- 분할 결과를 `richText = chunks.map { NotionRichText(text = NotionTextContent(it)) }`로 전달.
- private helper 함수 추가 가능: `private fun chunkText(text: String, max: Int = 1900): List<String>`.
- chunk는 단순 substring으로 자르면 됨(HTML 시각 정합 무관 — 어차피 paragraph rich_text 안의 plain text 저장).

## Out of Scope
- HTML 파싱 / Notion 블록 변환 없음 (현재처럼 HTML 원문을 plain text로 저장하는 설계 유지).
- 페이지 분할(여러 paragraph 블록으로) 없음 — 단일 paragraph 안 rich_text 배열로 충분.
- `htmlContent` 자체 길이 줄이기 / 프롬프트 수정 없음.
- 다른 Repository 변경 없음.

## Notion API 제약 (참고)
출처: https://developers.notion.com/reference/request-limits
- `rich_text.text.content` length ≤ 2000.
- block 당 `rich_text` 배열 최대 100 entries.
- 따라서 단일 paragraph block에 들어갈 수 있는 총 텍스트 ≤ 200,000자.

본 뉴스레터는 13~20KB 수준이라 100 entries 제약 안에서 충분히 처리 가능.

## Files Likely Involved
- `app/src/main/java/com/dailynewsletter/data/repository/NewsletterRepository.kt`

## Files Owned By This Task
- `app/src/main/java/com/dailynewsletter/data/repository/NewsletterRepository.kt`

## Files Explicitly Not Owned
- `app/src/main/java/com/dailynewsletter/service/NewsletterGenerationService.kt`
- 그 외 모든 파일.

## Forbidden Changes
- No new dependency.
- No public API contract change beyond the saveNewsletter body.
- No HTML 의미 변경.
- No Notion block 타입 변경 (paragraph 유지).
- No 추가 page split.
- TASK-007/008/009/010/011 변경분 보존.

## Acceptance Criteria
- [ ] `saveNewsletter` 안에서 `htmlContent`가 chunk 분할되어 `richText` 배열의 N개 entry로 들어간다.
- [ ] chunk size 상한 ≤ 2000 (권장 1900).
- [ ] grep `NotionRichText` in NewsletterRepository.kt → 변경 줄에 chunk map 형태로 등장.
- [ ] grep `2000\|1900` (또는 chunk 상수)이 NewsletterRepository.kt 내에 1회 이상 등장.
- [ ] 빈 문자열·짧은 문자열도 안전하게 처리 (chunkText("")는 listOf("") 또는 listOf() 반환 — Notion이 둘 다 허용하므로 어느 쪽이든 OK).
- [ ] 빌드 성공 또는 SKIPPED_ENVIRONMENT_NOT_AVAILABLE.

## Verification Command Candidates
- `grep -n "NotionRichText" app/src/main/java/com/dailynewsletter/data/repository/NewsletterRepository.kt`
- `grep -n "1900\|2000\|chunk" app/src/main/java/com/dailynewsletter/data/repository/NewsletterRepository.kt`
- `./gradlew :app:assembleDebug`

## Expected Implementer Output
- 변경 파일 1개.
- 추가/수정된 줄 인용 (chunk helper + saveNewsletter 변경 줄).
- grep 결과.
- 빌드 결과(또는 SKIPPED).
- 사용자 다음 동작 1줄 (재빌드 + 뉴스레터 수동 생성 재시도).

## STOP_AND_ESCALATE
- Notion API 응답이 다른 제약(예: rich_text 배열 자체 길이 100 초과)으로 다시 거부되면 escalate — 그 경우 paragraph block 자체를 여러 개로 split해야 함.
- chunk size를 어떻게 잡든 한국어 multi-byte 문자에서 surrogate pair가 깨지는 케이스가 의심되면 escalate (단순 substring으로도 대부분 안전 — 대안 확정 후 결정).
