---
updated: 2026-04-26
status: accepted
owner: designer
summary: "요일별 프린트 스케줄링 = 요일별 독립 PeriodicWork 7개 + 설정은 k/v JSON blob 1건. MVP 판정은 단일 슬롯; 다중 슬롯은 공개 배포 이월."
consumed_by:
  - "2026-04-26 planner: 라운드 1 scope 명확화 — MVP 1일 E2E 판정은 단일 슬롯 `[(모든주제, N장)]`만 검증. 다중 슬롯 코드 경로는 살아 있되 acceptance 대상 아님."
next_action: "implementer 진입 (선행: tag-system 3~4, newsletter-shelf 1~8 완료 후). MVP 검증 경로는 단일 슬롯에 한함."
refs:
  - docs/plans/weekday-print-slots/README.md
  - docs/specs/mvp.md
  - docs/decisions/0005-newsletter-shelf-lazy-generation.md
---

# ADR-0006: 요일별 프린트 스케줄링 구조

**상태:** accepted
**날짜:** 2026-04-19 (accepted) / 2026-04-26 (라운드 1 scope 명확화)
**결정자:** designer · planner (라운드 1)
**사용자 컨펌 시점:** 2026-04-19 (플랜 README Q1~Q5 추천안 일괄 수용)

## Round-1 scope clarification (2026-04-26)

라운드 1 결정에 따라 MVP 판정 = **단일 슬롯 `[(모든주제, N장)]` 1경로**만 검증한다. 본 ADR의 결정(요일별 독립 PeriodicWork 7개 + JSON blob 1건)은 다중 슬롯을 구조적으로 수용하지만, **MVP 1일 E2E 리허설에서는 단일 슬롯만 acceptance 대상**이다. 다중 슬롯 acceptance(요일 × 태그 유일성, 슬롯 간 격리·순서 무관, 매칭 검증)는 공개 배포 마일스톤으로 이월된다. 코드 변경 없이 acceptance 범위만 좁히는 변경이라 ADR 결정 본체는 유지.

## 번호 할당 메모

ADR-0004(API 키 저장소 보안 강화) / ADR-0005(공개 레포 이관) 번호는 ADR-0005(뉴스레터 미프린트 Newsletter 집합) 번호 할당 메모에서 release-hardening이 예약 중이다. 본 ADR은 MVP 1일 E2E의 핸드오프 #6을 막고 있는 결정이므로 다음 빈 번호 0006을 사용한다.

## 컨텍스트

`docs/specs/mvp.md` §3은 프린트 설정을 **"요일 × (프린트 시각, 슬롯 묶음)"** 모델로 정의한다.

- 요일마다 시각이 다를 수 있다.
- 슬롯 = `(태그, 장수)`, 한 요일에 N개, (요일, 태그) 유일.
- 요일별 on/off 수동 토글, 한 번 OFF는 사용자가 다시 켤 때까지 OFF.

현재 코드는 이 모델과 충돌:

- `SettingsEntity`는 단일 k/v 테이블이며 `KEY_PRINT_TIME_HOUR/MINUTE`, `KEY_NEWSLETTER_PAGES`로 **단일 프린트 시각 + 단일 장수**만 저장.
- `WorkScheduler.schedulePrint()`는 unique work `daily_print` 1개를 1일 주기로 enqueue — 요일별 시각 다름 표현 불가.
- `PrintWorker`는 `inputData.newsletter_id` 직접 지정 (이는 newsletter-shelf ADR-0005에서 `runForToday()`로 교체 예정).

지금 결정해야 하는 이유:

1. **핸드오프 #6 이후 3개 플랜(#7 IPP 실기, #8 1일 E2E 리허설)이 이 결정 위에 쌓인다.** 스케줄러 구조가 흔들리면 프린트 타이밍 검증이 반복된다.
2. **저장 포맷 결정은 Room 스키마 migration 여부와 연결**. 본인 1명 MVP에서 Room version 1 유지 vs 2로 올릴지가 분기점.
3. `ExistingPeriodicWorkPolicy.UPDATE`로 중복 enqueue는 안전하지만, 구조가 바뀌면 기존 `daily_print` unique work가 잔존해 cancel 필요 — 구조 변경 비용은 "지금 한 번" vs "여러 번".

