# Task Brief: 인쇄 흐름을 Android PrintManager로 전환

Task ID: TASK-20260428-023
Status: active

## Goal
TASK-021/022 진단으로 raw IPP + 자체 PDF 렌더링 경로가 막다른 골목임이 확인됨 (PDF 빈 파일 + 프린터가 PDF 포맷 거부). 이를 폐기하고 Android `PrintManager` + `PrintDocumentAdapter` 표준 경로로 전환. 사용자는 매일 1탭 ("인쇄" 다이얼로그 탭)으로 인쇄.

## User-visible behavior
- 뉴스레터 카드의 인쇄 버튼 탭 → **시스템 인쇄 다이얼로그**가 뜸. 프린터 선택(첫 회) + 매수 확인 후 "인쇄" 탭 → 디바이스에 설치된 print service plugin(Canon Print Service / Mopria 등)이 본문이 그려진 종이로 인쇄.
- 다이얼로그 호출 직후 Notion의 Status를 `printed`로 업데이트 (낙관적 — 사용자가 다이얼로그에서 취소해도 status가 printed로 표시되는 트레이드오프 수용. 후속 Task에서 `PrintJob` observe로 보강 가능).
- 어플 어디에도 raw IPP/PDF 호출 없음.

## Scope

### 1. `app/src/main/java/com/dailynewsletter/service/PrintService.kt` — 재작성
- 기존 IPP/HttpURLConnection 로직 폐기.
- 새 시그니처: `fun startSystemPrint(activity: Activity, html: String, title: String)` — synchronous 트리거 (`PrintManager.print` 자체는 다이얼로그를 띄우고 즉시 반환).
- 내부:
  ```kotlin
  val webView = WebView(activity).apply {
      webViewClient = object : WebViewClient() {
          override fun onPageFinished(view: WebView?, url: String?) {
              val adapter = createPrintDocumentAdapter(title)
              val printManager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
              printManager.print(title, adapter, null)
          }
      }
      loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
  }
  ```
- WebView가 PrintDocumentAdapter callback 동안 살아있도록 `Activity` 스코프에서 hold (간단히 클래스 필드/companion에 강참조 보관, 또는 onPageFinished 내 lambda에서 `webView` 캡처).
- `printerIp`/`printerEmail` 분기 모두 제거. PrintManager가 프린터 선택 담당.
- TASK-021/022에서 추가했던 `Log.d/e/w` 호출은 제거 가능 (필요한 한두 줄만 유지: e.g. `Log.d(TAG, "startSystemPrint title=$title")`).

### 2. `app/src/main/java/com/dailynewsletter/service/PdfService.kt` — 삭제
- PrintManager가 PDF 생성·변환을 담당하므로 더 이상 필요 없음.
- 파일 삭제. `import com.dailynewsletter.service.PdfService` 참조도 모두 제거.

### 3. `app/src/main/java/com/dailynewsletter/data/repository/NewsletterRepository.kt`
- `printNewsletter(id)` 의 흐름 변경:
  - `PrintService.printHtml(...)` 호출 제거 (PrintService는 더 이상 Repository에서 호출되지 않음 — Repository는 Activity 컨텍스트가 없음).
  - 메서드 이름을 `markNewsletterPrinted(id: String)`로 리네임. 내부는 `updateNewsletterStatus(id, "printed")` 호출만.
- 생성자에서 `printService: PrintService` 주입 제거.
- `getNewsletter(id)`/`getNewsletters()` 같은 다른 함수는 손대지 말 것.

### 4. `app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterViewModel.kt`
- `printNewsletter(id: String)` 시그니처를 `printNewsletter(activity: Activity, item: NewsletterUiItem)`로 변경.
- 흐름:
  ```kotlin
  fun printNewsletter(activity: Activity, item: NewsletterUiItem) {
      val html = item.htmlContent ?: run {
          _uiState.update { it.copy(error = "뉴스레터 내용이 없습니다") }
          return
      }
      printService.startSystemPrint(activity, html, item.title)
      viewModelScope.launch(exceptionHandler) {
          newsletterRepository.markNewsletterPrinted(item.id)
          loadNewsletters()
      }
  }
  ```
- ViewModel은 `PrintService` 직접 주입 받음 (Hilt).

### 5. `app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterScreen.kt`
- 인쇄 버튼 onClick 안에서 `LocalContext.current as? Activity`로 Activity 획득 후 `viewModel.printNewsletter(activity, item)` 호출.
- Activity 캐스팅 실패(null) 시 무시 또는 에러 표시.
- 기타 UI는 변경 없음.

### 6. `app/src/main/AndroidManifest.xml`
- `android:usesCleartextTraffic="true"` 그대로 유지 (다른 IPP 흔적은 제거 안 됨; 후속 cleanup 가능).
- 새로운 권한 추가 0건.

