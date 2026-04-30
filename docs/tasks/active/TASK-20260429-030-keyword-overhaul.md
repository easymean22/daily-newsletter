# Task Brief: 키워드 오버홀 — type 제거 + 긴글 본문화 + 시간 표시 + 태그 바 + 필터

Task ID: TASK-20260429-030
Status: active

## Goal
키워드 화면 5가지 변경:
1. type(키워드/메모) 구분 제거 — 모두 일반 키워드로 통일.
2. 긴 글: 제목은 "앞부분..." 으로 줄임, 원문은 Notion 페이지 본문(paragraph block).
3. 등록 시간 표시 (Notion `created_time` 활용).
4. 키워드 탭 상단에 태그 chip 바 — 전체 태그 표시 + `+` 버튼으로 추가 + 길게 눌러 삭제 (확인 다이얼로그 후).
5. 태그 chip 클릭 시 해당 태그로 키워드 목록 필터.

## User-visible behavior
- 키워드 추가 다이얼로그/시트에서 "type" 선택 사라짐 (텍스트 + 태그만).
- 긴 키워드(예: 100자 이상) 입력 시 카드에는 "앞부분 50자..." 표시, Notion 페이지 본문에 원문 paragraph 1개로 저장.
- 카드에 "2시간 전" 또는 "2026-04-29 14:30" 같은 등록 시간 표시.
- 화면 상단 chip 바에 모든 태그 (예: 모든주제 / 경제 / 기술). 선택된 chip만 강조, 그 외 회색.
- chip 끝에 `+` 버튼 → 새 태그 입력 다이얼로그.
- chip 길게 누름 → "이 태그를 삭제하시겠습니까?" 확인 후 삭제 (그 태그를 가진 모든 키워드의 multi_select에서 제거).
- chip 클릭 → 해당 태그를 가진 키워드만 표시.

## Scope

### 1. `app/src/main/java/com/dailynewsletter/data/repository/KeywordRepository.kt`
- 새 helper: 긴 텍스트(예: 80자 초과) → 제목 = "앞 50자..." / 본문 = paragraph block 1개로 저장.
- `addKeyword(text, type, tags)` 시그니처에서 `type` 인자 제거. 내부 Notion 저장 시 Type property에는 항상 "keyword" 같은 default 값 보냄(스키마 호환).
- 새 데이터 모델: `KeywordUiItem`에 `createdTime: String` 또는 `Instant` 추가 (Notion `created_time` 매핑).
- `getAllTags()` 함수 추가: 모든 키워드의 태그 union 반환 (Set<String>).
- `removeTagFromAllKeywords(tag)` 함수 추가: 그 태그를 가진 모든 키워드의 multi_select에서 해당 태그 제거.

### 2. `app/src/main/java/com/dailynewsletter/ui/keyword/KeywordViewModel.kt`
- `addKeyword(text, type, tags)` → `addKeyword(text, tags)` (type 인자 제거).
- 신규 state field: `availableTags: Set<String>`, `selectedTagFilter: String?`.
- 새 함수:
  - `loadTags()`: repository.getAllTags() 결과로 availableTags 갱신.
  - `selectTagFilter(tag: String?)`: selectedTagFilter 변경.
  - `addNewTag(tag: String)`: availableTags에 추가 (실제로는 빈 키워드 만들지 않고, UI에서 그 다음 키워드 추가 시 그 태그가 선택지에 보이게 함). 또는 별도 SettingsRepository에 'all_tags' 키로 영속화 — 결정은 implementer.
  - `removeTag(tag: String)`: repository.removeTagFromAllKeywords(tag) 호출.
- 키워드 list filtered: selectedTagFilter가 null이면 전체, 아니면 그 태그 가진 것만. UiState에서 derivable.
- `clearAutoGenStatus`/`AutoGenStatus` 등 dead code 정리는 out of scope (남겨도 OK).

### 3. `app/src/main/java/com/dailynewsletter/ui/keyword/KeywordScreen.kt`
- 상단(TopAppBar 아래)에 chip 바:
  ```kotlin
  LazyRow {
      items(state.availableTags.toList()) { tag ->
          FilterChip(
              selected = tag == state.selectedTagFilter,
              onClick = { viewModel.selectTagFilter(if (tag == state.selectedTagFilter) null else tag) },
              onLongClick = { showDeleteDialog = tag },
              label = { Text(tag) }
          )
      }
      item {
          IconButton(onClick = { showAddTagDialog = true }) { Icon(Icons.Default.Add, ...) }
      }
  }
  ```
