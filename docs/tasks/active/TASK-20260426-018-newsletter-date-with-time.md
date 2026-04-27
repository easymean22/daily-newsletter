# Task Brief: Newsletters DB의 Date에 생성 시간 포함

Task ID: TASK-20260426-018
Status: active

## Goal
Notion Newsletters DB의 Date 컬럼이 날짜만이 아니라 생성 **시간**까지 표시되도록. Notion date 속성은 ISO 8601 datetime 값을 받으면 시간을 자동 표시.

## User-visible behavior
- 새 뉴스레터 생성 → Notion DB에서 Date 컬럼이 "2026년 04월 26일 오후 3:15" 같은 형태로 시간 함께 표시.
- 기존 행은 영향 없음(과거 row의 값은 갱신하지 않음).

## Scope
`NewsletterRepository.saveNewsletter` 안에서 Date property 값:
- 현재: `LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)` → `"2026-04-26"`.
- 변경: `OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)` → 예: `"2026-04-26T15:15:30+09:00"`.

Notion API의 `date.start`는 date-only 또는 datetime 모두 허용 — 자동 감지.

읽는 쪽 (`NewsletterRepository.refreshNewsletters` 등에서 `page.properties["Date"]?.date?.start ?: ""`)은 변경 없이 그대로 동작 (문자열 그대로 들고 다님).

## Out of Scope
- DB 스키마 변경 없음 (`type = "date"` 그대로 — Notion은 date 속성에서 시간 자동 처리).
- Topics DB의 Date 변경 없음 (Newsletters만; 후속 결정).
- 표시 형식 커스터마이징 없음 (Notion 기본 포맷 사용).
- 과거 row 마이그레이션 없음.
- ViewModel/Screen 표시 형식 변경 없음 (UI는 string 그대로 보여줌).

## Files Owned By This Task
- `app/src/main/java/com/dailynewsletter/data/repository/NewsletterRepository.kt`

## Files Explicitly Not Owned
- `app/src/main/java/com/dailynewsletter/data/repository/TopicRepository.kt`
- `app/src/main/java/com/dailynewsletter/service/NotionSetupService.kt`
- 그 외 모든 파일.

## Forbidden Changes
- No new dependency.
- No DB 스키마 추가/변경.
- No public API contract change beyond saveNewsletter body.
- TASK-007~015 변경분 보존.
- `chunkText` / `htmlToBlocks` helper 시그니처/동작 보존.

## Android Constraints
- `java.time.OffsetDateTime` + `DateTimeFormatter.ISO_OFFSET_DATE_TIME` 표준 라이브러리 사용.
- API level 26+ 가정 (이미 본 프로젝트 minSdk 가 그 이상이라고 추정 — 그렇지 않으면 ThreeTenABP 등 추가 의존성 escalate).

## Acceptance Criteria
- [ ] `saveNewsletter`에서 Date `start` 값이 `OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)` 또는 동등한 ISO datetime 문자열을 사용한다.
- [ ] 다른 메서드(refresh/parse)는 변경되지 않는다.
- [ ] grep `OffsetDateTime` 또는 `ISO_OFFSET_DATE_TIME` in NewsletterRepository.kt → 1+ hit.
- [ ] grep `LocalDate.now\(\)` in NewsletterRepository.kt → saveNewsletter 안에서는 0 hits (다른 헬퍼에서 쓴다면 무관, but 본 변경 위치에서는 제거).
- [ ] 빌드 성공 또는 SKIPPED_ENVIRONMENT_NOT_AVAILABLE.

## Verification Command Candidates
- `grep -n "OffsetDateTime\|ISO_OFFSET_DATE_TIME\|LocalDate" app/src/main/java/com/dailynewsletter/data/repository/NewsletterRepository.kt`
- `./gradlew :app:assembleDebug`

## Expected Implementer Output
- 변경 파일 1개.
- 변경 줄(이전/이후) 인용.
- grep 결과.
- 빌드 결과.

## STOP_AND_ESCALATE
- minSdk가 java.time을 직접 지원하지 않아 desugar 라이브러리 추가가 필요한 경우 escalate (지금까지 빌드된 흔적으로 보아 이미 가능).
