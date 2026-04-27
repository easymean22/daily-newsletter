---
updated: 2026-04-19
summary: "저장 스키마 대안 3개 + 추천안(A: k/v JSON blob) + 마이그레이션"
parent: ./README.md
---

# 데이터 모델 / 저장 스키마

## 대안 비교 (3개)

### 옵션 D-A: `settings` k/v에 `schedule_v1` 키 + JSON blob 1건

- `SettingsEntity.KEY_WEEKLY_SCHEDULE = "schedule_v1"`.
- 값은 JSON 문자열:
  ```json
  {
    "monday":    { "enabled": true,  "hour": 7, "minute": 0, "slots": [{"tag": "모든주제", "pages": 2}] },
    "tuesday":   { "enabled": true,  "hour": 7, "minute": 0, "slots": [{"tag": "IT", "pages": 2}, {"tag": "모든주제", "pages": 1}] },
    "wednesday": { "enabled": false, "hour": 7, "minute": 0, "slots": [] },
    ...
  }
  ```
- 읽기: `SettingsRepository.get("schedule_v1")?.let { Gson().fromJson(it, WeeklySchedule::class.java) } ?: defaultSchedule()`.
- 쓰기: `Gson().toJson(schedule).let { set("schedule_v1", it) }`.

### 옵션 D-B: Room 신규 테이블 `weekly_slots`

```kotlin
@Entity(
  tableName = "weekly_slots",
  primaryKeys = ["day", "tag_normalized"]   // 유일성 DB 레벨 강제
)
data class WeeklySlotEntity(
    val day: String,               // "MONDAY"..
    val tagNormalized: String,     // normalizeTag 결과
    val tagDisplay: String,        // 원형
    val pages: Int,
    val slotOrder: Int             // 요일 내 표시 순서 (spec은 무관하지만 UI는 고정 순서 필요)
)

@Entity(tableName = "day_schedules")
data class DayScheduleEntity(
    @PrimaryKey val day: String,
    val enabled: Boolean,
    val hour: Int,
    val minute: Int
)
```

- Room 스키마 1→2 마이그레이션 필요.

### 옵션 D-C: k/v에 요일별 7개 키 (`schedule_monday` … `schedule_sunday`)

- 각 값은 해당 요일 JSON `{enabled, hour, minute, slots: []}`.
- D-A보다 세분화. 요일별 개별 저장 트랜잭션.

## Trade-off

| 축 | D-A (JSON blob 1건) | D-B (Room 신규 테이블) | D-C (k/v 요일별 7키) |
|---|---|---|---|
| **spec 의도 부합** ★ | 정확히 "요일 × (시각, 슬롯 묶음)" 1:1 매핑. | 동일. DB 제약이 유일성을 자동 강제. | 동일하나 7파일 분산. |
| **마이그레이션 비용** | 0 — 기존 Room 스키마 version 1 유지. | Room 1→2 마이그레이션 + TypeConverter 추가. | 0. |
| **유일성 강제 방식** | 앱 코드(ViewModel/Save 시점) 1곳. | DB 제약 + 앱 코드 둘 다. | 앱 코드 1곳, 단 단일 키 로드/저장 시 충돌 검증 로직이 복잡. |
| **부분 업데이트 비용** | "읽기-병합-쓰기" 1회 (5KB 이내). 사실상 무비용. | 해당 row만 update — 가장 저렴. | 요일 단위 부분 저장. |
| **관측/디버깅** | `adb exec sqlite3 "SELECT value FROM settings WHERE key='schedule_v1'"` → JSON 1줄. 가독성 좋음. | 정식 테이블 join — IDE Room inspector 친화. | 7줄 검색. |
| **스키마 진화** | `schedule_v1` → `schedule_v2` 키 도입 + 이관 코드. 가능하나 하드코드 버전 관리. | Migration 클래스 추가. 표준 패턴. | D-A와 동일하나 키 7개 × 버전. |
| **MVP 구현 비용** | **최소** — JSON adapter 1개 + Repository 메서드 2개. | 중 — Entity 2개 + Dao 2개 + Migration 1개 + 모듈 배선. | 중 — 요일별 로직 분기. |
| **공개 배포 시 부담** | k/v JSON은 타입 안전성 약함 (서버처럼 스키마 검증 절차 필요). | DB 레벨 타입 보장. | D-A와 동일. |
| **코드 일관성** | 기존 `SettingsEntity` 모든 필드가 k/v 문자열 — 일관. | 단일 k/v 원칙 깨짐 (API 키/프린터 IP 등은 계속 k/v). | 일관. |

## 추천안: **옵션 D-A**

근거:

