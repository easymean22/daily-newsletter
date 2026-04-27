---
updated: 2026-04-26
status: accepted
owner: designer
summary: "요일별 프린트 설정 UI — (요일 × 시각 × 슬롯 묶음) 편집 + 다중 슬롯 스케줄러 확장 (MVP 핸드오프 #6). MVP 검증은 단일 슬롯만; 다중 슬롯은 공개 배포 이월."
consumed_by:
  - "2026-04-26 planner: 라운드 1 문서 정합 sweep — MVP = 단일 슬롯 `[(모든주제, N장)]` 1경로 명문화. 다중 슬롯 코드 경로는 구조상 살아 있지만 MVP 1일 E2E 리허설에서는 검증 대상 아님."
next_action: "implementer 진입 (선행: tag-system 3~4, newsletter-shelf 1~8 완료 후). MVP 판정 경로는 단일 슬롯에 한함."
refs:
  # (refs는 배경 확인용 — 작업 전 필수 읽기 아님)
  - docs/specs/mvp.md
  - docs/decisions/0005-newsletter-shelf-lazy-generation.md
  - docs/decisions/0006-weekday-print-scheduling.md
  - docs/plans/newsletter-shelf/README.md
  - docs/plans/tag-system/README.md
---

# 요일별 프린트 설정 UI (슬롯 묶음)

> **라운드 1 확정 (2026-04-19, 본문 갱신 2026-04-26):** MVP 슬롯 구성은 **단일 슬롯 1경로** `[(모든주제, N장)]`로 한정. 다중 슬롯(요일 × 서로 다른 태그 여러 개)의 격리·순서·매칭 검증은 **공개 배포 마일스톤으로 이월**한다. 본 플랜의 데이터 모델·스케줄러 구조는 다중 슬롯을 수용하지만(요일 × 태그 유일성, 순서 무관 등), MVP 1일 E2E 판정 경로와 acceptance는 단일 슬롯만 커버한다. 다중 슬롯 관련 acceptance/sub-file 항목은 "공개 배포 이월" 표시로 갈음한다.

MVP **핸드오프 #6** 전용 플랜. spec §3의 "각 요일 = (프린트 시각, 슬롯 묶음)" 모델을 실제 설정 UI + 저장 스키마 + 스케줄러 확장으로 구현한다. (라운드 1: MVP 검증은 단일 슬롯만.)

## 요구사항 (spec §3 "요일별 프린트 설정")

- 요일마다 **(프린트 시각, 슬롯 리스트, on/off)** 을 편집 가능.
- 슬롯 = `(태그, 장수)`. 한 요일에 슬롯 N개 가능.
- **같은 요일 안에서 (태그) 유일** — `(월, IT)` 슬롯은 하나만.
- 요일별 on/off 토글은 **수동 유지** (자동 ON 복귀 없음).
- 빈 슬롯 요일 또는 OFF 요일은 **프린트 스킵**.
- 스케줄러가 요일별 다중 슬롯을 처리해야 한다.

## 바로 써야 하는 의존 인터페이스

