---
updated: 2026-04-19
summary: "implementer 체크리스트 12단계 (전제 플랜 완료 후 진입)"
parent: ./README.md
---

# 단계별 플랜 (implementer 체크리스트)

> **전제 순서**: tag-system 1~4단계(Notion 모델 확장 + 정규화 + Setup 시드 + Repository 시그니처) + topic-generation-paths 2~3단계(사이클 해소, `deleteTodayTopics` 분해, `ClaudeTopicSuggester` 신설, `DailyTopicWorker` 제거)가 먼저 완료되어 있어야 함. 본 플랜은 그 위에 얹힘.

## 1단계: Notion 모델 확장 — `getPage` + Topics.Status `consumed`

- [x] `NotionApi`에 `@GET("v1/pages/{id}")` 엔드포인트 1줄 추가.
- [x] `NotionSetupService.setupDatabases()`의 Topics DB Status select 옵션에 `consumed` 추가 (색상 `default`).
- [x] 기존 setup한 사용자는 Notion UI에서 옵션 추가하거나 reset 경로로 (본 플랜 04-infra.md의 "MVP 본인 1명, 기존 데이터 폐기 가능" 전제).

## 2단계: `NewsletterRepository` 신규 메서드

- [x] `getNewsletter(id): NewsletterUiItem` — 단건 조회 (기존 전체 조회 대체용).
- [x] `findUnprintedByTagAndPages(tag, pageCount): List<NewsletterUiItem>` — `Status.equals generated` AND `Tags.multi_select.contains tag` AND `Page Count.number.equals pageCount`.
- [x] `saveNewsletter(...)` 반환 타입을 `Unit → String`으로 변경 (생성된 page id 반환). 기존 호출자(없을 예정 — NewsletterWorker 제거 단계에서 동반 변경).
- [x] `printNewsletter(id)` 내부를 `getNewsletter(id)` 호출로 교체.

## 3단계: `TopicRepository` 신규 메서드

- [x] `findPendingTopicsByTag(tag: String): List<TopicUiItem>` — `Status.does_not_equal consumed` AND `Tags.multi_select.contains tag`.
- [x] `markTopicsConsumed(ids: List<String>)` — 각 id에 `updatePage(Status = "consumed")` 호출. 순차 구현.
- [x] `TopicUiItem.tags`는 tag-system에서 추가됨 (본 플랜은 읽기만).

## 4단계: `NewsletterGenerationService.generateForSlot` 재작성

- [x] 기존 `generateAndSaveNewsletter()` 메서드 제거.
- [x] 신규 `generateForSlot(tag, pageCount): GeneratedNewsletter` 구현:
  - 주제 후보 조회 → Claude 프롬프트 조립 → Claude 호출 → 응답 파싱.
  - 응답 스키마 `{ selectedTopicIds, titleSuffix, html }`.
  - `newsletterRepository.saveNewsletter(title, fullHtml, selectedTopicIds, pageCount, emptyList())` 호출 후 id 받음.
  - `topicRepository.markTopicsConsumed(selectedTopicIds)` 호출. 실패 시 로그만.
  - 반환: `GeneratedNewsletter(id, title, fullHtml, selectedTopicIds, tags=emptyList())`.
- [x] 프롬프트 설계:
  - 후보 주제 목록 (id + title + priorityType) 전달.
  - 장수 `pageCount` + 목표 글자수 `pageCount * 1800` 명시.
  - "N ≤ pageCount × 2" 상한 가이드 포함. 초과 시 Log.w (코드 강제 X).
  - "선택한 topic ids를 배열로 반환" 명시.
- [x] HTML wrapper (`<!DOCTYPE ... @page size: A4 ...>`)는 기존 로직 재사용.

## 5단계: `PrintOrchestrator` 신설

