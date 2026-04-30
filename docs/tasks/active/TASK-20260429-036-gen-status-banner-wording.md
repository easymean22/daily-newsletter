# Task Brief: 생성 진행 배너 + 재시도/실패 메시지 가시성 + 워딩 정리

Task ID: TASK-20260429-036
Status: active

## Goal
TASK-035로 추가한 재시도 메시지가 사용자에게 잘 안 보임. 또한 일부 워딩이 부정적("혼잡", "실패"). 다음 3가지 픽스:
1. **상시 진행 배너**: 생성 중/재시도 중일 때 BottomSheet 닫혀도 NewsletterScreen 상단에 진행 배너가 계속 보이도록.
2. **최종 실패 시 모달 알림**: 모든 재시도 소진 후 AlertDialog로 "잠시 후 다시 시도해 주세요" 표시.
3. **워딩 중립화**: "혼잡", "실패" 같은 부정적 표현 모두 제거.

## User-visible behavior
- 사용자가 생성 버튼 누름 → BottomSheet 닫혀도 NewsletterScreen 상단(TopAppBar 아래)에 가로 배너:
  - Running: "뉴스레터 생성 중..." + 작은 spinner.
  - Retrying: "잠시 후 다시 시도하고 있어요 (1/3)" + spinner.
- 모든 재시도 실패 시: AlertDialog 모달 — 제목 "생성을 마치지 못했어요", 본문 "잠시 후 다시 시도해 주세요", 확인 버튼 1개.
- Success: 기존 Snackbar 그대로 ("뉴스레터가 추가되었습니다").
- 시스템 notification에서도 "생성 실패" → "생성 미완료" 또는 "생성 마무리 안 됨" 같은 중립 워딩.

## Scope

### 1. `app/src/main/java/com/dailynewsletter/service/GeminiRetry.kt`
- `withRetry`의 transient 소진 시 throw하는 `IllegalStateException` 메시지를 중립으로:
  - 기존: `"Gemini가 일시적으로 혼잡합니다. 잠시 후 다시 시도해 주세요"`
  - 변경: `"잠시 후 다시 시도해 주세요"` (단, 메시지 자체는 ViewModel/Screen에서 알림 띄울 때만 사용 — 또는 sentinel string으로 감지).
- 그 외 동작 변경 0.

### 2. `app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterViewModel.kt`
- `ManualGenStatus.Retrying` 의 메시지 생성 부분:
  - 기존: `"혼잡으로 재시도 중입니다. 잠시만 기다려 주세요 (${attempt}/${total})"`
  - 변경: `"잠시 후 다시 시도하고 있어요 (${attempt}/${total})"` 또는 `"조금만 기다려 주세요 (${attempt}/${total})"`.
- `notificationHelper.notify` 호출의 title `"생성 실패"` → `"생성 미완료"` 또는 `"잠시 후 다시 시도"`.
- `ManualGenStatus.Running` 표시 메시지가 아직 `"뉴스레터 생성 중..."` 같은 텍스트로 노출되는지 확인 — UI에서 사용.
- 다른 로직 0 변경.

### 3. `app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterScreen.kt`
- **신규 배너 Composable**: `ManualGenBanner(status: ManualGenStatus)` 또는 inline:
  - status가 Running 또는 Retrying이면 가로 풀폭 Surface(또는 Card) 한 줄.
  - 좌측 작은 `CircularProgressIndicator(modifier = Modifier.size(16.dp))`.
  - 우측 텍스트: Running → "뉴스레터 생성 중...", Retrying → status.message.
  - tonal/elevated background, 작은 padding.
- 배너 위치: Scaffold의 content 영역 최상단, LazyVerticalGrid 위. 또는 selectedNewsletter 진입 시에도 노출되도록 Scaffold 안쪽(공통 위치).
- **AlertDialog**: status가 `ManualGenStatus.Failed`이고 message가 "잠시 후 다시 시도해 주세요"를 포함(또는 sentinel)이면 AlertDialog:
  - title = "생성을 마치지 못했어요"
  - text = "잠시 후 다시 시도해 주세요"
  - confirmButton = TextButton(onClick = `viewModel.clearManualGenStatus()`) { Text("확인") }
- 기존 Snackbar 처리에서 Failed 분기 제거 (또는 transient 케이스만 AlertDialog로 분기). Success Snackbar는 보존.
- BottomSheet의 inline retry message·버튼 라벨도 이 워딩에 맞춰 정리. ("재시도 중..." → "잠시만요...").

## Out of Scope
- GeminiTopicSuggester(키워드 추가 시 토픽 추천)에 동일 처리 — 후속.
- 알람 자동 생성 흐름의 진행 표시 — TASK-027.
- 다른 종류의 에러(401/400) 메시지 워딩 — 후속.

## Files Owned By This Task
- `app/src/main/java/com/dailynewsletter/service/GeminiRetry.kt`
- `app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterViewModel.kt`
- `app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterScreen.kt`

## Files Explicitly Not Owned
- 그 외 모든 파일.

## Forbidden Changes
- No new dependency.
- No `withRetry` 시그니처 변경.
- No `ManualGenStatus`의 기존 variant 시그니처 변경.

## Acceptance Criteria
- [ ] grep `혼잡` in newsletter-related kotlin (Repository/ViewModel/Screen + GeminiRetry) → **0건**.
- [ ] grep `실패` in newsletter-related kotlin user-facing string → **0건** (코멘트 제외).
- [ ] `NewsletterScreen.kt`에 Running/Retrying 분기로 배너를 그리는 코드 (Surface/Card 또는 Banner Composable).
- [ ] `NewsletterScreen.kt`에 AlertDialog의 title "생성을 마치지 못했어요" + text "잠시 후 다시 시도해 주세요".
- [ ] 빌드 성공 또는 SKIPPED_ENVIRONMENT_NOT_AVAILABLE.

## Verification Command Candidates
- `grep -rn "혼잡\\|실패" app/src/main/java/com/dailynewsletter/{service,ui/newsletter}` (코멘트 외 0건 기대)
- `grep -n "AlertDialog\\|생성 중\\|잠시 후" app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterScreen.kt`
- `./gradlew :app:assembleDebug`

## Expected Implementer Output
- 변경 파일 3개. 핵심 코드 인용 (배너 + AlertDialog + 워딩 변경). grep 결과. 빌드 결과. 사용자 다음 동작 1줄.