```kotlin
// SettingsRepository (본 플랜 2단계에서 신설)
suspend fun getWeeklySchedule(): WeeklySchedule
fun observeWeeklySchedule(): Flow<WeeklySchedule>
suspend fun setWeeklySchedule(schedule: WeeklySchedule)
suspend fun getSlotsForDay(day: DayOfWeek): List<Slot>

// Slot / DaySchedule / WeeklySchedule (본 플랜 1단계에서 신설, data/schedule/)
data class Slot(val tag: String, val pages: Int)   // pages: 1..5
data class DaySchedule(val enabled: Boolean, val hour: Int, val minute: Int, val slots: List<Slot>)
data class WeeklySchedule(val byDay: Map<DayOfWeek, DaySchedule>) {
    fun slotsForDay(day: DayOfWeek): List<Slot>
    fun scheduledDays(): List<Pair<DayOfWeek, DaySchedule>>
    companion object { fun default(): WeeklySchedule }
}

// PrintOrchestrator (newsletter-shelf 플랜 5단계에서 신설 — 본 플랜 5단계에서 runForDay 로 시그니처 확정)
suspend fun runForDay(day: DayOfWeek)
suspend fun runForToday()   // = runForDay(LocalDate.now().dayOfWeek) 편의 메서드

// WorkScheduler (본 플랜 4단계에서 재설계)
suspend fun scheduleAll()          // 기존 work 정리 + scheduleWeeklyPrints() 호출
private suspend fun scheduleWeeklyPrints()  // 요일별 PeriodicWork 7개 관리
// unique work names: "daily_print_MONDAY" … "daily_print_SUNDAY"

// NewsletterRepository (tag-system 5단계 이월분 — 본 플랜 3단계에서 구현)
suspend fun listAvailableTagNames(): List<String>   // Newsletters DB multi_select 옵션 풀 조회

// TagNormalizer (tag-system 2단계 완료)
fun normalize(input: String): String  // 유일성 검증에 사용

// PrintWorker (newsletter-shelf 7단계 + 본 플랜 5단계에서 변경)
// inputData: "day_of_week" -> DayOfWeek.name()
// 내부: printOrchestrator.runForDay(DayOfWeek.valueOf(dayName))
```

## 전제 플랜

- newsletter-shelf (review) — `PrintOrchestrator.runForToday()` + `SettingsRepository.getSlotsForDay(DayOfWeek): List<Slot>` stub 시그니처
- tag-system (in-progress) — `NewsletterRepository.listAvailableTagNames()` 헬퍼. tag-system 5단계가 본 플랜으로 이월되었음

## 핵심 결정 (ADR-0006 후보)

1. **저장 스키마** = `settings` k/v 단일 테이블에 `schedule_v1` 키 + JSON blob 1건. 신규 Room 테이블 도입하지 않음.
2. **스케줄러 구조** = `DayOfWeek` 별 독립 periodic work (7개 미만 — OFF나 빈 슬롯은 schedule하지 않음). T-2h / T-30m 주기 워크 완전 제거(shelf 플랜이 이미 결정). 슬롯은 요일 단위 `PrintWorker` 내부에서 `PrintOrchestrator`가 루프.
3. **태그 유일성 강제** = ViewModel의 slot 추가/수정 시점에 정규화 키 비교. 중복이면 저장 거부 + UI 에러 표시.
4. **기본값(마이그레이션)** = 최초 실행 시 기존 단일 프린트 시각 설정(`KEY_PRINT_TIME_HOUR/MINUTE + KEY_NEWSLETTER_PAGES`)을 monday~sunday 동일 + `[("모든주제", pages)]` 슬롯 1개로 변환. 기존 키는 deprecated 마크만 하고 제거는 후속.

## 파일 지도

| 파일 | 내용 | 참조 시점 |
|---|---|---|
| [01-background.md](./01-background.md) | 현재 코드 맥락 + spec 맞춤 지점 + 범위 경계 | 설계 배경 파악 시만 |
| [02-data.md](./02-data.md) | 저장 스키마 대안 3개 + trade-off + 추천안 + 마이그레이션 | 1~2단계 작업 시만 |
| [03-scheduler.md](./03-scheduler.md) | 스케줄러 확장 대안 3개 + trade-off + on/off 정책 반영 | 4~5단계 작업 시만 |
| [04-ui.md](./04-ui.md) | UI 설계 (와이어프레임 + 컴포넌트 트리 + 에러/빈 상태) | 6~8단계 작업 시만 |
| [05-checklist.md](./05-checklist.md) | implementer 체크리스트 10단계 | 항상 참조 |

## 진행률

