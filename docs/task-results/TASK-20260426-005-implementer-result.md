---
task_id: TASK-20260426-005
date: 2026-04-26
status: IMPLEMENTED
---

# Implementer Result: newsletter-shelf Scope A

## Changed Files

| File | Change | Delta |
|------|--------|-------|
| `data/remote/notion/NotionApi.kt` | `getPage(pageId)` 엔드포인트 추가 | +6 lines |
| `data/remote/notion/NotionModels.kt` | `NotionFilter`에 `number` 필드 추가 + `NotionNumberFilter` 신설 | +11 lines |
| `service/NotionSetupService.kt` | Topics DB Status 옵션에 `consumed` 추가 | +1 line |
| `data/repository/NewsletterRepository.kt` | `saveNewsletter` 반환 `Unit→String`, `getNewsletter(id)` + `findUnprintedByTagAndPages` 신설, `printNewsletter` 내부를 `getNewsletter` 사용으로 교체 | +80 lines |
| `data/repository/TopicRepository.kt` | `findPendingTopicsByTag(tag)` + `markTopicsConsumed(ids)` 신설 (additive) | +57 lines |
| `service/NewsletterGenerationService.kt` | `generateAndSaveNewsletter()` 제거, `generateForSlot(tag, pageCount): GeneratedNewsletter` + `GeneratedNewsletter` 데이터 클래스 신설 | 전면 재작성 (~130 lines) |
| `service/NotificationHelper.kt` | 신설 — `@Singleton notify(title, message, channelId)` | +44 lines (신규) |
| `ui/newsletter/NewsletterViewModel.kt` | `NewsletterGenerationService` + `NotificationHelper` 주입, `ManualGenStatus` sealed class 추가, `generateNewsletterManually(tag, pageCount)` + `clearManualGenStatus()` 신설 | +40 lines |
| `ui/newsletter/NewsletterScreen.kt` | TopAppBar `+` 액션 추가, `GenerateNewsletterSheet` BottomSheet 컴포저블 신설, 스낵바 연동, 카드 상태 뱃지 "생성됨"/"프린트 완료"/"실패", 빈 상태 문구 변경 | +80 lines |
| `worker/NewsletterWorker.kt` | 삭제 | -27 lines |
| `worker/WorkScheduler.kt` | `scheduleNewsletterGeneration` 메서드 + 호출 제거, `SettingsRepository` 의존성 제거, 양쪽 legacy worker cancel 추가, `suspend` 제거 | -32 lines |
| `docs/plans/newsletter-shelf/README.md` | 진행률 1·2·3·4·6·8·9·10·12 `[x]` | 갱신 |
| `docs/plans/newsletter-shelf/05-checklist.md` | 1·2·3·4·6·8·9·10 단계 체크박스 `[x]` | 갱신 |
| `docs/status.md` | newsletter-shelf 진행 상태 갱신 | 갱신 |

## TASK-004 산출물 확인 결과

`grep -n "scheduleNewsletterGeneration\|scheduleDailyTopicSelection" WorkScheduler.kt` 결과:
- `scheduleNewsletterGeneration`: **line 28, 32 — STILL PRESENT** (TASK-004가 처리하지 않은 상태)
- `scheduleDailyTopicSelection`: 0 matches (TASK-004가 이미 제거)

판단: TASK-004는 `scheduleNewsletterGeneration`을 제거하지 않았다. 따라서 본 task(TASK-005)가 단계 8의 전체 범위(파일 삭제 + 스케줄 제거)를 모두 수행함.

실제 처리:
- `worker/NewsletterWorker.kt` 삭제 완료
- `WorkScheduler.scheduleNewsletterGeneration()` 사설 메서드 제거
- `scheduleAll()` 내 `scheduleNewsletterGeneration(hour, minute)` 호출 제거
- `scheduleAll()` 진입부에 `cancelUniqueWork("newsletter_generation")` + `cancelUniqueWork("daily_topic_selection")` 추가
- `SettingsRepository` 의존성 (더 이상 불필요) 제거 — 생성자 파라미터 삭제, Hilt 자동 처리

## 단계별 요약

### 단계 1 — Notion 모델 확장
- `NotionApi.getPage(pageId: String): NotionPage` — `@GET("v1/pages/{pageId}")` 추가
- `NotionModels.NotionFilter`에 `number: NotionNumberFilter?` 필드 추가
- `NotionNumberFilter` 데이터 클래스 신설 (equals/doesNotEqual/greaterThan/lessThan)
- `NotionSetupService` Topics DB Status 옵션에 `NotionSelectOption("consumed", "default")` 추가