## 결정

### 1. 저장 스키마: `settings` k/v에 `schedule_v1` 키 + JSON blob 1건

- 기존 `SettingsEntity` k/v 단일 테이블 유지. Room 스키마 version 1 유지 (마이그레이션 없음).
- 신규 상수 `KEY_WEEKLY_SCHEDULE = "schedule_v1"`.
- 값은 Gson으로 직렬화한 `WeeklySchedule` JSON:
  ```json
  {
    "monday":    { "enabled": true,  "hour": 7, "minute": 0, "slots": [{"tag": "모든주제", "pages": 2}] },
    "tuesday":   { "enabled": false, "hour": 0, "minute": 0, "slots": [] },
    ...
  }
  ```
- 읽기·쓰기 API: `SettingsRepository.getWeeklySchedule() / observeWeeklySchedule() / setWeeklySchedule() / getSlotsForDay(day)`.
- 손상 JSON은 `WeeklySchedule.default()`(모든 요일 OFF)로 복구 + UI 스낵바.

### 2. 스케줄러: 요일별 독립 PeriodicWork 7개 (최대)

- Unique work name: `daily_print_MONDAY` ... `daily_print_SUNDAY`.
- `PeriodicWorkRequestBuilder<PrintWorker>(7, TimeUnit.DAYS)` — 1주 주기.
- 초기 delay = 다음 해당 요일 시각까지의 밀리초.
- `inputData = workDataOf("day_of_week" to day.name)` — Worker 내부에서 요일 파싱 후 `PrintOrchestrator.runForDay(day)`.
- OFF 요일 / 빈 슬롯 요일은 **enqueue하지 않음** + `cancelUniqueWork("daily_print_<day>")`.
- `WorkScheduler.scheduleAll()` 진입부에서 기존 `daily_print` unique work cancel (레거시 정리).

### 3. 태그 유일성: 앱 코드(ViewModel)에서 강제, DB 제약 아님

- `TagNormalizer.normalize(tag)` 기준으로 `(day, tagNormalized)` 중복을 ViewModel의 `validateSlotInsert`에서 검증.
- 위반 시 저장 거부 + UI 피드백 (드롭다운 비활성 or 스낵바).
- **근거**: 저장 스키마가 단일 JSON blob이므로 DB UNIQUE 제약 불가. 대신 편집 진입점이 단 하나(`WeeklyScheduleViewModel`)라 코드 강제로 충분.

### 4. on/off 토글: `DaySchedule.enabled: Boolean`, 자동 복귀 없음

- 사용자가 명시적으로 `toggleDay(day, true)` 호출 전까지 enabled 유지.
- OFF 전환은 해당 요일 unique work cancel.
- 알림: OFF 요일은 Worker가 enqueue되지 않으므로 알림 자체 없음.

### 5. 마이그레이션: 레거시 단일 설정 → 7요일 동일 설정 자동 변환

- 앱 시작 시 1회 `migrateLegacyPrintTimeIfNeeded()`:
  - `KEY_WEEKLY_SCHEDULE` 존재 → no-op.
  - 없고 `KEY_PRINT_TIME_HOUR/MINUTE` 있음 → 7요일 모두 `enabled=true`, 시각 동일, 슬롯 `[("모든주제", KEY_NEWSLETTER_PAGES)]` 1개로 저장.
  - 둘 다 없음(신규 설치) → `WeeklySchedule.default()`(모든 요일 OFF) 저장.
- 기존 레거시 k/v 키는 `@Deprecated` 처리. 완전 삭제는 릴리스 하드닝 트랙.

## 대안

