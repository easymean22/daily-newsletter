# Task Brief: Print flow diagnostic logging

Task ID: TASK-20260428-021
Status: active

## Goal
사용자가 어플에서 프린터 IP 입력 후 수동 출력 → "성공처럼 보이지만 실제 인쇄 결과 미확인" 상황을 진단하기 위해, PDF 생성·IPP 호출 전 과정에 식별 가능한 Log를 심는다. **동작은 일절 바꾸지 않는다.** 본 Task의 결과물(다음 logcat)로 진짜 실패 지점을 식별한 뒤, 후속 Task에서 픽스를 진행할 예정.

## User-visible behavior
- 변경 없음. 출력 동작·UI·상태 변화 모두 동일.
- 단, logcat에서 `D/PrintService` / `D/PdfService` 태그로 출력 경로 흐름을 추적 가능.

## Scope
1. **`app/src/main/java/com/dailynewsletter/service/PrintService.kt`**:
   - 클래스 상단 `private val TAG = "PrintService"` 또는 companion object의 `const val TAG`.
   - `printHtml(html, title)` 진입 시:
     - `Log.d(TAG, "printHtml ENTER title=$title html.length=${html.length}")`
     - 프린터 IP/이메일 결정 직후: `Log.d(TAG, "route=ipp ip=$printerIp")` 또는 `route=email`/`route=none`.
   - `pdfFile` 생성 직후: `Log.d(TAG, "pdf ready path=${pdfFile.absolutePath} size=${pdfFile.length()}")`.
   - `printViaIpp(...)` 진입: `Log.d(TAG, "ipp ENTER url=http://$printerIp:631/ipp/print pdfPath=$pdfPath")`.
   - IPP 바이트 빌드 직후: `Log.d(TAG, "ipp request bytes=${ippRequest.size}")`.
   - `responseCode` 받은 직후: `Log.d(TAG, "ipp HTTP responseCode=$responseCode")`.
   - 200~299 아닌 경우: 던지기 전 `Log.w(TAG, "ipp non-2xx responseCode=$responseCode")` 한 줄.
   - try/catch 추가: `try { ... } catch (e: Exception) { Log.e(TAG, "ipp failed", e); throw e }` — IOException/SecurityException 등이 던져진 경우 stack trace를 logcat에 남기도록. 기존 `finally { connection.disconnect() }`는 그대로 유지.
   - **던지기 거동은 동일** — 단지 던지기 전에 로그 한 줄 추가하고 다시 던질 것. 예외를 삼키지 말 것.

2. **`app/src/main/java/com/dailynewsletter/service/PdfService.kt`**:
   - `private val TAG = "PdfService"` 또는 companion object 추가.
   - `htmlToPdf(html, fileName)` 진입: `Log.d(TAG, "htmlToPdf ENTER html.length=${html.length} fileName=$fileName")`.
   - `onPageFinished` 진입: `Log.d(TAG, "onPageFinished webView.width=${webView.width} height=${webView.height}")` — WebView 크기 0 의혹을 직접 확인하기 위함.
   - `document.writeTo` 직후: `Log.d(TAG, "pdf written file=${file.absolutePath} size=${file.length()}")`.
   - 예외/예상 외 분기 없으면 추가 catch는 불필요. 기존 흐름 그대로.

## Out of Scope
- printer-uri 하드코딩(`ipp://localhost/...`) 픽스 — **하지 말 것**. 후속 Task에서 처리.
- IPP 속성 보강(`requesting-user-name` 등) — 후속.
- WebView measure/layout 추가 — 후속.
- 멀티페이지 PDF 처리 — 후속.
- PrintManager 전환 — 후속.
- ViewModel/Screen 변경 0건.
- AndroidManifest 변경 0건.

## Files Owned By This Task
- `app/src/main/java/com/dailynewsletter/service/PrintService.kt`
- `app/src/main/java/com/dailynewsletter/service/PdfService.kt`

## Files Explicitly Not Owned
- 그 외 모든 파일.

## Forbidden Changes
- No new dependency.
- No 동작 변경 (조건 분기·예외 처리 흐름 동일 유지).
- No printer-uri/IPP 속성 변경.
- No WebView 렌더링 로직 변경.

## Acceptance Criteria
- [ ] PrintService.kt에 `Log.d(TAG, ...)` 호출이 5개 이상 (printHtml ENTER, route, pdf ready, ipp ENTER, ipp request bytes, ipp responseCode 중 5+).
- [ ] PrintService.printViaIpp 내부 try-catch에 `Log.e(TAG, "ipp failed", e); throw e` 패턴 존재.
- [ ] PdfService.kt에 `Log.d(TAG, ...)` 호출이 3개 (htmlToPdf ENTER, onPageFinished, pdf written).
- [ ] PrintService.kt와 PdfService.kt 어디에도 기존 `throw`/조건 분기를 제거·수정하지 않았음 (diff에서 동작 변경 없음 확인).
- [ ] grep `import android.util.Log` 가 두 파일에 추가되어 있다.
- [ ] 빌드 성공 또는 SKIPPED_ENVIRONMENT_NOT_AVAILABLE.

## Verification Command Candidates
- `grep -n "Log\\.\\(d\\|w\\|e\\)" app/src/main/java/com/dailynewsletter/service/PrintService.kt`
- `grep -n "Log\\.\\(d\\|w\\|e\\)" app/src/main/java/com/dailynewsletter/service/PdfService.kt`
- `./gradlew :app:assembleDebug`

## Expected Implementer Output
- 변경 파일 2개. diff는 로그 추가 + import만.
- 추가된 Log 호출 줄 인용.
- grep 결과.
- 빌드 결과.
- 사용자 다음 동작 1줄: 재빌드 후 뉴스레터 카드의 인쇄 버튼 누르고 logcat에서 `PrintService\\|PdfService` 필터로 흐름 캡처.
