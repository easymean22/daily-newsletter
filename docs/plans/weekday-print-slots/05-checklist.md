---
updated: 2026-04-19
summary: "implementer 체크리스트 10단계 (전제: newsletter-shelf + tag-system 4단계까지 완료)"
parent: ./README.md
---

# 단계별 플랜 (implementer 체크리스트)

> **전제 순서**:
> - [tag-system/](../tag-system/README.md) 1~4단계 (Notion multi_select + 정규화 + Setup 시드 + Repository 시그니처) 완료.
> - [newsletter-shelf/](../newsletter-shelf/README.md) 1~8단계 (PrintOrchestrator 신설, NewsletterWorker 제거, PrintWorker 변경) 완료 또는 병행.
>
> 본 플랜은 newsletter-shelf의 `getSlotsForDay` stub을 실제 구현으로 교체한다. newsletter-shelf보다 늦게 진입.

## 1단계: Slot/DaySchedule/WeeklySchedule 도메인 모델 + JSON 직렬화

- [ ] `data/schedule/Slot.kt` 신설. `data class Slot(val tag: String, val pages: Int)`.
- [ ] `data/schedule/DaySchedule.kt` 신설. `data class DaySchedule(val enabled, val hour, val minute, val slots)`.
- [ ] `data/schedule/WeeklySchedule.kt` 신설. `Map<DayOfWeek, DaySchedule>` + `slotsForDay`, `scheduledDays`, `default()`.
- [ ] `data/schedule/WeeklyScheduleJson.kt` — Gson 직렬화/역직렬화 유틸. `toJson / fromJson` + 방어적 파싱 (손상 시 default 복귀).
- [ ] **JVM unit test 8개**: default, 빈 JSON, 일부 요일만 있는 JSON, 전체 요일 JSON, 잘못된 hour(25) 방어, 잘못된 pages(0) 방어, 미지의 요일 키 무시, 순수 slots만 역직렬화.

## 2단계: `SettingsRepository` 확장

- [ ] `SettingsEntity.KEY_WEEKLY_SCHEDULE = "schedule_v1"` 상수 추가.
- [ ] `SettingsRepository`에 Gson 주입 — 기존 `@Provides` Gson 확인 후 같은 인스턴스 재사용 (DI 모듈 변경 필요 시 `NetworkModule`/`AppModule` 점검).
- [ ] `suspend fun getWeeklySchedule(): WeeklySchedule` + `observeWeeklySchedule(): Flow<WeeklySchedule>` + `suspend fun setWeeklySchedule(schedule: WeeklySchedule)` + `suspend fun getSlotsForDay(day: DayOfWeek): List<Slot>` 추가.
- [ ] 기존 `getPrintTimeHour/Minute`, `getNewsletterPages`는 **`@Deprecated("use getWeeklySchedule()")`** 표시. 제거는 호출 지점이 모두 사라진 뒤 별도 커밋.
- [ ] 마이그레이션 함수 `suspend fun migrateLegacyPrintTimeIfNeeded()` 추가. 호출 지점은 5단계의 `WorkScheduler.scheduleAll()` 진입부 또는 ViewModel init.

## 3단계: Tag 옵션 풀 헬퍼 (tag-system 5단계 이월)

- [ ] `NotionApi`에 `@GET("v1/databases/{id}") suspend fun getDatabase(@Path("id") id: String, @Header("Notion-Version") ver: String = "2022-06-28"): NotionDatabaseResponse` 추가.
- [ ] `data/remote/notion/NotionModels.kt`에 `NotionDatabaseResponse(properties: Map<String, NotionPropertySchema>)` 확장. `NotionPropertySchema.multiSelect`는 이미 tag-system 1단계에서 추가됨 — 재사용.
- [ ] `NewsletterRepository.listAvailableTagNames(): List<String>` 신설. `getDatabase(newslettersDbId)` → properties["Tags"].multiSelect.options.map { it.name }.
- [ ] **메모리 캐시 5분 TTL**: `@Singleton` 내부에 `var cachedTagNames: Pair<List<String>, Long>? = null`, 5분 만료. 실패 시 `emptyList()`로 fallback.
- [ ] tag-system README 진행률 5단계를 `[x]` 처리하고 본 플랜에서 완료했음을 `consumed_by`에 기록.

## 4단계: `WorkScheduler.scheduleWeeklyPrints()` 재설계

- [ ] 기존 `scheduleNewsletterGeneration` / `scheduleDailyTopicSelection` 제거 확인 (newsletter-shelf / topic-generation-paths 플랜 몫 — 본 플랜 진입 시 이미 제거되어 있어야 함).
- [ ] `schedulePrint()` 메서드 **제거** → `scheduleWeeklyPrints()` 신설.
- [ ] `scheduleAll()` 진입부 cancel 목록에 `"daily_print"` 추가:
  ```kotlin
  workManager.cancelUniqueWork("daily_print")
  workManager.cancelUniqueWork("newsletter_generation")
  workManager.cancelUniqueWork("daily_topic_selection")
  ```
