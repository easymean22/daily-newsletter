---
updated: 2026-04-19
summary: "현재 코드 맥락 + spec §3 요일별 프린트 설정 요구와의 간극"
parent: ./README.md
---

# 배경 / 현재 코드 맥락

## spec §3 재확인 — "요일별 프린트 설정 슬롯 묶음"

- 각 요일 = (프린트 시각, 슬롯 묶음).
- 슬롯 = (태그, 장수). 한 요일 슬롯 N개.
- **요일 × 태그 = 유일**.
- 요일별 on/off 수동 토글, 한 번 OFF면 사용자가 다시 켤 때까지 OFF.
- 슬롯 출력 순서 무관, 슬롯 간 실패 격리.

## 현재 코드에서의 간극

### 1. `SettingsEntity` — 요일 개념 없음

```
KEY_PRINT_TIME_HOUR = "print_time_hour"
KEY_PRINT_TIME_MINUTE = "print_time_minute"
KEY_NEWSLETTER_PAGES = "newsletter_pages"
```

- **단일 프린트 시각** 1쌍. 요일별 설정 부재.
- **단일 pages**. 슬롯별 장수 부재.
- 태그 슬롯 개념 전무.

### 2. `WorkScheduler` — 단일 주기 워크

`scheduleAll()`:
- `scheduleDailyTopicSelection()` (T-2h) — topic-generation-paths 플랜에서 제거 예정.
- `scheduleNewsletterGeneration()` (T-30m) — newsletter-shelf 플랜에서 제거 예정.
- `schedulePrint()` (T) — **단일 unique work** `daily_print`, 1일 주기.
- `scheduleCleanup()` (00:00) — MVP 판정 밖, 유지.

요일별 시각이 다를 수 있다는 spec 요구가 현재 구조로는 표현 불가.

### 3. `PrintWorker` — `inputData.newsletter_id` 단일 페이지 직접 지정

- newsletter-shelf 플랜이 이 의존을 `PrintOrchestrator.runForToday()` 호출로 교체.
- 본 플랜은 여기서 한 걸음 더 — **요일 메타를 inputData에 실어** `PrintOrchestrator.runForDay(dayOfWeek)` 호출로 확장.

### 4. `SettingsScreen` — 단일 TimePicker + 단일 Slider

```kotlin
Card(onClick = { showTimePicker = true }) { "매일 %02d:%02d 에 프린트" }
Slider(value = newsletterPages, valueRange = 1f..5f, steps = 3)
```

- 요일별 편집 진입점 없음.
- 태그 셀렉터 없음.

### 5. `SettingsRepository.getSlotsForDay(DayOfWeek)` — newsletter-shelf stub

newsletter-shelf 05-checklist.md 5단계에서:

> `getSlotsForDay(DayOfWeek): List<Slot>`은 `SettingsRepository`에 신규 메서드 — **본 플랜 scope 외 (핸드오프 #6)**. 임시 stub: `Slot("모든주제", pageCount)` 1개 반환.

본 플랜이 **실제 구현**을 채운다.

## 범위 경계

### 본 플랜이 손대는 범위

| 영역 | 파일 |
|---|---|
| 데이터 모델 | `data/schedule/` (신규) — `Slot`, `DaySchedule`, `WeeklySchedule`, JSON adapter |
| 설정 저장 | `SettingsEntity.KEY_WEEKLY_SCHEDULE` 신규 키 + `SettingsRepository.getWeeklySchedule / setWeeklySchedule / getSlotsForDay` |
| 스케줄러 | `WorkScheduler.scheduleWeeklyPrints()` + OFF 요일 cancel 로직 |
| Worker | `PrintWorker.doWork()` — `inputData["day_of_week"]` 읽어 `PrintOrchestrator.runForDay(DayOfWeek)` 호출 |
| Orchestrator | `PrintOrchestrator.runForDay(DayOfWeek)` — 기존 `runForToday()`를 이 메서드로 재명명 + 호출자만 오늘 요일 주입 |
| Tag 옵션 풀 | `NewsletterRepository.listAvailableTagNames()` (tag-system 5단계 이월) |
| UI | `ui/schedule/WeeklyScheduleScreen.kt` + `WeeklyScheduleViewModel.kt` + `SettingsScreen`에 진입점 Card 1개 |
| 마이그레이션 | 기존 `KEY_PRINT_TIME_HOUR/MINUTE/NEWSLETTER_PAGES` → `KEY_WEEKLY_SCHEDULE` 1회 변환 |

### 본 플랜이 손대지 않는 범위

- 프린터 IP 입력 UI (Q5 답: #7로 위임).
- `PrintService` IPP 실기 검증 (#7).
- Claude 추천 실제 호출 (newsletter-shelf Q1, #7).
- 다중 태그 슬롯 OR/AND (미래).
- Notion Newsletters DB 스키마 변경 (이미 tag-system에서 multi_select 추가됨).

## 기존 코드 이슈 (본 플랜이 같이 해결)

- **이슈 A**: `SettingsRepository`에 `observeAll()`은 있으나 특정 키만 observe하는 헬퍼 없음. `getWeeklySchedule(): Flow<WeeklySchedule>` 신설 시 `observe(KEY_WEEKLY_SCHEDULE).map { parseJson(it) }` 패턴으로 해결.
- **이슈 B**: `WorkScheduler.scheduleAll()`은 scheduler 교체 시점의 cancel 로직이 없음. newsletter-shelf 플랜이 `cancelUniqueWork("newsletter_generation")`, topic-generation-paths가 `cancelUniqueWork("daily_topic_selection")`을 추가. 본 플랜은 기존 단일 `daily_print`를 `cancelUniqueWork("daily_print")` + 요일별 7개 enqueue로 교체하면서 동일 패턴으로 cleanup.