### (SA) 저장 스키마: Room 신규 테이블 `weekly_slots` — 기각

- `@Entity WeeklySlotEntity(primaryKeys = ["day", "tag_normalized"])` + `DayScheduleEntity(@PrimaryKey day)`.
- UNIQUE 제약이 DB 레벨로 박혀 태그 유일성이 스키마에서 보장.
- 기각 사유:
  1. Room 스키마 1→2 마이그레이션 비용 + TypeConverter 배선. 본인 1명 MVP가 지불할 비용이 아님.
  2. CLAUDE.md "로컬 Room은 설정 전용" 단일 테이블 원칙과 충돌 (설정 전용 테이블에 추가로 스케줄 테이블 도입).
  3. 유일성 강제는 앱 코드 한 곳이면 충분 — DB 제약 이득의 실체 없음.

### (SB) 저장 스키마: k/v에 요일별 7개 키 (`schedule_monday` … `schedule_sunday`) — 기각

- A의 세분화. 요일별 저장 트랜잭션 독립.
- 기각 사유: 유일성 검증이 7키를 읽어 합쳐야 해서 경로가 복잡. 부분 저장 이득은 JSON blob이 < 5KB라 실체 없음.

### (ScA) 스케줄러: 단일 PeriodicWork 1일 주기 + Worker 내부 요일 필터 — 기각

- 기존 `daily_print`를 1일 주기로 유지. Worker 진입 시 오늘 요일 설정을 확인해 실행/스킵.
- 기각 사유:
  1. 1일 주기는 특정 시각 1개만 반영 가능 → 요일별 시각 다름을 spec이 허용하지만 구조가 이를 표현 못함. 7요일 동일 시각 강제는 spec 위배.
  2. OFF 요일도 Worker가 깨어나 즉시 return — 불필요 wakeup.

### (ScB) 스케줄러: OneTimeWorkRequest 체이닝 — 기각

- Worker 완료 후 다음 실행 `OneTimeWorkRequest`로 enqueue.
- 장점: 요일별 시각 자유.
- 기각 사유: 체인 끊기면 영구 중단 위험. MVP 신뢰성 기준 초과.

### (MA) 마이그레이션: 기존 레거시 설정 완전 무시 (전부 OFF로 시작) — 기각

- 기존 `KEY_PRINT_TIME_HOUR/MINUTE`가 있어도 WeeklySchedule은 default(모든 OFF)로 시작.
- 기각 사유: 본인 1명 기존 사용자(= 이 앱 개발자 본인)가 설정 다시 해야 함. 마찰 ↑.

### (MB) 마이그레이션: 스키마 version 2로 올리고 Room Migration 1→2 — 기각

- Room `@AutoMigration` 또는 수동 Migration으로 기존 keys를 새 스키마로 복사.
- 기각 사유: 본 ADR 결정 1이 "Room version 1 유지"이므로 Migration 자체가 불필요. 기존 k/v 읽어 새 키 쓰기는 일반 코드로 충분.

## 영향

### 데이터 모델 변경

- **Room**: 변경 없음 (version 1 유지).
- **Settings k/v**: `schedule_v1` 키 추가. 기존 `KEY_PRINT_TIME_HOUR / MINUTE / KEY_NEWSLETTER_PAGES` deprecated.

### 코드 변경 범위 (상세는 plans/weekday-print-slots/05-checklist.md)

- **신규**:
  - `data/schedule/Slot.kt`, `DaySchedule.kt`, `WeeklySchedule.kt`, `WeeklyScheduleJson.kt`.
  - `ui/schedule/WeeklyScheduleScreen.kt`, `WeeklyScheduleViewModel.kt`.
  - Navigation route `"settings/weekly-schedule"`.
