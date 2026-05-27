package com.aggregatorx.app.engine.scraper

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebViewFetcher @Inject constructor(
    private val context: Context
) {

    private val webViewInstance = AtomicReference<WebView?>(null)

    suspend fun fetch(
        url: String,
        query: String,
        timeoutMs: Long = 18_000L
    ): String? = withContext(Dispatchers.Main) {
        var webView: WebView? = null
        return@withContext try {
            webView = WebView(context)
            
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = false
                databaseEnabled = false
                mixedContentMode = 1  // WebSettings.MIXED_CONTENT_NEVER
                cacheMode = WebSettings.LOAD_NO_CACHE
                defaultTextEncodingName = "utf-8"
            }

            val htmlRef = AtomicReference<String?>(null)
            var finished = false

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    htmlRef.set(view?.title ?: "")
                    finished = true
                }
                
                override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                    finished = true
                }
            }

            webView.loadUrl(url)
            
            val startTime = System.currentTimeMillis()
            while (!finished && System.currentTimeMillis() - startTime < timeoutMs) {
                delay(100)
            }

            val result = try {
                webView.evaluateJavascript("document.documentElement.outerHTML") { html ->
                    htmlRef.set(html?.removeSurrounding("\""))
                }
                htmlRef.get()
            } catch (_: Exception) {
                htmlRef.get()
            }

            result.takeIf { !it.isNullOrBlank() }

        } catch (e: Exception) {
            null
        } finally {
            webView?.apply {
                try {
                    stopLoading()
                    clearHistory()
                    clearCache(true)
                    settings.apply {
                        javaScriptEnabled = false
                        domStorageEnabled = false
                    }
                    removeAllViews()
                    destroy()
                } catch (_: Exception) {}
            }
            webViewInstance.set(null)
        }
    }
}