1. **MVP 구현 비용 최소 + 마이그레이션 회피**. Room version 1 유지.
2. **k/v 단일 테이블 원칙 유지** (CLAUDE.md "로컬 Room은 설정 전용 단일 settings k/v 테이블").
3. **유일성 강제는 ViewModel 한 곳**이면 충분 — 편집 흐름이 단 하나(WeeklyScheduleViewModel)이므로 DB 제약 이득이 실체 없음.
4. **저장 크기 예상치 < 5KB** — 7요일 × 평균 3슬롯 × 태그문자열 최대 50바이트 ≈ 1~2KB. 전체 rewrite 비용 미미.
5. 공개 배포 시 스키마 검증 필요해지면 `schedule_v1` → `schedule_v2` 전환으로 대응. D-B의 Migration도 공개 배포에서는 재설계 대상.

ADR-0006에 박는 결정 #1.

## 데이터 모델 상세

### 도메인 모델 (Kotlin)

```kotlin
// data/schedule/Slot.kt
data class Slot(
    val tag: String,    // 표시형 원형 (예: "IT", "모든주제")
    val pages: Int      // 1..5
)

// data/schedule/DaySchedule.kt
data class DaySchedule(
    val enabled: Boolean,
    val hour: Int,          // 0..23
    val minute: Int,        // 0..59
    val slots: List<Slot>   // 요일 × 태그 유일 — 앱 코드에서 강제
)

// data/schedule/WeeklySchedule.kt
data class WeeklySchedule(
    val byDay: Map<DayOfWeek, DaySchedule>
) {
    fun slotsForDay(day: DayOfWeek): List<Slot> =
        byDay[day]?.takeIf { it.enabled }?.slots.orEmpty()

    fun scheduledDays(): List<Pair<DayOfWeek, DaySchedule>> =
        byDay.entries
            .filter { (_, ds) -> ds.enabled && ds.slots.isNotEmpty() }
            .map { it.key to it.value }

    companion object {
        fun default(): WeeklySchedule = WeeklySchedule(
            byDay = DayOfWeek.values().associateWith { DaySchedule(enabled = false, hour = 7, minute = 0, slots = emptyList()) }
        )
    }
}
```

### JSON 스키마 (Gson 직렬화)

```json
{
  "monday":    { "enabled": true,  "hour": 7, "minute": 0, "slots": [{"tag": "모든주제", "pages": 2}] },
  "tuesday":   { "enabled": false, "hour": 0, "minute": 0, "slots": [] },
  "wednesday": { ... },
  "thursday":  { ... },
  "friday":    { ... },
  "saturday":  { ... },
  "sunday":    { ... }
}
```

- 최상위 키는 `java.time.DayOfWeek.name().lowercase()` 규약.
- `Map<DayOfWeek, DaySchedule>` 직렬화를 위해 `TypeToken<Map<String, DaySchedule>>`로 변환 후 enum 매핑 처리.

### `SettingsRepository` 신규 API

```kotlin
@Singleton
class SettingsRepository @Inject constructor(
    private val settingsDao: SettingsDao,
    private val gson: Gson   // 신규 주입 — NetworkModule이 이미 @Provides Gson. 같은 인스턴스 주입 또는 DI 모듈에 @Provides Gson 유지 확인.
) {
    suspend fun getWeeklySchedule(): WeeklySchedule {
        val raw = get(SettingsEntity.KEY_WEEKLY_SCHEDULE) ?: return WeeklySchedule.default()
        return runCatching { parseWeeklySchedule(raw) }
            .getOrElse { WeeklySchedule.default() }   // 파싱 실패 시 기본값으로 복구
    }

    fun observeWeeklySchedule(): Flow<WeeklySchedule> =
        observe(SettingsEntity.KEY_WEEKLY_SCHEDULE).map { raw ->
            raw?.let { runCatching { parseWeeklySchedule(it) }.getOrNull() } ?: WeeklySchedule.default()
        }

    suspend fun setWeeklySchedule(schedule: WeeklySchedule) {
        set(SettingsEntity.KEY_WEEKLY_SCHEDULE, serializeWeeklySchedule(schedule))
    }

    suspend fun getSlotsForDay(day: DayOfWeek): List<Slot> =
        getWeeklySchedule().slotsForDay(day)

    private fun parseWeeklySchedule(raw: String): WeeklySchedule { ... }
    private fun serializeWeeklySchedule(schedule: WeeklySchedule): String { ... }
}
```

### 유일성 강제 로직 (ViewModel에서 호출)