## Out of Scope
- Daily 스케줄러 (WorkManager periodic) — 후속 Task.
- Daily 알림 발송 — 후속 Task.
- Settings 화면에서 `printerIp`/`printerEmail` 입력 필드 제거 — 후속 cleanup. 입력해도 무시되는 상태로 남겨둠.
- `SettingsRepository.getPrinterIp/getPrinterEmail` 함수 제거 — 후속.
- `PrintJob.isCompleted` observe로 정확한 status 갱신 — 후속.
- 인쇄 Status 별도 UI 표시(인쇄 진행 중/완료/실패) — 후속.

## Files Owned By This Task
- `app/src/main/java/com/dailynewsletter/service/PrintService.kt`
- `app/src/main/java/com/dailynewsletter/service/PdfService.kt` (삭제)
- `app/src/main/java/com/dailynewsletter/data/repository/NewsletterRepository.kt`
- `app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterViewModel.kt`
- `app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterScreen.kt`

## Files Explicitly Not Owned
- `app/src/main/AndroidManifest.xml` (확인만, 변경 0)
- `app/src/main/java/com/dailynewsletter/ui/settings/*`
- `app/src/main/java/com/dailynewsletter/data/repository/SettingsRepository.kt`
- `app/src/main/java/com/dailynewsletter/data/local/entity/SettingsEntity.kt`
- 그 외 모든 파일.

## Forbidden Changes
- No new dependency (Mopria/Canon plugin 등은 사용자 디바이스가 가짐).
- No new permission.
- No DB 스키마 변경.
- No 다른 ViewModel/Repository 함수 시그니처 변경 (printNewsletter 외).
- No Settings 화면 변경.
- No daily scheduler 구현.

## Android Constraints
- Compose에서 Activity 획득은 `LocalContext.current as? Activity` (Hilt가 ComponentActivity 컨텍스트 제공).
- `PrintManager.print`는 Main 스레드에서 호출.
- WebView는 Main 스레드 전용. Activity context 필요.
- WebView가 `onPageFinished` callback 후 `PrintDocumentAdapter`에서 페이지 정보를 다시 조회하므로 GC되지 않도록 강참조 유지 필수.

## Acceptance Criteria
- [ ] `PrintService.startSystemPrint(activity, html, title)` 함수 존재 (grep `startSystemPrint`).
- [ ] `PrintService.kt`에서 `HttpURLConnection`/`buildIppPrintRequest`/`writeIppAttribute` 모두 사라짐 (grep 0건).
- [ ] `PdfService.kt` 파일이 삭제됨.
- [ ] `NewsletterRepository`에 `markNewsletterPrinted` 함수 존재. `printNewsletter` 함수는 사라짐.
- [ ] `NewsletterRepository` 생성자에서 `printService: PrintService` 주입이 사라짐.
- [ ] `NewsletterViewModel.printNewsletter`가 `Activity`를 첫 인자로 받는 시그니처.
- [ ] `NewsletterScreen`에서 `LocalContext.current as? Activity` 또는 `as Activity` 패턴 등장.
- [ ] grep `PdfService` → 0건 (import 포함).
- [ ] grep `application/ipp\|/ipp/print\|writeIppAttribute` → 0건.
- [ ] 빌드 성공 또는 SKIPPED_ENVIRONMENT_NOT_AVAILABLE.

## Verification Command Candidates
- `grep -rn "PdfService\\|HttpURLConnection\\|writeIppAttribute\\|/ipp/print" app/src/main`
- `grep -n "startSystemPrint\\|markNewsletterPrinted\\|PrintManager" app/src/main/java/com/dailynewsletter`
- `./gradlew :app:assembleDebug`

## Expected Implementer Output
- 변경 파일 5개 (4 modify + 1 delete).
- `PrintService` 새 구현 핵심 코드 인용.
- `NewsletterScreen` Activity 획득 + 호출 부분 인용.
- `NewsletterRepository.markNewsletterPrinted` 인용.
- grep 결과.
- 빌드 결과.
- 사용자 다음 동작 1줄: 재빌드 → 인쇄 버튼 → 시스템 다이얼로그 → 프린터 선택(첫 회) → "인쇄" 탭 → 종이 출력 확인.

## STOP_AND_ESCALATE
- Hilt가 `NewsletterViewModel`에 Activity 스코프 의존성 주입을 거부할 경우 — Activity는 ViewModel 인자로만 전달, 주입 X.
- WebView가 `onPageFinished` 후 GC되어 PrintDocumentAdapter callback이 실패하면 — Singleton PrintService에 WebView 강참조 보관 또는 Activity의 lifecycle scope에 attach.
- `LocalContext.current`가 ComponentActivity가 아닌 경우 (예: ApplicationContext) — 흔치 않으나 발견 시 escalate.
