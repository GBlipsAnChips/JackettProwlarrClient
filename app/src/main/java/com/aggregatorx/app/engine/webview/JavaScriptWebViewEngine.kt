package com.aggregatorx.app.engine.webview

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Cleaned WebView Engine - Zero Cache, Pure Live Streaming Pipes.
 */
class JavaScriptWebViewEngine(private val webView: WebView) {

    private var pageContent = ""
    private var readySignal = CompletableFuture<String>()

    init {
        configureWebView()
    }

    private fun configureWebView() {
        webView.apply {
            // Completely wipe past storage arrays out of memory
            clearCache(true)
            clearHistory()
            clearFormData()

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                
                // CRITICAL FIX 1: Bypass local storage and force live network fetches every time
                cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                javaScriptCanOpenWindowsAutomatically = false
                
                // CRITICAL FIX 2: Mask as a standard modern desktop browser to bypass bot gates
                userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            }

            // Flush out background cookies to prevent sticky tracking behavior
            val cookieManager = android.webkit.CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)
            cookieManager.removeAllCookies(null)
            cookieManager.flush()
        }
    }

    private fun extractPageContent(view: WebView) {
        view.evaluateJavascript(
            """
            (function() {
                return document.documentElement.outerHTML;
            })();
            """.trimIndent()
        ) { result ->
            val html = result?.trim('"')?.replace("\\\"", "\"") ?: ""
            onJSContentReady(html)
        }
    }

    suspend fun loadUrlWithJavaScript(
        url: String,
        query: String? = null,
        timeoutMs: Long = 12000
    ): String {
        return suspendCancellableCoroutine { continuation ->
            var isResumed = false

            fun safeResume(html: String) {
                if (!isResumed) {
                    isResumed = true
                    continuation.resume(html)
                }
            }

            val timeoutRunnable = Runnable {
                webView.evaluateJavascript("document.documentElement.outerHTML") { result ->
                    val html = result?.trim('"')?.replace("\\\"", "\"") ?: ""
                    safeResume(html)
                }
            }
            webView.postDelayed(timeoutRunnable, timeoutMs)

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, loadedUrl: String) {
                    // Let async content settle cleanly for 3 seconds post-initialization
                    view.postDelayed({
                        view.evaluateJavascript("document.documentElement.outerHTML") { result ->
                            val html = result?.trim('"')?.replace("\\\"", "\"") ?: ""
                            webView.removeCallbacks(timeoutRunnable)
                            safeResume(html)
                        }
                    }, 3000)
                }
            }

            webView.loadUrl(url)
        }
    }

    suspend fun injectSearchAndWait(
        searchSelector: String,
        submitSelector: String,
        query: String,
        resultSelector: String,
        timeoutMs: Long = 15000
    ): String {
        return suspendCancellableCoroutine { continuation ->
            var isResumed = false

            fun safeResume(html: String) {
                if (!isResumed) {
                    isResumed = true
                    continuation.resume(html)
                }
            }

            val js = """
            (function() {
                const searchInput = document.querySelector('$searchSelector');
                if (searchInput) {
                    searchInput.value = '$query';
                    searchInput.dispatchEvent(new Event('input', { bubbles: true }));
                    searchInput.dispatchEvent(new Event('change', { bubbles: true }));
                }
                const submitBtn = document.querySelector('$submitSelector');
                if (submitBtn) {
                    submitBtn.click();
                }
            })();
            """.trimIndent()

            webView.evaluateJavascript(js, null)

            val startTime = System.currentTimeMillis()
            var checkRunnable: Runnable? = null
            
            checkRunnable = Runnable {
                webView.evaluateJavascript(
                    "(function() { return document.querySelectorAll('$resultSelector').length > 0; })();"
                ) { result ->
                    val found = result?.toBoolean() ?: false
                    if (found || (System.currentTimeMillis() - startTime) >= (timeoutMs - 1000)) {
                        webView.evaluateJavascript("document.documentElement.outerHTML") { finalHtml ->
                            val html = finalHtml?.trim('"')?.replace("\\\"", "\"") ?: ""
                            safeResume(html)
                        }
                    } else {
                        checkRunnable?.let { webView.postDelayed(it, 500) }
                    }
                }
            }

            webView.postDelayed(checkRunnable, 1500)
        }
    }

    suspend fun extractAllLinks(selector: String = "a[href]"): List<String> {
        return suspendCancellableCoroutine { continuation ->
            webView.evaluateJavascript(
                """
                (function() {
                    const links = Array.from(document.querySelectorAll('$selector'))
                        .map(a => a.href)
                        .filter(href => href && href.startsWith('http'));
                    return JSON.stringify(links);
                })();
                """.trimIndent()
            ) { result ->
                try {
                    val json = result?.trim('"')?.replace("\\\"", "\"") ?: "[]"
                    val links = kotlinx.serialization.json.Json.decodeFromString<List<String>>(json)
                    continuation.resume(links)
                } catch (e: Exception) {
                    continuation.resume(emptyList())
                }
            }
        }
    }

    suspend fun scrollToBottom(scrollCount: Int = 3) {
        repeat(scrollCount) {
            suspendCancellableCoroutine<Unit> { continuation ->
                // CRITICAL FIX 3: Removed immediate script double-resuming crash pattern
                webView.evaluateJavascript("window.scrollBy(0, window.innerHeight); true;", null)
                
                webView.postDelayed({
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }, 1500)
            }
        }
    }

    private fun onJSContentReady(html: String) {
        pageContent = html
        if (!readySignal.isDone) {
            readySignal.complete(html)
        }
    }

    fun reset() {
        pageContent = ""
        readySignal = CompletableFuture<String>()
    }

    fun destroy() {
        webView.stopLoading()
        webView.destroy()
    }
}