```kotlin
fun validateSlotInsert(
    day: DayOfWeek,
    newSlot: Slot,
    editingIndex: Int?    // 신규면 null, 편집이면 기존 슬롯 인덱스
): ValidationResult {
    val current = schedule.byDay[day]?.slots.orEmpty()
    val others = current.filterIndexed { idx, _ -> idx != editingIndex }
    val newKey = TagNormalizer.normalize(newSlot.tag)
    val duplicate = others.any { TagNormalizer.normalize(it.tag) == newKey }
    return when {
        duplicate -> ValidationResult.DuplicateTag(existingTag = others.first { TagNormalizer.normalize(it.tag) == newKey }.tag)
        newSlot.pages !in 1..5 -> ValidationResult.InvalidPages
        newSlot.tag.isBlank() -> ValidationResult.EmptyTag
        else -> ValidationResult.Ok
    }
}

sealed class ValidationResult {
    object Ok : ValidationResult()
    data class DuplicateTag(val existingTag: String) : ValidationResult()
    object InvalidPages : ValidationResult()
    object EmptyTag : ValidationResult()
}
```

- `TagNormalizer.normalize`는 tag-system에서 이미 구현.

## 마이그레이션 (기존 단일 설정 → 주간 스케줄)

### 트리거 시점

- 앱 시작 시 `DailyNewsletterApp.onCreate()` 또는 `SettingsRepository` 생성 시 1회.
- 또는 `WeeklyScheduleViewModel.init` 시점 lazy.

### 마이그레이션 로직

```kotlin
suspend fun SettingsRepository.migrateLegacyPrintTimeIfNeeded() {
    val alreadyMigrated = get(SettingsEntity.KEY_WEEKLY_SCHEDULE) != null
    if (alreadyMigrated) return

    val legacyHour = get(SettingsEntity.KEY_PRINT_TIME_HOUR)?.toIntOrNull()
    val legacyMinute = get(SettingsEntity.KEY_PRINT_TIME_MINUTE)?.toIntOrNull()
    val legacyPages = get(SettingsEntity.KEY_NEWSLETTER_PAGES)?.toIntOrNull() ?: 2

    if (legacyHour == null || legacyMinute == null) {
        // 완전 신규 사용자 — 기본값 저장
        setWeeklySchedule(WeeklySchedule.default())
        return
    }

    val defaultDay = DaySchedule(
        enabled = true,
        hour = legacyHour,
        minute = legacyMinute,
        slots = listOf(Slot(tag = FREE_TOPIC_TAG, pages = legacyPages))
    )
    val migrated = WeeklySchedule(
        byDay = DayOfWeek.values().associateWith { defaultDay }
    )
    setWeeklySchedule(migrated)
    // 기존 키는 deprecated 상태로 남겨둠. 제거는 스키마 version 2 마일스톤에서.
}
```

### 기존 키 처분

- `KEY_PRINT_TIME_HOUR`, `KEY_PRINT_TIME_MINUTE`, `KEY_NEWSLETTER_PAGES` — **본 플랜에서는 제거하지 않음**. `SettingsRepository.getPrintTimeHour()` 등 기존 getter는 deprecated 표시(`@Deprecated("use getWeeklySchedule()")`). 호출 지점이 `WorkScheduler.scheduleAll()`이며, 본 플랜 구현 단계에서 자연 제거.
- 완전 삭제는 릴리스 하드닝 시점의 별도 cleanup 커밋.

### 신규 사용자 플로우

1. 최초 설치 → `SettingsEntity` 전체 비어 있음 → `migrateLegacyPrintTimeIfNeeded()` 실행 → `WeeklySchedule.default()` 저장 (모든 요일 OFF).
2. 사용자가 `WeeklyScheduleScreen`에서 요일별 설정 시작.

### 기존 사용자 플로우

1. 앱 업데이트 → `migrateLegacyPrintTimeIfNeeded()` 실행 → legacy 키들로부터 7요일 동일 설정 생성.
2. 자동 마이그레이션 결과가 OFF 요일 없이 전부 ON으로 깔림 → 사용자가 원하는 요일만 남기고 나머지는 OFF 토글.

## 에러 / 빈 상태 (백엔드 보장 계약)

| 상황 | 정책 |
|---|---|
| `schedule_v1` JSON 파싱 실패 | `WeeklySchedule.default()` 반환 + 로그 경고. UI는 "설정이 손상됐습니다 — 다시 설정해주세요" 토스트 (ViewModel 책임). |
| 기존 `KEY_PRINT_TIME_HOUR/MINUTE` 미설정 + `KEY_WEEKLY_SCHEDULE`도 없음 | `WeeklySchedule.default()` — 모든 요일 OFF. 사용자가 명시적 설정 전까지 프린트 없음. |
| 저장 직후 읽기가 stale (Flow 지연) | DAO `@Upsert`는 즉시 commit — 다음 `observe` emit에서 갱신. UI는 낙관적 업데이트 권장. |
| `slotsForDay`에서 enabled=false인 요일 | 빈 리스트 반환 (slotsForDay 함수 내부에서 filter). |
| 슬롯이 0개인 enabled 요일 | 빈 리스트 반환. `WorkScheduler.scheduleWeeklyPrints()`가 이 요일은 enqueue 스킵. |
