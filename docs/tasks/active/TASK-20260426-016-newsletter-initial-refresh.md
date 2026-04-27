# Task Brief: 뉴스레터 화면 초기 진입 시 Notion에서 자동 로드

Task ID: TASK-20260426-016
Status: active

## Goal
뉴스레터 탭에 처음 들어갔을 때 Notion Newsletters DB의 기존 뉴스레터들이 즉시 표시되도록. 현재는 새로 생성하기 전까지 빈 그리드. (TASK-008과 동일 패턴 — 키워드 화면에서 했던 init refresh.)

## User-visible behavior
- 앱 첫 실행 후 뉴스레터 탭 진입 → 잠깐 로딩 → Notion에 저장된 모든 뉴스레터가 카드 그리드로 표시.
- 네트워크/권한 실패 시 에러 스낵바로 사유 표시.

## Scope
- `NewsletterViewModel.loadNewsletters()` 시작 부분 (또는 init 블록에서 별도 launch로) `newsletterRepository.refreshNewsletters()` 1회 호출.
- refresh 실패 시 `state.error` 메시지 세팅.

## Out of Scope
- `NewsletterRepository` / Notion API 변경 없음.
- ViewModel 다른 메서드 변경 없음 (수동 생성 후 호출되는 `loadNewsletters()` 흐름 그대로).
- Compose Screen 수정 없음.

## Relevant Context
- 동일 패턴 선례: `app/src/main/java/com/dailynewsletter/ui/keyword/KeywordViewModel.kt` (TASK-008에서 적용한 `runCatching { keywordRepository.refreshKeywords() }.onFailure { ... }` 패턴).
- 수정 대상: `app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterViewModel.kt`.
- `NewsletterRepository.refreshNewsletters()`가 이미 존재 (line 28~ 부근에서 query → StateFlow 갱신). 시그니처/동작 변경 금지.

## Files Owned By This Task
- `app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterViewModel.kt`

## Files Explicitly Not Owned
- `app/src/main/java/com/dailynewsletter/data/repository/NewsletterRepository.kt`
- `app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterScreen.kt`
- 그 외 모든 파일.

## Forbidden Changes
- No new dependency.
- No public API change.
- TASK-007/008/009/010/011/012/013/014/015 변경분 보존 (특히 TASK-014의 Compose UI는 절대 손대지 말 것).

## Acceptance Criteria
- [ ] `loadNewsletters()` 또는 init 블록에서 `newsletterRepository.refreshNewsletters()`가 호출된다.
- [ ] refresh 실패 시 `state.error`로 사유 노출.
- [ ] grep `refreshNewsletters` in NewsletterViewModel.kt → 1+ hit.
- [ ] diff가 init/loadNewsletters 영역에 국한.
- [ ] 빌드 성공 또는 SKIPPED_ENVIRONMENT_NOT_AVAILABLE.

## Verification Command Candidates
- `grep -n "refreshNewsletters" app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterViewModel.kt`
- `./gradlew :app:assembleDebug`

## Expected Implementer Output
- 변경 파일 1개.
- 변경 줄 수 ~5줄.
- grep 결과.
- 빌드 결과(또는 SKIPPED).
