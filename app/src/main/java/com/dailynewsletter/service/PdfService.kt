package com.dailynewsletter.service

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.print.PrintAttributes
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class PdfService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun htmlToPdf(html: String, fileName: String): File = withContext(Dispatchers.Main) {
        val file = File(context.cacheDir, "$fileName.pdf")

        suspendCancellableCoroutine { continuation ->
            val webView = WebView(context).apply {
                settings.javaScriptEnabled = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
            }

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    val printAdapter = webView.createPrintDocumentAdapter(fileName)
                    val attrs = PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                        .build()

                    // Use PdfDocument for direct generation
                    val document = PdfDocument()
                    val pageInfo = PdfDocument.PageInfo.Builder(
                        595, // A4 width in points (72 dpi)
                        842, // A4 height in points
                        1
                    ).create()

                    val page = document.startPage(pageInfo)
                    webView.draw(page.canvas)
                    document.finishPage(page)

                    FileOutputStream(file).use { out ->
                        document.writeTo(out)
                    }
                    document.close()

                    webView.destroy()
                    continuation.resume(file)
                }
            }

            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        }
    }
}
