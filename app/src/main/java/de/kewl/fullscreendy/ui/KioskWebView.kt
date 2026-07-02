package de.kewl.fullscreendy.ui

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/** Hält eine Referenz auf die aktive WebView, damit Befehle sie steuern können. */
class WebController {
    var webView: WebView? = null
        internal set

    fun load(url: String) {
        if (url.isNotBlank()) webView?.post { webView?.loadUrl(url) }
    }

    fun reload() {
        webView?.post { webView?.reload() }
    }

    fun clearCache() {
        webView?.post { webView?.clearCache(true) }
    }
}

@Composable
fun rememberWebController(): WebController = remember { WebController() }

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun KioskWebView(
    url: String,
    controller: WebController,
    ignoreSystemFontScale: Boolean,
    zoomEnabled: Boolean,
    pullToRefresh: Boolean,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            // fontScale = 1, damit die System-Schriftvergrößerung die Dashboard-Schrift
            // NICHT beeinflusst.
            val webCtx = if (ignoreSystemFontScale) {
                val cfg = Configuration(ctx.resources.configuration).apply { fontScale = 1.0f }
                ctx.createConfigurationContext(cfg)
            } else {
                ctx
            }

            val web = WebView(webCtx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView, request: WebResourceRequest
                    ): Boolean = false

                    override fun onPageFinished(view: WebView, url: String) {
                        (view.parent as? SwipeRefreshLayout)?.isRefreshing = false
                    }
                }
                webChromeClient = WebChromeClient()
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    setSupportZoom(zoomEnabled)
                    builtInZoomControls = zoomEnabled
                    displayZoomControls = false
                    textZoom = 100 // fixe Textgröße, unabhängig vom System
                    cacheMode = WebSettings.LOAD_DEFAULT
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                controller.webView = this
            }

            SwipeRefreshLayout(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                addView(web)
                isEnabled = pullToRefresh
                setOnRefreshListener { web.reload() }
            }
        },
        update = { refresh ->
            refresh.isEnabled = pullToRefresh
            val web = refresh.getChildAt(0) as WebView
            // Lädt beim ersten Aufbau und wann immer sich die URL ändert.
            if (web.tag != url) {
                web.tag = url
                web.loadUrl(url)
            }
        }
    )
}