- [ ] 1단계: Slot/DaySchedule 도메인 모델 + JSON 직렬화
- [ ] 2단계: SettingsRepository 확장 (`getWeeklySchedule`, `setWeeklySchedule`, `getSlotsForDay`)
- [ ] 3단계: Tag 옵션 풀 헬퍼 (tag-system 5단계 이월분)
- [ ] 4단계: WorkScheduler 요일별 재설계 (`scheduleWeeklyPrints()`)
- [ ] 5단계: PrintWorker 요일 메타 입력 + PrintOrchestrator.runForDay(DayOfWeek)
- [ ] 6단계: WeeklyScheduleViewModel + 상태 모델
- [ ] 7단계: SettingsScreen 진입점 + WeeklyScheduleScreen 화면
- [ ] 8단계: Slot 편집 BottomSheet (태그/장수 + 유일성 검증)
- [ ] 9단계: 기존 단일 프린트 시각 설정 deprecated 처리 + 마이그레이션
- [ ] 10단계: 수동 E2E 검증 + 산출물 상태 갱신

## 결정 근거 (배경 필요 시만)

> 📂 결정 근거 (배경 필요 시만 참고 — 코딩에 불필요)

> 2026-04-19 사용자 응답: "추천대로" — Q1~Q5 모두 추천안 수용.
> Q1 → A, Q2 → A, Q3 → A, Q4 → B, Q5 → A.

### Q1. 슬롯 묶음 저장 스키마를 어디에 둘까 → 확정: A

현재 `SettingsEntity`는 k/v 단일 테이블. 슬롯 묶음은 요일×N 구조라 k/v와 결이 다름.

- **A. `settings` k/v에 `schedule_v1` 키 + JSON blob 1건 (추천)** — 예: `{"monday":{"enabled":true,"hour":7,"minute":0,"slots":[{"tag":"모든주제","pages":2}]}, ...}`. Room 스키마 버전 유지(version 1). 장점: 마이그레이션 0, 기존 k/v 패턴 유지. 단점: 부분 업데이트 시 전체 재직렬화, Gson 스키마 변경 시 `schedule_v2` 키 도입 + 기존 키에서 이관. 저장 1회 < 5KB.
- **B. Room 신규 테이블 `weekly_slots(day, hour, minute, enabled, slot_index, tag, pages)` + `@Query` 편집** — 정식 엔티티. 장점: SQL 제약(UNIQUE `(day, tag)`)을 DB 레벨로 강제 — 유일성이 스키마로 박힘. 단점: Room 스키마 version 1→2 마이그레이션 필요. AppDatabase/TypeConverter 추가. 본인 1명 MVP에서 무게 과함. 공개 배포에서 재고 여지.
- **C. k/v에 요일별 7개 키 (`schedule_monday` … `schedule_sunday`) 각 JSON** — A의 세분화. 장점: 요일별 저장이 개별 트랜잭션. 단점: 유일성 검증이 파일 7개를 꿰뚫는 코드가 되어 복잡. 직렬화 지점 분산.
- **귀결**:
  - A: 구현 최소 + 마이그레이션 없음. 부분 업데이트 시 "읽기-병합-쓰기" 1회. 유일성은 앱 코드에서 강제.
  - B: 장기 확장성 최고 + DB 제약 보너스. 비용: 스키마 마이그레이션 1회. Room → Compose StateFlow 체인 배선 추가.
  - C: 중간. 동시 편집(일요일 수정 중 월요일 저장)이 드문 MVP 상황에서는 A 대비 이득 없음.
- **추천: A**. 기존 `SettingsEntity` 패턴 유지 + 마이그레이션 회피. 유일성 앱 코드 강제는 ViewModel 단 한 곳이면 충분.

### Q2. 스케줄러를 요일별로 어떻게 enqueue할까 → 확정: A

spec §3은 "각 요일 = (프린트 시각, 슬롯 묶음)"으로 요일별 시각이 다를 수 있음. newsletter-shelf ADR-0005 §결정 6은 "WorkScheduler가 요일마다 독립 스케줄을 걸 수 있도록 구조만 열어둔다"로 #6에 위임.

