# Task Brief: 뉴스레터 상세 UI — 인쇄 버튼 1개로 정리 + 뒤로가기

Task ID: TASK-20260428-028
Status: active

## Goal
뉴스레터 상세 화면(카드 탭 후 WebView 본문)에 현재 인쇄 버튼이 **상단 우측 + 본문 아래** 2개 중복 존재. 본문 아래 버튼을 제거하고 상단 우측만 남긴다. 뒤로가기 시 뉴스레터 그리드 탭으로 복귀하도록 명시.

## User-visible behavior
- 뉴스레터 카드 탭 → 상세 화면 진입 → 상단(TopAppBar) 우측에 인쇄 버튼 1개만 존재.
- 본문 하단(WebView 아래) 인쇄 버튼/관련 Row 사라짐.
- 디바이스 뒤로가기 또는 TopAppBar 좌측 화살표 → 뉴스레터 그리드(목록)로 돌아감.

## Scope
`app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterScreen.kt` 단일 파일.

작업:
1. 상세 뷰의 본문 아래 print 버튼/Button(또는 그를 감싼 Row/Column)을 식별해 제거. (TASK-023에서 이 자리에서 `viewModel.printNewsletter(activity, item)` 호출하는 코드가 있을 것 — 로직 자체는 상단 우측 IconButton에서 동일하게 호출되고 있어야 함, 그 IconButton은 보존.)
2. TopAppBar의 navigationIcon (좌측 화살표) 또는 BackHandler가 그리드로 돌아가도록 보장. 현재 단일 화면 안에서 selectedItem state로 분기되고 있다면, 뒤로가기 시 `selectedItem = null`로 세팅하는 분기가 있는지 확인 후 보존/추가.
   - 예: `BackHandler(enabled = selectedItem != null) { selectedItem = null }`.
   - TopAppBar navigationIcon도 동일 동작.

## Out of Scope
- 다른 화면 변경 0.
- ViewModel 로직 변경 0 (printNewsletter는 그대로).
- 그리드 자체 변경 0.
- 알람 / 설정 / 키워드 / 주제 화면 무관.

## Files Owned By This Task
- `app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterScreen.kt`

## Files Explicitly Not Owned
- 그 외 모든 파일.

## Forbidden Changes
- No new dependency.
- No ViewModel 시그니처 변경.
- No 다른 화면 파일 수정.

## Acceptance Criteria
- [ ] `NewsletterScreen.kt` 안에서 `printNewsletter(` 호출이 정확히 **1곳**만 남음 (`grep -c "printNewsletter("`).
- [ ] 그 호출이 IconButton/TopAppBar 영역 안에 위치 (본문 아래 Button 영역에는 없음).
- [ ] BackHandler 또는 navigationIcon 핸들러가 selectedItem null화 또는 동등한 그리드 복귀 동작 수행.
- [ ] 빌드 성공 또는 SKIPPED_ENVIRONMENT_NOT_AVAILABLE.

## Verification Command Candidates
- `grep -c "printNewsletter(" app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterScreen.kt`
- `grep -n "BackHandler\\|navigationIcon\\|selectedItem" app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterScreen.kt`
- `./gradlew :app:assembleDebug`

## Expected Implementer Output
- 변경 파일 1개.
- 제거된 Row/Button 영역 인용 (before/after diff 한 토막).
- BackHandler/navigationIcon 처리 인용.
- grep 결과.
- 빌드 결과.
- 사용자 다음 동작 1줄: 재빌드 → 카드 탭 → 인쇄 버튼이 상단에만, 뒤로가기로 그리드 복귀 확인.
