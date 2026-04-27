# Task Brief: 뉴스레터 화면 갤러리 UI (제목 + 빈 썸네일)

Task ID: TASK-20260426-014
Status: active

## Goal
뉴스레터 화면을 세로 리스트(`LazyColumn`)에서 **2열 카드 그리드**(`LazyVerticalGrid`)로 전환. 각 카드는 **제목(필수) + 표지 이미지 영역(현재는 빈 placeholder)**.

## User-visible behavior
- 뉴스레터 탭 진입 → 카드 그리드(2열) 표시.
- 각 카드:
  - 위쪽: 정사각/16:9 비율 빈 회색 박스 (썸네일 placeholder).
  - 아래쪽: 뉴스레터 제목 (한 줄 또는 두 줄 ellipsis).
- 데이터 0건일 때: 기존 빈 상태 메시지 유지(가능하면 그대로 사용; 없으면 "뉴스레터가 없습니다" 등 단순 텍스트).
- 우상단 `+` 액션(수동 생성 트리거 BottomSheet)은 그대로 유지.

## Out of Scope
- 썸네일 실제 채우기 — **추후** PDF 변환 → 이미지 추출 → Notion 업로드 파이프라인이 추가될 때까지 placeholder 유지.
- 카드 클릭 시 상세 화면 이동 — 후속 결정. 일단 클릭 동작 없음 또는 기존 동작 유지.
- 카드에 날짜/페이지수/태그/Status 표시 — 사용자 지시상 제목만.
- 데이터 모델 변경 없음 (NewsletterUiItem 그대로).
- ViewModel 데이터 흐름 변경 없음 (혹시 필요 시 표지 이미지 URL 필드 nullable로 추가 가능 — 현재는 null로 두고 placeholder 분기).

## Scope
1. `NewsletterScreen.kt`:
   - `LazyColumn` → `LazyVerticalGrid(columns = GridCells.Fixed(2))`.
   - 각 카드 Composable: `Card` 또는 `Surface` 안에 상단 빈 Box(예: `Modifier.aspectRatio(1f)` + `Color.LightGray` 또는 `MaterialTheme.colorScheme.surfaceVariant`) + 하단 `Text(title, maxLines=2, overflow=Ellipsis)`.
   - 적당한 padding/spacing.
2. `NewsletterViewModel.kt` (변경 최소):
   - 만약 `NewsletterUiItem`에 표지 이미지 URL 필드가 없으면 추가 불필요 — 현재는 placeholder만 그리므로 필드 없음으로 충분.
   - 표지 이미지 URL 필드를 미리 마련하고 싶다면 nullable `coverImageUrl: String? = null` 추가 가능 (선택).

## Files Owned By This Task
- `app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterScreen.kt`
- `app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterViewModel.kt`

## Files Explicitly Not Owned
- `app/src/main/java/com/dailynewsletter/data/repository/NewsletterRepository.kt`
- `app/src/main/java/com/dailynewsletter/service/NewsletterGenerationService.kt`
- `app/src/main/java/com/dailynewsletter/data/remote/notion/NotionModels.kt`
- 그 외 모든 파일.

## Forbidden Changes
- No new dependency.
- No DB schema 변경.
- No 데이터 흐름/Repository 변경.
- 우상단 `+` 액션 제거 금지 (수동 생성 트리거 보존).
- TASK-007/008/009/010/011/012 변경분 보존.
- BottomSheet 동작/UI 보존 (탭 액션 + 시트 본문은 그대로).

## Android Constraints
- Compose Material3 `LazyVerticalGrid` 사용.
- `aspectRatio` modifier로 썸네일 자리 일정.
- `MaterialTheme` 색상/타이포 활용 (직접 hex 색 명시 지양).
- 카드 사이 간격 8~12dp 권장.

## Acceptance Criteria
- [ ] NewsletterScreen.kt에서 `LazyVerticalGrid` 사용 (`LazyColumn` 제거 또는 갤러리 아닌 다른 용도로만).
- [ ] 각 카드에 빈 회색 박스 + 제목 텍스트가 그려진다.
- [ ] 0건일 때 빈 상태 메시지가 유지/표시된다.
- [ ] 우상단 `+` 아이콘 액션과 BottomSheet 트리거가 보존된다.
- [ ] grep `LazyVerticalGrid` → 1+ hit, `aspectRatio` → 1+ hit (또는 다른 비율 modifier).
- [ ] 빌드 성공 또는 SKIPPED_ENVIRONMENT_NOT_AVAILABLE.

## Verification Command Candidates
- `grep -n "LazyVerticalGrid\|aspectRatio" app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterScreen.kt`
- `./gradlew :app:assembleDebug`

## Expected Implementer Output
- 변경 파일 1~2개.
- 신규 카드 Composable 핵심 인용.
- grep 결과.
- 빌드 결과(또는 SKIPPED).
- 사용자 다음 동작 1줄.

## STOP_AND_ESCALATE
- 1열 vs 2열, 정사각 vs 16:9 등 시각적 결정이 본 Brief에 명시되지 않은 곳에서 사용자 의도 분기가 의심되면 escalate. 본 Brief 기준: 2열 + 정사각.
