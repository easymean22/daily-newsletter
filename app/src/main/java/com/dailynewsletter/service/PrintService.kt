package com.dailynewsletter.service

import android.app.Activity
import android.content.Context
import android.print.PrintManager
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrintService @Inject constructor() {
    companion object {
        private const val TAG = "PrintService"
    }

    // Strong reference to keep WebView alive during PrintDocumentAdapter callbacks
    private var activeWebView: WebView? = null

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
        activeWebView = webView
    }
}