- [ ] `scheduleWeeklyPrints()` 구현 — 03-scheduler.md §"WorkScheduler 재설계" 참고.
- [ ] `calculateDelayUntilNextOccurrence(day, hour, minute)` 유틸 추가 (dayOfWeek.value - now.dayOfWeek.value 계산 주의).
- [ ] `scheduleAll()` 진입부에 `settingsRepository.migrateLegacyPrintTimeIfNeeded()` 호출 배치.
- [ ] **JVM unit test 5개** (calculateDelayUntilNextOccurrence만): 오늘 미래 시각, 오늘 과거 시각(=다음 주), 내일, 같은 요일 다른 시각, 일요일→월요일 roll over.

## 5단계: `PrintWorker` 요일 입력 + `PrintOrchestrator.runForDay`

- [ ] `PrintWorker.doWork()`가 `inputData.getString("day_of_week")` → `DayOfWeek.valueOf(...)` → `printOrchestrator.runForDay(day)` 호출.
- [ ] `inputData` 파싱 실패 시 `Result.failure()` + 로그.
- [ ] 재시도 정책 `runAttemptCount < 1`로 — spec §6 "당일 1회 재시도"와 정합. (기존 `< 3`에서 변경).
- [ ] `PrintOrchestrator`에 `runForDay(day: DayOfWeek): Unit` 신설. 기존 `runForToday()`는 `runForDay(LocalDate.now().dayOfWeek)`로 위임하거나 제거.
- [ ] `getSlotsForDay(day)` 호출 — newsletter-shelf의 stub이 실제 구현으로 교체됨.

## 6단계: `WeeklyScheduleViewModel` + 상태 모델

- [ ] `ui/schedule/WeeklyScheduleViewModel.kt` 신설. `@HiltViewModel`.
- [ ] 의존성: `SettingsRepository`, `NewsletterRepository`, `WorkScheduler`.
- [ ] `WeeklyScheduleUiState` / `SlotEditTarget` / `SaveStatus` / `ValidationResult` sealed 클래스 정의 — 04-ui.md §"UI 상태 모델" 그대로.
- [ ] `uiState`: `combine(observeWeeklySchedule, ..., availableTagNames)` → StateFlow.
- [ ] mutator 메서드:
  - `toggleDay(day, enabled)` — 스키마 업데이트 + `setWeeklySchedule` + `workScheduler.scheduleAll()`.
  - `setDayTime(day, hour, minute)` — 동일 흐름.
  - `openSlotEdit(day, slotIndex?)` — `editingSlot` 설정.
  - `updateEditingSlotTag(tag)` / `updateEditingSlotPages(pages)` — validation 즉시 갱신.
  - `saveEditingSlot()` — validation 성공 시 저장 + scheduleAll().
  - `deleteEditingSlot()` — 슬롯 제거 + 저장.
  - `dismissSlotEdit()`, `openTimeEdit(day)`, `dismissTimeEdit()`.
- [ ] 유일성 검증 `validateSlotInsert(day, newSlot, editingIndex)` 구현 — 02-data.md §"유일성 강제 로직" 그대로.
- [ ] 에러 핸들링: `runCatching { setWeeklySchedule(...); scheduleAll() }` → SaveStatus 업데이트.

## 7단계: `SettingsScreen` 진입점 + `WeeklyScheduleScreen` 화면

- [ ] `SettingsScreen`에서 기존 **"프린트 시간" Card + "뉴스레터 분량" Slider 제거**. 대신 "프린트 스케줄" Card 추가 — 탭 시 `onNavigateToWeeklySchedule()` 콜백 호출.
- [ ] `SettingsScreen`이 `onNavigateToWeeklySchedule: () -> Unit` 파라미터 받도록 시그니처 변경. 호출자(Navigation host)에서 `navController.navigate("settings/weekly-schedule")` 바인딩.
- [ ] Navigation graph(`ui/DailyNewsletterApp.kt` 또는 `ui/navigation/*`)에 `composable("settings/weekly-schedule") { WeeklyScheduleScreen(...) }` 추가. TopAppBar `navigationIcon`에 back arrow.
- [ ] `ui/schedule/WeeklyScheduleScreen.kt` 신설. LazyColumn of 7 day cards.
- [ ] 요일 카드 구조:
  - 상단 Row: 요일 이름 + Switch (enabled 토글).
  - enabled=true면:
    - 시각 Card (탭 시 TimePicker Dialog).
    - 슬롯 리스트 + "[+ 슬롯 추가]" 버튼.
  - enabled=false면 "요일 OFF — 토글을 눌러 활성화" 안내.

## 8단계: Slot 편집 BottomSheet (태그/장수 + 유일성 검증)