- 키워드 추가 시트/다이얼로그에서 type 선택 UI 제거.
- 키워드 카드에:
  - 등록 시간 표시 (예: `formatTime(item.createdTime)`).
  - 긴 글 ellipsis는 백엔드 결과(title)를 그대로 보여주면 됨.
- 태그 추가 다이얼로그 + 태그 삭제 확인 다이얼로그.

### 4. `app/src/main/java/com/dailynewsletter/service/NotionSetupService.kt`
- 신규 DB 스키마는 그대로 ("Type" property 유지). 본 Task에서는 변경 0.
- 기존 사용자 DB도 그대로.

## Out of Scope
- 주제 화면의 태그 시스템 — TASK-031.
- Type property를 Notion에서 제거 — 후속 (스키마 변경 escalation 영역).
- AutoGenStatus dead code 청소 — 후속.
- 시간 표시 포맷의 정교한 i18n — 단순 "yyyy-MM-dd HH:mm" 또는 "N분 전" 중 하나로 OK.

## Files Owned By This Task
- `app/src/main/java/com/dailynewsletter/data/repository/KeywordRepository.kt`
- `app/src/main/java/com/dailynewsletter/ui/keyword/KeywordViewModel.kt`
- `app/src/main/java/com/dailynewsletter/ui/keyword/KeywordScreen.kt`

## Files Explicitly Not Owned
- `app/src/main/java/com/dailynewsletter/service/*` (Gemini 관련 — 병렬 TASK-038 소유)
- `app/src/main/java/com/dailynewsletter/ui/newsletter/*`
- `app/src/main/java/com/dailynewsletter/ui/topics/*`
- `app/src/main/java/com/dailynewsletter/data/repository/TopicRepository.kt`
- `app/src/main/java/com/dailynewsletter/service/NotionSetupService.kt` (스키마 그대로, 변경 불필요)
- 그 외 모든 파일.

## Forbidden Changes
- No new dependency.
- No DB 스키마/migration 변경.
- No 다른 ViewModel/Screen 수정.

## Acceptance Criteria
- [ ] `KeywordRepository.addKeyword`가 type 인자 받지 않음 (또는 default로 무시).
- [ ] 긴 텍스트(예: 80자+) 저장 시 Notion page body에 paragraph block 추가되는 코드 경로 존재.
- [ ] `KeywordUiItem`에 createdTime 필드.
- [ ] `KeywordRepository.getAllTags()` 함수 존재.
- [ ] `KeywordRepository.removeTagFromAllKeywords(tag)` 함수 존재.
- [ ] `KeywordViewModel.UiState`에 `availableTags`, `selectedTagFilter` 필드.
- [ ] `KeywordScreen`에 LazyRow + FilterChip 또는 동등한 chip 바.
- [ ] 키워드 카드에 시간 표시.
- [ ] 빌드 성공 또는 SKIPPED_ENVIRONMENT_NOT_AVAILABLE.

## Verification Command Candidates
- `grep -n "getAllTags\\|removeTagFromAllKeywords\\|availableTags\\|selectedTagFilter\\|createdTime" app/src/main/java/com/dailynewsletter/{data,ui/keyword}`
- `./gradlew :app:assembleDebug`

## Expected Implementer Output
- 변경 3 파일. 핵심 코드 인용 (Repository 새 함수 + ViewModel state + Screen chip 바). grep 결과. 빌드 결과. 사용자 다음 동작 1줄.

## STOP_AND_ESCALATE
- 긴 키워드 저장 시 Notion 페이지 body에 block 추가하는 API가 setup된 적 없으면 — 이미 NewsletterRepository.saveNewsletter가 children 배열로 추가하는 패턴 사용 중. 같은 패턴 적용. escalate 불필요.
- 태그 chip 바가 화면 너비 좁을 때 깨지면 horizontal scroll(LazyRow)이 정상이라 escalate 불필요.
- `addKeyword(text, type, tags)` 시그니처 변경이 다른 caller(예: 마이그레이션 코드)와 충돌하면 escalate.
