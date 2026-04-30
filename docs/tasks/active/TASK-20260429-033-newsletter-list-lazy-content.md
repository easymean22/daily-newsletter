# Task Brief: 뉴스레터 목록 지연 로딩 (그리드는 메타만, 본문은 카드 탭 시)

Task ID: TASK-20260429-033
Status: active

## Goal
뉴스레터 탭 진입 시 N개 뉴스레터의 본문을 모두 받아오느라 1+N 순차 GET → 수 초 지연. 그리드는 제목/날짜/상태/태그만 필요하므로 본문 fetch를 카드 탭 시점으로 지연. 결과: 그리드 1초 내 표시.

## User-visible behavior
- 뉴스레터 탭 진입 → 그리드 빠르게 표시 (단일 GET, ~500ms).
- 카드 탭 → 본문 로드 indicator(짧게) → WebView 본문 표시.
- 이미 로드된 카드 재탭 시 즉시 표시 (캐시).
- 인쇄 버튼: 본문 로드 완료 후에만 활성. 로딩 중에는 비활성.

## Scope

### 1. `app/src/main/java/com/dailynewsletter/data/repository/NewsletterRepository.kt`
- `getNewsletters()` 수정: `getBlockChildren` 호출 + `blocksToHtml` 부분 제거. 결과 NewsletterUiItem.htmlContent = null 그대로.
- `getNewsletter(id)` (line ~173) 그대로 유지 — 이미 단건으로 본문 fetch 함.
- `markNewsletterPrinted(id)`(line ~308) 등 다른 함수는 손대지 말 것.
- 다른 단건 조회 함수들(line 223, 273)도 그대로 — 필요시 그대로 호출.

### 2. `app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterViewModel.kt`
- 새 함수 `fun loadNewsletterContent(id: String)`:
  - 이미 그 id의 item.htmlContent != null이면 noop.
  - 그 외: `newsletterRepository.getNewsletter(id)`로 본문 fetch → state.newsletters 안에서 해당 item만 htmlContent 업데이트.
  - 로딩 상태를 위한 새 state field: `val loadingDetailIds: Set<String> = emptySet()`. 시작 시 add, 완료/실패 시 remove.
- 인쇄(`printNewsletter`)는 그대로.

### 3. `app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterScreen.kt`
- 카드 탭 핸들러에서 `viewModel.loadNewsletterContent(item.id)` 호출 → 그 다음 `selectedNewsletter = item` 세팅.
- 상세 뷰: `selectedNewsletter.htmlContent`가 null이면 `CircularProgressIndicator` 또는 "본문 로드 중..." 텍스트. 도착하면 WebView 렌더.
  - state.newsletters 변화 감지 — `selectedNewsletter`를 그대로 hold하면 stale될 수 있음. 해결: 상세 뷰에서 `state.newsletters.find { it.id == selectedNewsletter?.id }` 로 항상 최신 item 사용.
- TopAppBar 인쇄 IconButton: htmlContent == null이면 disabled (`enabled = false`).

## Out of Scope
- API 캐시(Room) — 후속.
- 페이지네이션 — 후속.
- 503 재시도 — 별도 TASK-034.

## Files Owned By This Task
- `app/src/main/java/com/dailynewsletter/data/repository/NewsletterRepository.kt`
- `app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterViewModel.kt`
- `app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterScreen.kt`

## Files Explicitly Not Owned
- `app/src/main/java/com/dailynewsletter/service/NewsletterGenerationService.kt` (TASK-034 소유)
- `app/src/main/java/com/dailynewsletter/data/remote/notion/*`
- 그 외 모든 파일.

## Forbidden Changes
- No new dependency.
- No DB 스키마 변경.
- No `printNewsletter`/`markNewsletterPrinted` 시그니처 변경.

## Acceptance Criteria
- [ ] `NewsletterRepository.getNewsletters()` 본문 안에서 `getBlockChildren` 호출 0건 (grep 결과 0).
- [ ] `NewsletterViewModel`에 `loadNewsletterContent` 함수 존재.
- [ ] `NewsletterViewModel.UiState`에 `loadingDetailIds` 또는 동등한 필드.
- [ ] `NewsletterScreen` 카드 탭 핸들러에서 `loadNewsletterContent` 호출.
- [ ] 상세 뷰에서 `htmlContent == null` 분기에 진행 표시 (CircularProgressIndicator 또는 텍스트).
- [ ] 빌드 성공 또는 SKIPPED_ENVIRONMENT_NOT_AVAILABLE.

## Verification Command Candidates
- `grep -n "getBlockChildren" app/src/main/java/com/dailynewsletter/data/repository/NewsletterRepository.kt`
- `grep -n "loadNewsletterContent\|loadingDetailIds" app/src/main/java/com/dailynewsletter/ui/newsletter`
- `./gradlew :app:assembleDebug`

## Expected Implementer Output
- 변경 파일 3개. 핵심 변경 인용. grep 결과. 빌드 결과. 사용자 다음 동작 1줄.