- **변경**:
  - `SettingsRepository` — 스케줄 API + 마이그레이션 + 레거시 getter deprecated.
  - `SettingsEntity` — `KEY_WEEKLY_SCHEDULE` 상수 추가.
  - `WorkScheduler` — `schedulePrint()` 제거 → `scheduleWeeklyPrints()` 신설. `scheduleAll()` 진입부 cancel 목록에 `daily_print` 추가.
  - `PrintWorker` — `inputData["day_of_week"]` 파싱 + `runForDay(day)` 호출. (newsletter-shelf 플랜이 입력 방식 변경을 이미 다룸 — 본 플랜은 요일 메타 추가).
  - `PrintOrchestrator` — `runForToday()` → `runForDay(day)` 재명명. `getSlotsForDay(day)` stub 제거.
  - `NewsletterRepository` — `listAvailableTagNames()` 실제 구현 (tag-system 5단계 이월).
  - `NotionApi` — `getDatabase(id)` 엔드포인트 추가.
  - `SettingsScreen` — 기존 TimePicker/Slider 제거 + "프린트 스케줄" Card 진입점.
  - `SettingsViewModel` — printTimeHour/Minute/newsletterPages 필드 제거.

### UI 영향

- 설정 화면에서 프린트 시각/분량 직접 편집 UI **제거**. 대신 진입점 Card → 전용 `WeeklyScheduleScreen`.
- Navigation 레벨 1 추가.
- 한국어 문구 대거 추가 (plans/weekday-print-slots/04-ui.md §"한국어 문구" 참고).

### MVP 1일 E2E에 미치는 영향

- spec §4 체크리스트 6번("요일별 프린트 설정에 슬롯 1~2개 등록")의 직접 검증 지점.
- 본인이 당일 요일을 ON + 시각 +5분 + 슬롯 1개 설정 → PrintWorker 실행 확인 → 프린트 성공이 본 ADR의 수용 기준.

### 되돌리기 비용

- 저장 스키마 (§1) 되돌리기: **작음** — k/v 키 하나라 삭제로 종료. 단 마이그레이션 코드가 여전히 실행될 수 있으므로 해당 함수도 함께 삭제.
- 스케줄러 구조 (§2) 되돌리기: **중** — 7개 unique work를 모두 cancel 후 단일 work 복원. 한 번 사용자 단말에 enqueue된 work들은 cancel 호출이 `scheduleAll()`에 있어 앱 기동 1회로 정리 가능.
- 마이그레이션 (§5) 되돌리기: **작음** — 함수 제거.

되돌리기 비용은 작은 편. 본 ADR을 박는 주된 이유는 "여러 번 바꾸는 것보다 한 번에 박자"이며 구조적 비가역성 때문이 아님.

## 의도적으로 본 ADR이 결정하지 않는 것 (후속 핸드오프로 위임)

- **다중 태그 슬롯 OR/AND 매칭** — 슬롯은 단일 태그. 다중 태그는 미래 확장.
- **요일별 시각 복사/프리셋 UX** — 본 ADR은 요일별 독립 입력만. 공개 배포 시 재고.
- **프린트 재시도 상세 정책** — spec §6 "당일 1회 재시도". `PrintWorker.runAttemptCount < 1`로 최소 반영. 상세는 **핸드오프 #7**.
- **프린터 IP 입력 UI 손질** — 본 플랜 범위 밖. **핸드오프 #7**.
- **후보 뉴스레터 복수 시 Claude 추천** — **핸드오프 #7**, newsletter-shelf Q1에서 확정.

## 관련

- [ADR-0003](0003-tag-system-data-model.md) — 태그 모델 (본 ADR 슬롯의 태그 셀렉터 출처).
- [ADR-0005](0005-newsletter-shelf-lazy-generation.md) — 미프린트 Newsletter 집합 + PrintOrchestrator. 본 ADR은 `runForToday() → runForDay(day)` 시그니처 확정.
- 플랜: [`docs/plans/weekday-print-slots/`](../plans/weekday-print-slots/README.md).
- spec: [`docs/specs/mvp.md`](../specs/mvp.md) §3 "요일별 프린트 설정 슬롯 묶음", 핸드오프 #6.
