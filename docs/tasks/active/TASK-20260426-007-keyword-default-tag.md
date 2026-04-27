# Task Brief: Keyword 기본 태그 invariant 확장

Task ID: TASK-20260426-007
Status: active

## Goal
Keyword 등록 시 사용자가 태그를 입력하지 않으면 자동으로 `모든주제` 1개를 채운다. (Topic 생성 시점의 invariant gate와 동일 동작을 Keyword 시점에도 적용.)

## User-visible behavior
- 키워드 화면에서 태그 입력 없이 키워드를 등록하면 Notion Keywords DB의 해당 row Tags에 `모든주제`가 자동으로 들어간다.
- 사용자가 명시적으로 태그를 입력했으면 그 값 그대로 저장(자동 추가 없음).
- 사용자가 태그 여러 개를 입력했고 그 중 `모든주제`가 없으면 `모든주제`도 함께 추가됨(invariant 동일 동작).

## Scope
- `KeywordRepository.addKeyword(text, type, tags)` 안에서 Notion 저장 직전에 `TagNormalizer.ensureFreeTopicTag(tags)` 적용.
- 그 결과 정규화된 태그 리스트를 그대로 createPage 호출의 multi_select에 사용.
- `addKeyword`가 반환하는 `KeywordUiItem.tags` 필드도 정규화된 값으로 채움.

## Out of Scope
- ViewModel/Screen 수정 없음 (입력 UI는 그대로).
- Notion DB 스키마 변경 없음.
- Topic/Newsletter invariant 동작 변경 없음.
- `TagNormalizer`의 시그니처/내부 로직 변경 없음 — 이미 존재하는 `ensureFreeTopicTag(tags: List<String>): List<String>` 그대로 호출.

## Relevant Context
- 기존 invariant gate 사용 예시:
  - `app/src/main/java/com/dailynewsletter/data/repository/TopicRepository.kt` (saveTopic 내부에서 호출)
  - `app/src/main/java/com/dailynewsletter/data/tag/TagNormalizer.kt` (`ensureFreeTopicTag` 정의)
- 수정 대상:
  - `app/src/main/java/com/dailynewsletter/data/repository/KeywordRepository.kt` (addKeyword 메서드)

## Files Likely Involved
- `app/src/main/java/com/dailynewsletter/data/repository/KeywordRepository.kt`

## Files Owned By This Task
- `app/src/main/java/com/dailynewsletter/data/repository/KeywordRepository.kt`

## Files Explicitly Not Owned
- `app/src/main/java/com/dailynewsletter/data/tag/TagNormalizer.kt`
- `app/src/main/java/com/dailynewsletter/ui/keyword/KeywordViewModel.kt`
- `app/src/main/java/com/dailynewsletter/ui/keyword/KeywordScreen.kt`
- 그 외 Topic/Newsletter Repository

## Forbidden Changes
- No new dependency.
- No DB schema change / Room migration.
- No Notion DB property schema change.
- No public API contract change beyond the addKeyword body itself.
- No background scheduling change.
- No broad refactor.
- No formatting-only edits to other files.

## Android Constraints
- Use existing Kotlin style.
- 기존 `import com.dailynewsletter.data.tag.TagNormalizer` 추가만 허용.
- ensureFreeTopicTag 호출 결과를 한 번만 계산해서 createPage + return 양쪽에 동일 사용.

## Acceptance Criteria
- [ ] `KeywordRepository.addKeyword` 내부에서 `TagNormalizer.ensureFreeTopicTag(tags)` 결과를 사용하도록 변경되어 있다.
- [ ] createPage의 `Tags` multi_select 값과 반환 `KeywordUiItem.tags` 값이 동일한 정규화된 리스트를 사용한다.
- [ ] grep `ensureFreeTopicTag` 가 KeywordRepository.kt에 1회 이상 등장한다.
- [ ] 동일 파일 내 다른 메서드(deleteKeyword, toggleResolved 등)는 수정되지 않는다 (diff가 addKeyword 영역에 국한).
- [ ] 빌드/테스트는 환경상 SKIPPED_ENVIRONMENT_NOT_AVAILABLE 보고로 갈음 (사용자가 Android Studio에서 직접 빌드).

## Verification Command Candidates
- `./gradlew :app:assembleDebug` (환경 미가용 시 SKIPPED 보고)
- `./gradlew :app:testDebugUnitTest`
- 정적: `grep -n ensureFreeTopicTag app/src/main/java/com/dailynewsletter/data/repository/KeywordRepository.kt`

## Expected Implementer Output
- Changed file 1개 (KeywordRepository.kt).
- diff 줄 수 5줄 이내 예상.
- 변경 줄/메서드 인용 포함.
- 빌드 결과(또는 SKIPPED 사유) 포함.
- grep 검증 결과 포함.

## 재현/검증 시나리오 (참고)
1. 사용자: 키워드 화면에서 태그 입력 없이 "eBPF" 키워드 등록.
2. 기대: Notion Keywords DB의 새 row Tags 셀에 `모든주제` 칩 1개 표시.
3. 사용자: 태그 `리눅스` 입력하고 키워드 등록.
4. 기대: Tags 셀에 `리눅스`, `모든주제` 둘 다 표시.