- **A. 요일별 7개 독립 periodic work `daily_print_<MON..SUN>`, 각 1주 주기, 초기 delay는 다음 해당 요일 시각까지 (추천)** — WorkManager는 지정 주기(1주)로 enqueue 가능. 각 요일 work는 입력 데이터에 `dayOfWeek` 포함 → `PrintWorker`가 해당 요일 슬롯만 처리. 장점: 요일별 시각이 달라도 자연 대응. OFF 요일은 enqueue 자체를 안 함(= 재schedule 시 cancel). 단점: Unique work name 7개 관리. WorkManager 1주 주기 사용은 `PeriodicWorkRequestBuilder(7, DAYS)` 가능 (`flexInterval` 불필요).
- **B. 기존 단일 `daily_print` 1일 주기 유지 + Worker 내부에서 "오늘 요일이 enabled인지 + 시각이 지금±허용범위인지" 필터** — 1일 주기로 매일 동일 시각에 깨나 "오늘 설정된 시각"이 다르면 허탕. 장점: 스케줄링 단순. 단점: 요일마다 시각이 다른 spec §3 요구사항과 맞지 않음 — 가장 이른 시각으로 깨어야 함 + 다른 시각 요일은 추가 alarm 필요. 또는 사용자가 7요일 시각이 같도록 UX 강제 → spec 의도 위배.
- **C. 가장 이른 시각 1개 + 내부 스케줄러가 오늘 이후 다음 슬롯까지 delay 재enqueue** — `OneTimeWorkRequest` 체이닝. 장점: WorkManager 주기 의존 X. 단점: "다음 실행 시각" 계산 버그가 곧 프린트 실패. MVP 신뢰성 초과.
- **귀결**:
  - A: Unique work 7개 배선 + on/off 토글 시 `cancelUniqueWork("daily_print_MON")`. 코드 단순. 시각 변경 시 7개 재schedule.
  - B: 요일별 시각이 다를 때 구조적 결함. "7요일 동일 시각" 제약을 UI에 걸어야 spec을 어기지 않음.
  - C: 가장 유연하나 OneTime 재enqueue 버그 리스크 높음 — MVP 수용 어려움.
- **추천: A**. spec §3 "요일별 시각"을 단순하게 풀 유일한 옵션. ADR-0006로 박음.

### Q3. 요일별 시각이 전부 같을 때(가장 흔한 경우) UX를 어떻게 단순화할까 → 확정: A

현실적으로 사용자는 평일 동일 시각을 쓸 가능성이 큼. 저장 모델은 요일별 독립이지만 UI는 반복 입력 피로가 생긴다.

- **A. 요일별 전체 독립 카드 — 시각도 슬롯도 각 요일에서 편집 (추천)** — 가장 단순한 UI. 복사 기능 없음. 장점: 구현 0 추가 + 멘탈 모델 명확. 단점: 7요일 동일 시각 입력 7회 반복.
- **B. "모든 요일에 적용" 체크박스가 달린 공용 TimePicker 1개 + 요일별 슬롯만 독립** — 시각은 공용/요일별 토글. 장점: 공통 경우 빠름. 단점: 토글 UX 학습 필요. 공용 시각 + 특정 요일만 다른 시각 조합이 복잡.
- **C. "평일 같은 시각" 프리셋 + 주말은 별도** — 3그룹(월~금, 토, 일) 시각. 장점: 흔한 사용 패턴 직접 지원. 단점: spec과 맞지 않음 — spec은 "각 요일"을 1:1 축으로 정의. 변칙.
- **귀결**:
  - A: 입력 부담 최대. MVP 1일 E2E는 본인 1명 + 당일 요일만 검증 → 부담이 실체 없음.
  - B: 미래 확장 친화. 초기 구현 +2~3단계.
  - C: spec을 왜곡. 기각.
- **추천: A**. MVP 1일 E2E 성공 기준은 당일 요일 1개만 제대로 동작하면 됨. 복사/프리셋은 공개 배포 마일스톤에서.

### Q4. 슬롯 유일성 위반 시 UI 피드백 → 확정: B

같은 요일에 `(IT, 2장)`과 `(IT, 1장)` 추가 시도: 태그가 동일해서 spec상 금지.

