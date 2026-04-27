# Task Brief: blocksToHtml NPE 픽스 — 뉴스레터 그리드 표시 복구

Task ID: TASK-20260426-019
Status: active

## Goal
`NewsletterRepository.getNewsletters()`가 NPE로 throw하는 문제를 해결해 카드 그리드가 정상 표시되도록. detail view의 WebView 동작은 본 Brief 범위 외(htmlContent가 빈 문자열로 떨어지는 것 허용 — 후속 Task에서 정식 변환 처리).

## Root cause (logcat 확인 완료)
- `getBlockChildren` 응답은 block 객체 리스트 (`{"object":"block","type":"heading_1",...}`).
- Retrofit/Gson이 응답을 `NotionQueryResponse(results: List<NotionPage>)`로 매핑 — 잘못된 타이핑.
- block JSON에는 `properties` 키가 없어서 Gson이 `NotionPage.properties`를 null로 채움(Kotlin 비-null 타입 무시).
- `blocksToHtml`이 `page.properties.entries.firstOrNull()` 호출 → NPE.
- 결과: `getNewsletters()` throw → ViewModel `state.error` 설정 → Screen `isEmpty()` 분기로 "아직 뉴스레터가 없습니다" fallback.

## Scope
`app/src/main/java/com/dailynewsletter/data/repository/NewsletterRepository.kt`만 수정. 두 단계 방어:

1. **호출지(getNewsletters 안)**: `blocksToHtml(it)` 호출을 try-catch로 감싸 NPE/예외 시 null 반환.
   ```kotlin
   val htmlContent = contentBlocks?.let {
       try { blocksToHtml(it) } catch (e: Exception) { null }
   }
   ```

2. **함수 내부(blocksToHtml)**: null `properties` 안전 처리. 한 줄 변경:
   ```kotlin
   page.properties?.entries?.firstOrNull()?.value?.richText?.joinToString("") { ... } ?: ""
   ```
   (현재는 `page.properties.entries...` — non-null 가정.)

이 두 변경만으로 NPE 차단. `getNewsletters()`는 전체 페이지 리스트를 정상 반환.

## Out of Scope
- `NotionApi.getBlockChildren`의 반환 타입을 `List<NotionBlock>`으로 정식 변경하는 리팩토링 — **별도 후속 과제**(TASK-020 candidate).
- `blocksToHtml`을 실제 block 구조에서 HTML로 정확히 변환하는 재작성 — 위 후속 과제와 함께.
- Detail view WebView가 빈 페이지를 보이는 문제 — 위 후속 과제로 해결.
- ViewModel/Screen 변경 없음.
- NotionModels.kt / NotionApi.kt 변경 없음.

## Files Owned By This Task
- `app/src/main/java/com/dailynewsletter/data/repository/NewsletterRepository.kt`

## Files Explicitly Not Owned
- `app/src/main/java/com/dailynewsletter/data/remote/notion/NotionModels.kt`
- `app/src/main/java/com/dailynewsletter/data/remote/notion/NotionApi.kt`
- `app/src/main/java/com/dailynewsletter/ui/newsletter/*`
- 그 외 모든 파일.

## Forbidden Changes
- No new dependency.
- No data class field nullability change in NotionModels.kt.
- No NotionApi 시그니처 변경.
- TASK-007~018 변경분 보존 (특히 saveNewsletter 흐름, htmlToBlocks, chunkText, OffsetDateTime 사용).

## Acceptance Criteria
- [ ] `getNewsletters()`의 `blocksToHtml(it)` 호출이 try-catch로 감싸져 있다.
- [ ] `blocksToHtml` 내부의 `page.properties.entries` 접근이 null-safe로 변경되어 있다 (`page.properties?.entries?.firstOrNull()...` 형태).
- [ ] grep `try {` count가 NewsletterRepository.kt 안에서 ≥1 (blocksToHtml 호출 보호).
- [ ] grep `properties\?\.` 또는 동등한 null-safe 접근이 blocksToHtml 안에 1+ hit.
- [ ] 빌드 성공 또는 SKIPPED_ENVIRONMENT_NOT_AVAILABLE.

## Verification Command Candidates
- `grep -n "blocksToHtml\|properties\?\." app/src/main/java/com/dailynewsletter/data/repository/NewsletterRepository.kt`
- `./gradlew :app:assembleDebug`

## Expected Implementer Output
- 변경 파일 1개. diff 3~5줄.
- 변경 줄 인용.
- grep 결과.
- 빌드 결과.
- 사용자 다음 동작 1줄 (재빌드 + 뉴스레터 탭 재진입).