- [ ] `ModalBottomSheet` — `editingSlot != null`일 때 표시.
- [ ] 태그 드롭다운 (`ExposedDropdownMenuBox`):
  - 옵션 = `availableTagNames`.
  - 이미 같은 요일에 등록된 태그(편집 중 슬롯 본인 제외)는 `(이미 추가됨)` 부제 + disabled.
  - 옵션 풀이 비어 있으면 disabled + "태그가 없습니다 ..." 안내 + [주제로 이동] 버튼(KeywordsScreen 또는 TopicsScreen으로 이동).
- [ ] 장수 Stepper — 1~5 범위.
- [ ] 저장 버튼: `validation == Ok`일 때만 enabled.
- [ ] 삭제 버튼: 편집 모드일 때만 노출.
- [ ] 취소: `dismissSlotEdit()`.
- [ ] SaveStatus를 SnackbarHost와 연동: `Saved` → "저장됐어요", `Failed(m)` → "저장 실패: m".

## 9단계: 기존 단일 프린트 시각 설정 deprecated 처리 + 마이그레이션 검증

- [ ] 앱 첫 기동 시 `migrateLegacyPrintTimeIfNeeded()` 실행. 기존 레거시 값 존재 시 7요일 동일 설정(`enabled=true, slots=[("모든주제", pages)]`)으로 변환.
- [ ] `SettingsViewModel`의 `SettingsUiState`에서 `printTimeHour/Minute`, `newsletterPages` 필드 제거. `SettingsScreen`의 관련 UI 제거 확인.
- [ ] 기존 `SettingsRepository.getPrintTimeHour / getPrintTimeMinute / getNewsletterPages` getter **제거**. 호출 지점이 남아있으면 컴파일 실패 — 즉시 수정.
- [ ] `SettingsEntity.KEY_PRINT_TIME_HOUR / KEY_PRINT_TIME_MINUTE / KEY_NEWSLETTER_PAGES` 상수는 마이그레이션 코드에서만 사용 — 실제 DB row는 사용자 단말에 남아있을 수 있음. 제거 커밋은 릴리스 하드닝.

## 10단계: 수동 E2E 검증 + 산출물 상태 갱신

- [ ] **새 설치 E2E**:
  - 앱 신규 설치 → Settings 화면 → "프린트 스케줄" 탭 → WeeklyScheduleScreen 진입.
  - 모든 요일 OFF 기본 상태 확인.
  - 월요일 ON + 07:00 + 슬롯 `(모든주제, 2장)` 추가.
  - Notion Topics DB에 `모든주제` 태그 주제 여러 개 준비 (topic-generation-paths 경로로).
  - 당일 요일을 월요일로 맞추고 시각을 +5분 후로 변경 → 저장 → `dumpsys jobscheduler`에 `daily_print_MONDAY` 등장 확인.
  - 5분 대기 → PrintWorker 실행 → PrintOrchestrator.runForDay(MONDAY) → generateForSlot("모든주제", 2) x 2 → 1 프린트, 1 비축.
- [ ] **유일성 E2E**:
  - 월요일에 이미 `(IT, 2장)`이 있는 상태에서 Slot 추가 BottomSheet → 드롭다운에서 `IT`이 disabled/`(이미 추가됨)` 표시 확인.
  - 해당 슬롯 편집으로 들어가면 `IT`이 (이미 추가됨) 표시 없이 선택된 상태로 복원 확인.
- [ ] **OFF 토글 E2E**:
  - 월요일 OFF 전환 → `daily_print_MONDAY` unique work cancel 확인 (`dumpsys` 에서 사라짐).
  - 다시 ON → re-enqueue 확인.
- [ ] **다음날 재진입 E2E**:
  - 다음 요일로 날짜 조정(또는 실제 다음날) → 다음날 요일 work가 예정대로 실행되는지.
- [ ] **레거시 마이그레이션 E2E** (기존 사용자 시나리오):
  - 기존 단일 프린트 시각 값이 있는 상태에서 본 플랜 빌드 설치 → WeeklyScheduleScreen 진입 → 모든 요일에 `(모든주제, N장)` 단일 슬롯 + 기존 시각으로 채워졌는지 확인.
- [ ] **파싱 실패 복구 E2E**:
  - `adb shell` 또는 Room Inspector로 `schedule_v1` row를 잘못된 JSON으로 수정 → 앱 재시작 → 기본 스케줄 + 스낵바 "설정이 손상되어 초기화했습니다" 확인.
- [ ] 산출물 상태 갱신:
  - 본 플랜 README frontmatter `status: review → accepted → in-progress → consumed` 전이.
  - ADR-0006 `draft → accepted`.
  - `docs/status.md` 해당 라인을 archive로 이관.
  - tag-system 플랜 `consumed_by`에 "2026-MM-DD implementer: 5단계(listAvailableTagNames)" 추가.
  - newsletter-shelf 플랜 `consumed_by`에 "2026-MM-DD implementer: runForDay 치환 + getSlotsForDay stub 제거" 추가.