- **A. 중복 태그 저장 시도 시 snackbar 에러 + BottomSheet 유지 — "이 요일엔 이미 [IT] 슬롯이 있어요. 기존 슬롯을 수정해주세요." (추천)** — 저장 버튼 클릭 후 검증. 장점: 즉시 피드백 + 복구 경로(기존 슬롯 편집) 명확. 단점: 저장 버튼 누르기 전까지 중복 인지 불가.
- **B. 태그 드롭다운에서 이미 쓰인 태그를 disabled로 표시** — 선택 불가. 장점: 에러 자체가 발생 안 함. 단점: 드롭다운 옵션 렌더가 편집 중 슬롯(기존 값)과 충돌 — "현재 편집 중인 슬롯의 태그 본인"을 제외하는 로직 필요.
- **C. 드롭다운에서 선택은 허용 + 선택 즉시 하단에 경고 + 저장 버튼 disabled** — A와 B의 중간. 저장 전 미리 피드백.
- **귀결**:
  - A: 구현 가장 단순. "생성 후 발견" UX라 한 단계 지연.
  - B: UX 가장 매끄러움. 구현 시 "편집 중 vs 신규" 구분 필요.
  - C: 중간. 상태 하나 더(`duplicateWarning`) 필요.
- **추천: B**. 슬롯 편집 횟수가 적은 MVP에서도 드롭다운 선택 실수를 아예 차단하는 편이 좋음. 편집 중 슬롯 본인 제외는 `selectedTag = currentSlot?.tag` 참조로 단순 해결.

### Q5. 프린터 IP 입력 UI를 본 플랜에서 다룰지, #7(IPP E2E)로 미룰지 → 확정: A

spec §3 설정 UI에 "프린터 IP 입력"이 포함. 핸드오프 #6 범위에 "프린트 설정" 전반이 들어가지만, 실제 프린트 경로 검증은 #7 scope.

- **A. 본 플랜에서 프린터 IP 입력 UI는 건드리지 않음 — 기존 `SettingsScreen`의 `printerIp` 필드 그대로 유지 (추천)** — 요일별 스케줄 섹션만 신설. 장점: 본 플랜 범위 최소 + #7이 IPP 검증과 함께 UI도 손보게 둠(필요 시). 단점: spec §3 "설정 UI"를 한 번에 정돈할 기회 놓침.
- **B. 본 플랜에서 SettingsScreen 전면 재구성 — 프린터 섹션도 카드화, Wi-Fi/ePrint 토글 등 UX 정돈** — 한 화면 완성도 up. 단점: #7과 겹쳐 두 번 손보기 + 본 플랜 scope ↑.
- **귀결**:
  - A: 화면 분리 clean. #7에서 IPP 테스트 결과에 따라 IP 검증·테스트 프린트 버튼 추가 가능.
  - B: 사용자 혼란 ↓ (한 번에 정리). 작업량 ↑.
- **추천: A**. scope 겹침 방지 + #7이 프린터 관련 UI를 책임지도록.

## 의도적으로 본 플랜이 결정하지 않는 것

- **후보 뉴스레터 복수 시 Claude 추천** — newsletter-shelf Q1과 동일, #7에서.
- **프린트 재시도 정책 상세** — spec §6 "당일 1회 재시도". #7에서 PrintService 손볼 때.
- **프린터 IP 입력 UI 손질** — Q5 답대로 #7로 위임.
- **OR/AND 다중 태그 슬롯** — 본 플랜은 단일 태그 슬롯만. spec §3도 `(태그, 장수)`를 슬롯의 단위로 씀. 다중 태그는 미래 확장.

## 관련 결정

- ADR-0006 (accepted, 2026-04-19, 본 플랜이 신설): 요일별 프린트 스케줄링 구조 — 요일별 독립 PeriodicWork 7개 + k/v JSON blob 저장.
- ADR-0005 (proposed, newsletter-shelf): `PrintOrchestrator.runForToday()` 구조. 본 플랜이 `runForDay(DayOfWeek)` 형태로 시그니처 확정.
- ADR-0003 (accepted, tag-system): multi_select 옵션 풀을 태그 선택지 출처로 사용.
