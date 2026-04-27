# Task Brief: 키워드 화면 초기 진입 시 Notion에서 자동 로드

Task ID: TASK-20260426-008
Status: active

## Goal
앱 새로 빌드/설치 후 키워드 화면을 처음 열었을 때 Notion Keywords DB에 이미 존재하는 키워드 목록이 즉시 표시되도록 한다. 현재는 새 키워드를 추가하기 전까지 빈 화면.

## User-visible behavior
- 앱 첫 실행 후 키워드 탭 진입 → 잠깐 로딩 표시 → Notion에 저장된 모든 (`pending` + `resolved`) 키워드가 화면에 표시.
- 네트워크/권한 실패 시 에러 스낵바로 사유 표시(현재의 `exceptionHandler`/`error` 메커니즘 그대로 활용).

## Scope
- `KeywordViewModel.loadKeywords()` 시작 부분에서 `keywordRepository.refreshKeywords()`를 1회 호출하여 StateFlow에 초기 데이터를 채운다. 그 뒤 기존 `combine(observeKeywords(), _filter)` 흐름은 그대로.
- refresh 실패 시 `state.error`에 메시지 세팅 (사용자가 스낵바로 인지 가능).

## Out of Scope
- `KeywordRepository`/Notion API 클라이언트 변경 없음.
- 다른 ViewModel(Settings/Topics/Newsletter)의 초기 로드 로직 변경 없음 — 별도 후속 과제.
- 페이징/infinite-scroll 같은 추가 기능 도입 없음.
- Compose Screen 코드 수정 없음 (스낵바는 이미 존재하는 error 채널 그대로).

## Relevant Context
- `app/src/main/java/com/dailynewsletter/ui/keyword/KeywordViewModel.kt`
  - 현재 `init { loadKeywords() }` → `loadKeywords()`는 `combine(observeKeywords(), _filter)`만 collect.
  - `observeKeywords()`는 `_keywords = MutableStateFlow(emptyList())` 위에 만들어진 Flow. 어디선가 `refreshKeywords()`가 호출되기 전까지 비어 있음.
- `app/src/main/java/com/dailynewsletter/data/repository/KeywordRepository.kt`
  - `refreshKeywords()` 메서드가 이미 존재 — 동일 패턴 사용.
- `app/src/main/java/com/dailynewsletter/ui/common/ViewModelExtensions.kt`
  - `exceptionHandler` 사용 패턴 그대로 (이미 `viewModelScope.launch(exceptionHandler)` 적용 중).

## Files Likely Involved
- `app/src/main/java/com/dailynewsletter/ui/keyword/KeywordViewModel.kt`

## Files Owned By This Task
- `app/src/main/java/com/dailynewsletter/ui/keyword/KeywordViewModel.kt`

## Files Explicitly Not Owned
- `app/src/main/java/com/dailynewsletter/ui/keyword/KeywordScreen.kt`
- `app/src/main/java/com/dailynewsletter/data/repository/KeywordRepository.kt`
- 그 외 모든 파일

## Forbidden Changes
- No new dependency.
- No DB schema change.
- No public API change beyond ViewModel internal.
- No background scheduling change.
- No formatting-only edits to other files.
- TASK-007 변경분(`ensureFreeTopicTag` 적용)을 건드리지 말 것.

## Android Constraints
- Use existing Kotlin style.
- runCatching / try-catch 어느 쪽이든 OK, 단 `state.error`로 사용자가 사유를 볼 수 있어야 함.
- isLoading 상태 토글이 자연스럽게 끝나도록 (combine collect는 무한 루프이므로 isLoading=false는 첫 emit 시 또는 refresh 직후에 토글).

## Acceptance Criteria
- [ ] `loadKeywords()` 안에서 `keywordRepository.refreshKeywords()`가 호출된다 (또는 init 블록에서 별도 launch로 호출).
- [ ] refresh 호출이 collect 시작 전에 실행되거나, collect의 첫 emit이 비어 있어도 refresh 결과가 그 이후 emit으로 따라온다.
- [ ] refresh 실패 시 `_uiState.update { it.copy(error = e.message ?: "...") }` 형태로 에러가 노출된다.
- [ ] grep 단일 호출로 `refreshKeywords` 호출이 KeywordViewModel.kt에 1회 이상 등장하는지 확인.
- [ ] diff가 `loadKeywords()` 또는 init 블록 영역에 국한.

## Verification Command Candidates
- 정적: `grep -n "refreshKeywords" app/src/main/java/com/dailynewsletter/ui/keyword/KeywordViewModel.kt`
- `./gradlew :app:assembleDebug` (환경 미가용 시 SKIPPED 보고)
- `./gradlew :app:testDebugUnitTest`

## Expected Implementer Output
- 변경 파일 1개 (KeywordViewModel.kt).
- 변경 줄 수 ~5줄.
- 변경 메서드 인용 + grep 결과 + 빌드 결과(또는 SKIPPED 사유).

## 재현/검증 시나리오 (참고)
1. 앱 빌드 후 데이터 초기화(앱 정보 → 저장공간 → 캐시/데이터 삭제) 또는 새 인스턴스 설치.
2. 설정 화면에서 Notion 키 + 상위 페이지 ID 재입력 → "Notion DB 자동 생성" 또는 기존 DB 재사용.
3. 키워드 탭 진입 → 기존 DB의 키워드들이 자동으로 화면에 표시됨.