### 단계 2 — NewsletterRepository 신규 메서드
- `saveNewsletter(...)`: 반환 타입 `Unit → String` (createPage 결과 `page.id` 반환). TODO 주석으로 richText 2000자 한계 명시.
- `getNewsletter(id: String): NewsletterUiItem`: `notionApi.getPage(id)` 호출 후 파싱
- `findUnprintedByTagAndPages(tag, pageCount)`: AND 필터 3개 (Tags contains + Status=generated + Page Count=pageCount)
- `printNewsletter(id)`: `getNewsletters()` → `getNewsletter(id)` 으로 교체 (N+1 부분 개선)
- 기존 `findUnprintedNewsletterByTag`, `updateNewsletterStatus` 유지

### 단계 3 — TopicRepository 신규 메서드 (additive)
- `findPendingTopicsByTag(tag)`: AND 필터 2개 — `Tags.multi_select.contains tag` AND `Status.does_not_equal consumed`
- `markTopicsConsumed(ids)`: 순차 루프, 개별 실패 시 `Log.w` + 계속 진행

### 단계 4 — generateForSlot 재작성
- `generateAndSaveNewsletter()` 제거
- `GeneratedNewsletter(id, title, html, selectedTopicIds, tags)` 데이터 클래스 신설
- `generateForSlot(tag, pageCount)`: findPendingTopicsByTag → Claude JSON 호출 → 응답 파싱 → saveNewsletter → markTopicsConsumed
- 프롬프트: "N ≤ pageCount × 2" 상한 가이드 + 목표 글자수 pageCount*1800 포함
- 응답 초과 시 `Log.w` (코드 강제 X — html-id 불일치 회피)
- 응답 파싱: 수동 JSON 파싱 (추가 라이브러리 불필요). selectedTopicIds, titleSuffix, html 필드 추출.

### 단계 6 — NotificationHelper 신설
- `service/NotificationHelper.kt`: `@Singleton @Inject constructor(@ApplicationContext context: Context)`
- `notify(title, message, channelId)`: NotificationCompat.Builder로 알림 발송
- 알림 ID: 1003 (PrintWorker의 1002와 분리)

### 단계 8 — NewsletterWorker + 스케줄 제거
- `worker/NewsletterWorker.kt` 삭제
- `WorkScheduler`: `scheduleNewsletterGeneration` private 메서드 제거, scheduleAll에서 호출 제거, cancel 2개 추가 (`newsletter_generation`, `daily_topic_selection`), `SettingsRepository` 파라미터 제거

### 단계 9 — NewsletterViewModel orchestration
- `generateNewsletterManually(tag, pageCount)`: viewModelScope.launch → RunCatching → onSuccess 성공 후 loadNewsletters() + NotificationHelper.notify + ManualGenStatus.Success emit → onFailure 실패 시 NotificationHelper.notify + ManualGenStatus.Failed emit
- `ManualGenStatus` sealed class: Idle / Running / Success(title) / Failed(message)
- `clearManualGenStatus()`: Idle로 초기화

### 단계 10 — NewsletterScreen UI 변경
- TopAppBar actions: 기존 Print 아이콘 + 신규 Add 아이콘 (`+`) — 뷰 상태에 따라 표시
- `showGenerateSheet` remember 상태로 BottomSheet 토글
- `GenerateNewsletterSheet`: `ModalBottomSheet`, 태그 `OutlinedTextField` (기본값 "모든주제"), 장수 `Slider` (1f..5f, steps=3), 취소/생성 버튼
- `LaunchedEffect(state.manualGenStatus)` → snackbarHostState.showSnackbar
- 카드 상태 뱃지: "생성됨" / "프린트 완료" / "실패" (진열대 어휘 없음)
- 빈 상태: "아직 뉴스레터가 없습니다. 상단 +로 직접 생성할 수 있어요."

### 단계 12 — 산출물 상태 갱신
- `docs/plans/newsletter-shelf/README.md`: 진행률 1·2·3·4·6·8·9·10·12 `[x]`
- `docs/plans/newsletter-shelf/05-checklist.md`: 1·2·3·4·6·8·9·10 단계 체크박스 `[x]`
- `docs/status.md`: "진행중" → "코드 완료 / 빌드 검증 SKIPPED_ENVIRONMENT_NOT_AVAILABLE"

## Acceptance 체크 결과