- [ ] `service/PrintOrchestrator.kt` 신설. `@Singleton + @Inject constructor`.
- [ ] `runForToday()` 구현 (02-backend.md §"PrintOrchestrator 설계"의 의사코드).
- [ ] `getSlotsForDay(DayOfWeek): List<Slot>`은 `SettingsRepository`에 신규 메서드 — **본 플랜 scope 외 (핸드오프 #6)**. 본 단계에서는 **임시 stub**: 설정에서 `KEY_PRINT_TIME_HOUR/MINUTE + KEY_NEWSLETTER_PAGES` + "모든주제" 단일 태그 → `Slot("모든주제", pageCount)` 1개 반환. 주석 `// TODO: #6 슬롯 묶음 도입 시 실제 구현`.
- [ ] 슬롯 간 try/catch로 실패 격리.
- [ ] `ClaudeNewsletterRecommender`는 Stub 수준으로 "후보의 첫 번째 반환"만 구현 — 실제 Claude 호출은 후속 (사용자 확인 필요 #1).

## 6단계: `NotificationHelper` 분리

- [x] `service/NotificationHelper.kt` 신설. `@Singleton @Inject constructor(@ApplicationContext context: Context)`.
- [x] `notify(title, message, channelId)` 시그니처 (채널 인자로 받음).
- [ ] `PrintOrchestrator`에서 주입 사용 (이터레이션 B).

## 7단계: `PrintWorker` 변경

- [ ] 생성자에서 `NewsletterRepository` 의존성 제거 → `PrintOrchestrator` 주입.
- [ ] `doWork()`에서 `inputData.getString("newsletter_id")` 분기 제거.
- [ ] `printOrchestrator.runForToday()` 호출.
- [ ] 재시도 정책: 기존 `runAttemptCount < 3` 유지 (spec §6 "당일 1회 재시도"와 불일치하나 MVP 수용 — 재시도 상세는 #7 핸드오프에서 조정).

## 8단계: `NewsletterWorker` + 스케줄 제거

- [x] `worker/NewsletterWorker.kt` 삭제 — TASK-20260426-005.
- [x] `WorkScheduler.scheduleNewsletterGeneration()` 메서드 + `scheduleAll()` 호출 제거 — TASK-20260426-005.
- [x] `WorkScheduler.scheduleAll()` 진입부에 `cancelUniqueWork("newsletter_generation")` + `cancelUniqueWork("daily_topic_selection")` 추가 — TASK-20260426-005.

## 9단계: `NewsletterViewModel` 수동 생성 orchestration

- [x] `NewsletterGenerationService` + `NotificationHelper` 주입 추가 — TASK-20260426-005.
- [x] `generateNewsletterManually(tag, pageCount)` 메서드 추가 (ManualGenStatus StateFlow 포함) — TASK-20260426-005.
  ```
  fun composeManualNewsletter(tag: String, pageCount: Int) = viewModelScope.launch {
      _uiState.update { it.copy(manualGenStatus = ManualGenStatus.Running) }
      runCatching {
          val result = newsletterGenerationService.generateForSlot(tag, pageCount)
          loadNewsletters()  // 목록 갱신
          result
      }.onSuccess { result ->
          _uiState.update { it.copy(manualGenStatus = ManualGenStatus.Success(result.title)) }
      }.onFailure { e ->
          _uiState.update { it.copy(manualGenStatus = ManualGenStatus.Failed(e.message ?: "알 수 없는 오류")) }
      }
  }
  ```

## 10단계: `NewsletterScreen` UI 변경

- [x] TopAppBar에 `+` 아이콘 액션 추가 — BottomSheet 토글 상태 — TASK-20260426-005.
- [x] BottomSheet 컴포저블 신설(`GenerateNewsletterSheet`): 태그 텍스트 입력(기본값 "모든주제") + 장수 Slider(1~5) + 취소/생성 버튼 — TASK-20260426-005.
- [x] `manualGenStatus`를 SnackbarHost와 연동 — Running/Success/Failed 상태별 한국어 메시지 — TASK-20260426-005.
- [x] 카드 상태 뱃지 문구 (라운드 1 어휘 규칙 준수):
  - `generated` → "생성됨"
  - `printed` → "프린트 완료"
  - `failed` → "실패"
- [x] 빈 상태 문구: "아직 뉴스레터가 없습니다. 상단 +로 직접 생성할 수 있어요." — TASK-20260426-005.

## 11단계: 수동 E2E 검증 (spec §4 acceptance 순서)

- [ ] 앱 신규 설치 → setup 완료 → 키워드 입력 → 자동 주제 생성 (topic-generation-paths 흐름) → Topics DB에 주제 여럿 쌓임 (`모든주제` 태그 포함).
- [ ] 설정 화면에서 프린트 시각을 가까운 미래로 조정 (예: 5분 후). 슬롯은 임시 stub 기준 `모든주제 × 2장`.
- [ ] 프린트 시각 도달 → PrintWorker 실행 → 미프린트 Newsletter 집합 비어있음 → `generateForSlot("모든주제", 2)` 2회 실행 → 1개 프린트, 1개 비축.
- [ ] Notion Newsletters DB 확인: 2개 페이지. 하나 `printed`, 하나 `generated`.
- [ ] Notion Topics DB 확인: 선택된 주제들 `consumed` 상태로 전환. 선택 안 된 주제는 `selected` 유지.
- [ ] Canon G3910에서 종이 출력 확인 (실제 프린트 검증).
- [ ] 다음날 (또는 프린트 시각 재조정) 실행 → 비축 1개가 매칭 → lazy 없이 프린트.
- [ ] **수동 생성 경로**: NewsletterScreen `+` 탭 → 태그·장수 선택 → 생성 → 스낵바 성공 → 미프린트 Newsletter 집합에 추가.
- [ ] **실패 경로**: Claude API Key 임시 제거 → 수동 생성 탭 → 스낵바 "API 키를 먼저 설정해주세요".
- [ ] **주제 0개 경로**: 모든 주제를 consumed로 만든 후 수동 생성 탭 → 스낵바 "해당 태그의 pending 주제가 없습니다".

## 12단계: 산출물 상태 갱신

- [ ] 본 플랜 README frontmatter `status: review` → 사용자/planner 컨펌 후 `accepted` → implementer 시작 시 `in-progress` → 완료 시 `consumed`.
- [ ] ADR-0005 `proposed → accepted` (사용자 컨펌 시).
- [ ] `docs/status.md` 갱신 (해당 라인 archive로 이관).
