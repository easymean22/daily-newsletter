# Task Brief: PDF 실제 렌더링 + IPP 속성 정상화

Task ID: TASK-20260428-022
Status: active

## Goal
TASK-021 진단 결과: 어플이 IPP 200 OK는 받지만 **빈 PDF(741 byte)**를 보내고 있고, 프린터에서 종이가 한 장도 안 나옴. 두 가지 픽스를 한 Brief에 묶음.

1. **PdfService**가 HTML을 실제로 렌더링한 멀티페이지 PDF를 만들도록.
2. **PrintService.printViaIpp**가 IPP 표준 속성을 갖춘 요청을 보내도록 (`printer-uri`/`requesting-user-name`).

## User-visible behavior
- 뉴스레터 카드 → 인쇄 버튼 → 프린터에서 **본문이 그려진 종이**가 나온다.
- 본문이 1 A4 페이지를 넘으면 여러 장 출력.
- 실패 시 사용자는 어플에서 명확한 에러 메시지를 본다 (ViewModel 흐름 그대로).

## Scope

### 1. `app/src/main/java/com/dailynewsletter/service/PdfService.kt`

`htmlToPdf(html, fileName)` 재작성. 핵심:

- `WebView`를 attach 없이 사용하지만, `draw` 전에 **measure + layout**을 반드시 거쳐 실제 contents 크기를 잡을 것.
- A4 사이즈 (595 × 842 points @ 72dpi)에 맞춰 width 고정, height는 contents 따라 계산.
- 멀티페이지: `totalHeight = webView.measuredHeight`, `pageHeight = 842` 기준으로 페이지 수 계산하고 각 페이지마다 `canvas.translate(0f, -i * pageHeight)` 적용 후 `webView.draw(canvas)`.
- `viewport`/`useWideViewPort`/`loadWithOverviewMode` 같은 WebView setting은 측정 결과를 망가뜨릴 수 있으므로 검토하고 필요한 것만 남길 것.
- `webView.createPrintDocumentAdapter(fileName)` 같은 미사용 dead code는 제거.

대안 구현(implementer 판단으로 선택 가능):
- (a) 위처럼 직접 measure/layout + 페이지별 translate.
- (b) Android `PrintDocumentAdapter` + `PrintedPdfDocument` 정식 패턴 사용. 둘 중 안정적인 쪽 선택.

남은 디버그 로그(`D/PdfService` 3개)는 그대로 유지하고, 가능하면 `pageCount`도 한 줄 추가 (`Log.d(TAG, "pageCount=$pageCount totalHeight=$totalHeight")`).

### 2. `app/src/main/java/com/dailynewsletter/service/PrintService.kt`

`printViaIpp` / `buildIppPrintRequest` 보강:

- **`printer-uri` 픽스**: 현재 `ipp://localhost/ipp/print` 하드코딩 → `ipp://$printerIp:631/ipp/print` (실제 프린터 IP·포트). `printViaIpp`에서 IP를 받아 `buildIppPrintRequest(title, pdfData, printerIp)`로 넘기도록 시그니처 한 줄 수정.
- **`requesting-user-name` 추가**: `writeIppAttribute(buffer, 0x42, "requesting-user-name", "dailynewsletter")` (또는 임의 문자열). RFC 8011 §4.1 권장 속성.
- **응답 본문 일부 로깅**: `connection.inputStream.bufferedReader().use { it.readText() }`로 IPP 응답 본문(처음 256 byte 정도) 읽어 `Log.d(TAG, "ipp response body=...")` 한 줄. 200 응답에도 IPP-level 에러가 들어있을 수 있으므로 진단 가치가 큼.
- 기존 `responseCode in 200..299` 분기·예외 로직은 그대로.
- 이미 추가된 try-catch + Log는 유지.

### Out of Scope
- `usesCleartextTraffic` 변경 0 (이미 true).
- `PrintManager`/system print dialog 전환 — 후속 결정.
- email/SMTP 분기 — 그대로.
- ViewModel/Screen/Repository 변경 0건.
- AndroidManifest 변경 0건.
- 새 dependency 추가 금지.

## Files Owned By This Task
- `app/src/main/java/com/dailynewsletter/service/PdfService.kt`
- `app/src/main/java/com/dailynewsletter/service/PrintService.kt`

## Files Explicitly Not Owned
- `app/src/main/java/com/dailynewsletter/data/repository/NewsletterRepository.kt`
- `app/src/main/java/com/dailynewsletter/ui/newsletter/*`
- 그 외 모든 파일.

## Forbidden Changes
- No new dependency.
- No 새로운 manifest 권한.
- No `printService.printHtml` 시그니처 변경 (호출자 변경 회피).
- No 동작 큰 변경 — email 분기·예외 처리 흐름 동일.

## Acceptance Criteria
- [ ] PdfService가 WebView measure + layout 후 draw하도록 변경됨 (코드에 `webView.measure(` 또는 `View.MeasureSpec` 또는 `webView.layout(` 등장).
- [ ] PdfService가 멀티페이지 처리 — 코드에 `pageCount` 또는 페이지 루프 (`for (i in 0 until ...)`) 존재.
- [ ] `webView.createPrintDocumentAdapter` dead code 제거 또는 실제 사용으로 전환.
- [ ] PrintService.kt의 `ipp://localhost/ipp/print` 문자열이 사라짐.
- [ ] `printer-uri` 값이 `printerIp` 변수를 사용해 동적 구성됨 (grep `ipp://\$` 또는 `\"ipp://\" \\+`).
- [ ] `requesting-user-name` 속성 IPP 요청에 포함 (grep `requesting-user-name`).
- [ ] IPP 응답 본문 일부를 `Log.d`로 출력 (grep `ipp response body`).
- [ ] 빌드 성공 또는 SKIPPED_ENVIRONMENT_NOT_AVAILABLE.

## Verification Command Candidates
- `grep -n "measure\\|layout\\|pageCount" app/src/main/java/com/dailynewsletter/service/PdfService.kt`
- `grep -n "ipp://\\|requesting-user-name\\|response body" app/src/main/java/com/dailynewsletter/service/PrintService.kt`
- `./gradlew :app:assembleDebug`

## Expected Implementer Output
- 변경 파일 2개. 각각 핵심 변경 코드 인용 (PdfService measure/layout/page-loop, PrintService printer-uri/requesting-user-name/response body 로깅).
- grep 결과.
- 빌드 결과.
- 사용자 다음 동작 1줄 (재빌드 → 카드 인쇄 → 프린터에서 본문 출력 + logcat의 PdfService `pageCount` + PrintService `ipp response body`).

## STOP_AND_ESCALATE
- WebView measure/layout가 attach 없는 상태에서 동작하지 않는 Android API 제약 발견 시 escalate.
- `PrintDocumentAdapter` 정식 패턴이 비동기 callback 흐름을 강제해 `suspend fun` 시그니처 유지 불가 시 escalate.
- IPP 응답 본문이 binary라 안전하게 문자열화 불가능한 경우 — Hex dump로 대체할지 escalate.
