# Task Result: TASK-20260428-023 PrintManager Migration

Task ID: TASK-20260428-023
Status: IMPLEMENTED

## Changed Files

- `app/src/main/java/com/dailynewsletter/service/PrintService.kt` — full rewrite: dropped IPP/HttpURLConnection/PdfService, new `startSystemPrint(activity, html, title)` using WebView + PrintDocumentAdapter + PrintManager
- `app/src/main/java/com/dailynewsletter/service/PdfService.kt` — DELETED
- `app/src/main/java/com/dailynewsletter/data/repository/NewsletterRepository.kt` — removed `PrintService` import + constructor injection; replaced `printNewsletter(id)` with `markNewsletterPrinted(id)` (calls `updateNewsletterStatus(id, "printed")` only)
- `app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterViewModel.kt` — added `PrintService` injection; changed `printNewsletter(id: String)` to `printNewsletter(activity: Activity, item: NewsletterUiItem)`
- `app/src/main/java/com/dailynewsletter/ui/newsletter/NewsletterScreen.kt` — added `LocalContext.current as? Activity` capture; both print button call sites updated to pass `activity` and `item`

## Key Code Snippets

**PrintService.startSystemPrint:**
```kotlin
fun startSystemPrint(activity: Activity, html: String, title: String) {
    Log.d(TAG, "startSystemPrint title=$title")
    val webView = WebView(activity).apply {
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val adapter = view!!.createPrintDocumentAdapter(title)
                val printManager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
                printManager.print(title, adapter, null)
            }
        }
        loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }
    activeWebView = webView  // strong reference to prevent GC during callbacks
}
```

**NewsletterScreen Activity acquisition:**
```kotlin
val activity = LocalContext.current as? Activity
// call site:
activity?.let { viewModel.printNewsletter(it, item) }
```

**NewsletterRepository.markNewsletterPrinted:**
```kotlin
suspend fun markNewsletterPrinted(id: String) {
    updateNewsletterStatus(id, "printed")
}
```

## Grep Results

| Check | Result |
|---|---|
| `startSystemPrint` exists | PrintService.kt:21, NewsletterViewModel.kt:87 |
| `HttpURLConnection\|buildIppPrintRequest\|writeIppAttribute` | **0 matches** |
| `PdfService.kt` deleted | Confirmed (only PrintService.kt, NotificationHelper.kt, etc. remain in service/) |
| `markNewsletterPrinted` in Repository | NewsletterRepository.kt:306 |
| `printNewsletter` in Repository | **0 matches** |
| `printService\|PrintService` in Repository | **0 matches** |
| `fun printNewsletter(activity: Activity, ...)` in ViewModel | NewsletterViewModel.kt:82 |
| `LocalContext\|as? Activity` in Screen | NewsletterScreen.kt:18,28 |
| `PdfService` anywhere | **0 matches** |
| `application/ipp\|/ipp/print\|writeIppAttribute` | **0 matches** |

## Build Result

SKIPPED_ENVIRONMENT_NOT_AVAILABLE — no Android build environment in CI context. User should build in Android Studio.

## 사용자 다음 동작

Android Studio에서 Sync + Run 후, 뉴스레터 카드 "프린트" 버튼 탭 → 시스템 인쇄 다이얼로그 → 프린터 선택(첫 회) → "인쇄" 탭 → 종이 출력 확인.
