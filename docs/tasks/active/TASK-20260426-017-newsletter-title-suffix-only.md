# Task Brief: 뉴스레터 제목에서 "Daily Newsletter - 날짜 (...)" prefix 제거

Task ID: TASK-20260426-017
Status: active

## Goal
Notion Newsletters DB에 저장되는 뉴스레터 페이지 제목을 `titleSuffix` 본문만 사용하도록 변경. 현재는 `Daily Newsletter - 2026년 04월 26일 (titleSuffix)` 형태.

## User-visible behavior
- Notion Newsletters DB의 새 뉴스레터 row 제목 = Gemini가 만든 부제 문자열 그대로 (예: "고성능 네트워크 가속을 위한 RDMA, GPU Direct RDMA, SmartNIC/DPU 기술 심층 분석").
- 빈 titleSuffix가 오면 fallback으로 "Daily Newsletter - YYYY년 MM월 DD일" 사용 (절대 빈 문자열로 저장하지 말 것 — Notion title이 비면 페이지 식별이 어려움).

## Scope
`NewsletterGenerationService.kt:132` 근처:
```kotlin
val title = "Daily Newsletter - $today${if (titleSuffix.isNotBlank()) " ($titleSuffix)" else ""}"
```
↓
```kotlin
val title = if (titleSuffix.isNotBlank()) titleSuffix else "Daily Newsletter - $today"
```

## Out of Scope
- HTML 본문 안의 `<h1>Daily Newsletter - $today</h1>` 헤더는 유지(line 78 부근). 본문 헤더와 페이지 제목은 별개.
- Notion DB 스키마 변경 없음.
- Repository/ViewModel/UI 변경 없음.

## Files Owned By This Task
- `app/src/main/java/com/dailynewsletter/service/NewsletterGenerationService.kt`

## Files Explicitly Not Owned
- 그 외 모든 파일.

## Forbidden Changes
- No new dependency.
- No prompt 의미 변경.
- HTML body의 `<h1>` 헤더 변경 금지.
- TASK-007~015 변경분 보존.

## Acceptance Criteria
- [ ] `val title = ...` 줄이 `if (titleSuffix.isNotBlank()) titleSuffix else "Daily Newsletter - $today"` 형태로 변경.
- [ ] HTML 본문 안의 `<h1>Daily Newsletter - $today</h1>`는 그대로 유지.
- [ ] grep `titleSuffix` → 기존 hits 유지(파싱 + fallback 분기).
- [ ] grep `Daily Newsletter -` → 본문 헤더 + fallback 줄 = 2 hits.
- [ ] 빌드 성공 또는 SKIPPED_ENVIRONMENT_NOT_AVAILABLE.

## Verification Command Candidates
- `grep -n "Daily Newsletter -\|titleSuffix" app/src/main/java/com/dailynewsletter/service/NewsletterGenerationService.kt`
- `./gradlew :app:assembleDebug`

## Expected Implementer Output
- 변경 파일 1개. diff 1~2줄.
- 변경 전/후 줄 인용.
- grep 결과.
- 빌드 결과.