- [x] `NotionApi.getPage(pageId)` 존재 — NotionApi.kt line 48
- [x] `TopicRepository.findPendingTopicsByTag(tag)` / `markTopicsConsumed(ids)` 존재 — TopicRepository.kt lines 96, 133
- [x] `NewsletterRepository.findUnprintedByTagAndPages(tag, pages)` / `getNewsletter(id)` / `updateNewsletterStatus(id, status)` 존재, 기존 `findUnprintedNewsletterByTag` 유지
- [x] `NewsletterGenerationService.generateForSlot(tag, pageCount): GeneratedNewsletter` 존재, 내부에서 saveNewsletter 직후 `markTopicsConsumed` 호출
- [x] `service/NotificationHelper.kt` 신설
- [x] `NewsletterViewModel.generateNewsletterManually(tag, pageCount)` 존재
- [x] `NewsletterScreen` TopAppBar `+` 액션 + `GenerateNewsletterSheet` ModalBottomSheet 존재
- [x] 카드 상태 뱃지에 "진열대" 텍스트 없음 (grep 0 matches)
- [x] `worker/NewsletterWorker.kt` 삭제 완료 (find 0 results)
- [x] `WorkScheduler.scheduleAll()`에 `scheduleNewsletterGeneration` 호출 없음 (grep 0 matches)
- [ ] `./gradlew :app:assembleDebug` — **SKIPPED_ENVIRONMENT_NOT_AVAILABLE** (Java 미설치 환경)
- [ ] `./gradlew :app:testDebugUnitTest` — **SKIPPED_ENVIRONMENT_NOT_AVAILABLE**
- [x] `docs/plans/newsletter-shelf/README.md` + `05-checklist.md` 단계 1·2·3·4·6·9·10 `[x]`, 8단계 `[x]` (완료)
- [x] `docs/status.md` 갱신

## 빌드/테스트 명령

```
BUILD: SKIPPED_ENVIRONMENT_NOT_AVAILABLE
TEST: SKIPPED_ENVIRONMENT_NOT_AVAILABLE
```

사유: 로컬 Java 미설치 환경 (이전 TASK-003, TASK-004와 동일 상태).

## 정적 Grep 검증 결과

```
find app/src/main/java -name 'NewsletterWorker.kt'
  → (결과 없음 — 삭제 확인)

grep -n "scheduleNewsletterGeneration" .../worker/WorkScheduler.kt
  → No matches found (0 matches)

grep -n "generateForSlot" .../service/NewsletterGenerationService.kt
  → 36:    suspend fun generateForSlot(tag: String, pageCount: Int): GeneratedNewsletter {
  (정의 1회 등장)

grep -n "markTopicsConsumed" .../service/NewsletterGenerationService.kt .../data/repository/TopicRepository.kt
  → NewsletterGenerationService.kt:117: topicRepository.markTopicsConsumed(selectedTopicIds)
  → TopicRepository.kt:133: suspend fun markTopicsConsumed(ids: List<String>) {
  → TopicRepository.kt:150: android.util.Log.w("TopicRepository", "markTopicsConsumed failed...")
  (각각 1회 이상 등장)

grep -rn "진열대" app/src/main/
  → No matches found (0 matches)
```

## Forbidden Changes — Skip 기록

- `KeywordRepository.kt` — 미접촉
- `ClaudeTopicSuggester.kt` — 미접촉
- `PrintService.kt` — 미접촉
- `worker/PrintWorker.kt` — 미접촉
- `worker/CleanupWorker.kt` — 미접촉
- `ui/keyword/*`, `ui/topics/*`, `data/tag/*` — 미접촉
- `AndroidManifest.xml`, `app/build.gradle.kts` — 미접촉
- Room schema, DB migration — 변경 없음

## Notes for Verifier

**중요 파일:**
- `/app/src/main/java/com/dailynewsletter/service/NewsletterGenerationService.kt` — 핵심 변경
- `/app/src/main/java/com/dailynewsletter/data/repository/TopicRepository.kt` — additive 변경
- `/app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterScreen.kt` — UI 변경
- `/app/src/main/java/com/dailynewsletter/worker/WorkScheduler.kt` — scheduleNewsletterGeneration 제거 확인

**주의사항:**
1. `NewsletterGenerationService`의 JSON 파싱은 수동 구현 (Gson 사용 안 함). Claude 응답 형식 변동 시 파싱 실패 가능 — 실 E2E에서 확인 필요.
2. `WorkScheduler.scheduleAll()`이 `suspend`에서 일반 함수로 변경됨. 호출 사이트가 없으므로 영향 없음. 단, 추후 호출 시 coroutine scope 불필요.
3. `NotificationHelper` 알림 ID는 1003 (PrintWorker 1002와 분리).
4. Compose `ModalBottomSheet`은 `@OptIn(ExperimentalMaterial3Api::class)` 적용됨.
5. `mutableStateOf(2f)` 사용 (`mutableFloatStateOf` 대신) — 범용 호환성.

**권장 확인:**
- `./gradlew :app:assembleDebug` (Java 있는 환경에서)
- `generateForSlot` 호출 경로: NewsletterScreen `+` 탭 → BottomSheet → 생성 버튼 → ViewModel.generateNewsletterManually → Service.generateForSlot → Repository.saveNewsletter → Repository.markTopicsConsumed
