// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

// Ported from AnkiDroid (https://github.com/ankidroid/Anki-Android), GPLv3.

package com.ichi2.anki.workarounds

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import androidx.core.view.ancestors
import androidx.fragment.app.Fragment
import androidx.fragment.app.findFragment
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.ichi2.anki.BuildConfig
import com.ichi2.anki.R
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

/**
 * Holds a [WebView] and replaces it when its renderer process is gone, which Android otherwise
 * answers by killing the app (#143). The host reconfigures the replacement in
 * [OnWebViewRecreatedListener.onWebViewRecreated].
 *
 * The [WebView] is reached only through this layout, so no caller holds a destroyed instance.
 *
 * A renderer that crashes twice within [CRASH_LOOP_WINDOW] is not given a third [WebView], since
 * the content is likely what crashes it: an error is shown instead, with a button to retry, for
 * example after the user picked other content.
 */
open class SafeWebViewLayout :
    FrameLayout,
    OnRenderProcessGoneListener {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private enum class WebViewState {
        ACTIVE,

        /** The renderer was lost while recreation was impossible: recreated on [onAttachedToWindow] */
        DESTROYED_RECOVERABLE,

        /** The renderer kept crashing: an error is shown, and the [WebView] is recreated only on retry */
        CRASH_LOOP,

        /** [destroy] was called */
        DESTROYED_TERMINAL,
    }

    /** May be destroyed: prefer [activeWebView] unless the destroyed instance is needed. */
    private var uncheckedWebView: WebView = createWebView()

    private var webViewState = WebViewState.ACTIVE

    /** [SystemClock.elapsedRealtime] of the last renderer crash or retry, used to detect a crash loop */
    private var lastCrashAt: Long? = null

    /** Shown in [WebViewState.CRASH_LOOP] */
    private var crashLoopMessage: View? = null

    /**
     * Receives [OnWebViewRecreatedListener.onWebViewRecreated] instead of the host Fragment or
     * Activity, for hosts which are neither, such as a composable.
     */
    var onWebViewRecreatedListener: OnWebViewRecreatedListener? = null

    /** The inner [WebView], or `null` while it is destroyed. Public methods should go through this. */
    private val activeWebView: WebView?
        get() {
            if (webViewState == WebViewState.ACTIVE) return uncheckedWebView
            Timber.w("WebView call ignored: WebView was destroyed")
            return null
        }

    /** The inner [WebView], or `null` while it is destroyed. */
    @get:VisibleForTesting
    val webView: WebView?
        get() = uncheckedWebView.takeIf { webViewState == WebViewState.ACTIVE }

    var scrollBars: Int
        get() = uncheckedWebView.scrollBarStyle
        set(value) {
            activeWebView?.scrollBarStyle = value
        }

    override fun setBackgroundColor(color: Int) {
        super.setBackgroundColor(color)
        activeWebView?.setBackgroundColor(color)
    }

    protected open fun createWebView() = WebView(context)

    @get:MainThread
    val hitTestResult: WebView.HitTestResult? get() = activeWebView?.hitTestResult

    @get:MainThread
    @Suppress("DEPRECATION")
    val scale: Float get() = uncheckedWebView.scale

    @MainThread
    fun zoomBy(zoomFactor: Float) {
        activeWebView?.zoomBy(zoomFactor)
    }

    init {
        addView(uncheckedWebView, webViewLayoutParams)
    }

    // Not guarded when not [WebViewState.ACTIVE]: a no-op is impossible.
    // WebSettings of a destroyed WebView are unused and safe to write.
    @get:MainThread
    val settings: WebSettings get() = uncheckedWebView.settings

    @MainThread
    fun setWebViewClient(webViewClient: SafeWebViewClient) {
        val webView = activeWebView ?: return
        webViewClient.setOnRenderProcessGoneListener(this)
        webView.webViewClient = webViewClient
    }

    @MainThread
    fun setWebChromeClient(webChromeClient: WebChromeClient) {
        activeWebView?.webChromeClient = webChromeClient
    }

    @MainThread
    fun evaluateJavascript(script: String) {
        activeWebView?.evaluateJavascript(script, null)
    }

    /**
     * Requires [WebViewFeature.WEB_MESSAGE_LISTENER]
     *
     * @see WebViewCompat.addWebMessageListener
     */
    @SuppressLint("RequiresFeature") // checked by the caller
    @MainThread
    fun addWebMessageListener(
        jsObjectName: String,
        allowedOriginRules: Set<String>,
        listener: WebViewCompat.WebMessageListener,
    ) {
        val webView = activeWebView ?: return
        WebViewCompat.addWebMessageListener(webView, jsObjectName, allowedOriginRules, listener)
    }

    /** Calls [onComplete] once everything the page has done so far is ready to be drawn. */
    @MainThread
    fun postVisualStateCallback(onComplete: () -> Unit) {
        activeWebView?.postVisualStateCallback(
            0,
            object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) = onComplete()
            },
        )
    }

    @MainThread
    fun removeJavascriptInterface(name: String) {
        activeWebView?.removeJavascriptInterface(name)
    }

    @MainThread
    fun loadUrl(url: String) {
        activeWebView?.loadUrl(url)
    }

    @MainThread
    fun loadDataWithBaseURL(
        baseUrl: String?,
        data: String,
        mimeType: String?,
        encoding: String?,
        historyUrl: String?,
    ) {
        activeWebView?.loadDataWithBaseURL(baseUrl, data, mimeType, encoding, historyUrl)
    }

    @MainThread
    fun setAcceptThirdPartyCookies(accept: Boolean) {
        val webView = activeWebView ?: return
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, accept)
    }

    @MainThread
    fun goBack() {
        activeWebView?.goBack()
    }

    @MainThread
    fun canGoBack(): Boolean = activeWebView?.canGoBack() ?: false

    @MainThread
    fun clearHistory() {
        activeWebView?.clearHistory()
    }

    @MainThread
    fun setDownloadListener(listener: DownloadListener?) {
        activeWebView?.setDownloadListener(listener)
    }

    override fun setOnScrollChangeListener(l: OnScrollChangeListener?) {
        activeWebView?.setOnScrollChangeListener(l)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun setOnTouchListener(l: OnTouchListener?) {
        activeWebView?.setOnTouchListener(l)
    }

    /** New content is not what crashed the renderer: forgets earlier crashes and leaves a crash loop. */
    @MainThread
    fun onContentChanged() {
        lastCrashAt = null
        if (webViewState == WebViewState.CRASH_LOOP) leaveCrashLoop()
    }

    /**
     * Replaces the dead inner [WebView] when recreation is possible.
     *
     * When recreation is skipped (layout not in a usable fragment/window state), the dead
     * [WebView] is destroyed and not replaced; further calls on this layout are ignored until
     * [onAttachedToWindow] recreates it from [WebViewState.DESTROYED_RECOVERABLE].
     */
    override fun onRenderProcessGone(
        webView: WebView,
        didCrash: Boolean,
    ) {
        // Every WebView sharing the renderer is told; one replaced earlier may still be told
        if (webView !== uncheckedWebView) {
            destroyWebView(webView)
            return
        }

        // Always remove and destroy the dead WebView first. Android requires this even when
        // we skip recreation (e.g. fragment view already gone). See:
        // https://developer.android.com/develop/ui/views/layout/webapps/handle-termination
        removeView(webView)
        webView.destroy()

        if (didCrash && isCrashLoop()) {
            Timber.w("WebView renderer crashed twice within %s; not recreating the WebView", CRASH_LOOP_WINDOW)
            webViewState = WebViewState.CRASH_LOOP
            showCrashLoopMessage()
            return
        }

        recreateIfPossible()
    }

    /** Recreates the [WebView], or leaves it destroyed until [onAttachedToWindow] if impossible now */
    private fun recreateIfPossible() {
        if (!isAttachedToWindow) {
            Timber.w("skipping WebView recreation; layout is not attached to a window")
            webViewState = WebViewState.DESTROYED_RECOVERABLE
            return
        }

        val listener = findListener()
        if (listener == null) {
            Timber.w("skipping WebView recreation; no OnWebViewRecreatedListener found")
            webViewState = WebViewState.DESTROYED_RECOVERABLE
            return
        }

        recreateInnerWebView(listener)
    }

    /** Records a crash, returning whether it followed the previous one within [CRASH_LOOP_WINDOW] */
    private fun isCrashLoop(): Boolean {
        val now = SystemClock.elapsedRealtime()
        val previous = lastCrashAt
        lastCrashAt = now
        return previous != null && now - previous < CRASH_LOOP_WINDOW.inWholeMilliseconds
    }

    private fun showCrashLoopMessage() {
        val message =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                val padding = resources.getDimensionPixelSize(R.dimen.side_margin)
                setPadding(padding, padding, padding, padding)
                addView(
                    TextView(context).apply {
                        setText(R.string.webview_crash_loop_dialog_title)
                        gravity = Gravity.CENTER
                    },
                )
                addView(
                    Button(context).apply {
                        setText(R.string.retry)
                        setOnClickListener { retryAfterCrashLoop() }
                    },
                )
            }
        crashLoopMessage = message
        addView(message, webViewLayoutParams)
    }

    /** Gives the content another [WebView]: a crash soon after is a crash loop again */
    private fun retryAfterCrashLoop() {
        if (webViewState != WebViewState.CRASH_LOOP) return
        Timber.i("retrying the WebView after its renderer kept crashing")
        lastCrashAt = SystemClock.elapsedRealtime()
        leaveCrashLoop()
    }

    private fun leaveCrashLoop() {
        crashLoopMessage?.let { removeView(it) }
        crashLoopMessage = null
        webViewState = WebViewState.DESTROYED_RECOVERABLE
        recreateIfPossible()
    }

    /**
     * Finds the [OnWebViewRecreatedListener]: [onWebViewRecreatedListener], else the host
     * Fragment, else the host Activity.
     */
    private fun findListener(): OnWebViewRecreatedListener? {
        val fragment = findFragmentOrNull()
        if (fragment != null && fragment.view == null) {
            Timber.w("skipping WebView recreation; fragment view is gone")
            return null
        }
        onWebViewRecreatedListener?.let { return it }
        val host: Any = fragment ?: context
        if (host is OnWebViewRecreatedListener) return host
        val message = "'${host.javaClass.simpleName}' must implement OnWebViewRecreatedListener"
        if (BuildConfig.DEBUG && !isInEditMode) throw IllegalStateException(message)
        Timber.w("%s; WebView recreation may not be handled", message)
        return null
    }

    private fun findFragmentOrNull(): Fragment? =
        try {
            findFragment<Fragment>()
        } catch (e: IllegalStateException) {
            null
        }

    private fun recreateInnerWebView(listener: OnWebViewRecreatedListener) {
        uncheckedWebView = createWebView()
        webViewState = WebViewState.ACTIVE
        addView(uncheckedWebView, webViewLayoutParams)
        listener.onWebViewRecreated()
    }

    private fun tryRecoverDestroyedWebViewIfNeeded(listener: OnWebViewRecreatedListener) {
        if (webViewState != WebViewState.DESTROYED_RECOVERABLE) return
        // For Fragment hosts, ensure the layout is still in the fragment's current view hierarchy.
        val fragment = findFragmentOrNull()
        if (fragment != null) {
            val fragmentView = fragment.view ?: return
            if (this !== fragmentView && ancestors.none { it === fragmentView }) {
                Timber.w("skipping WebView recovery; layout is not in the fragment's current view hierarchy")
                return
            }
        }
        recreateInnerWebView(listener)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // also checks, in debug builds, that the host implements OnWebViewRecreatedListener
        val listener = findListener() ?: return
        tryRecoverDestroyedWebViewIfNeeded(listener)
    }

    /**
     * Destroys the inner [WebView] with [destroyWebView]: unlike [WebView.destroy], this also
     * stops loading, clears callbacks and removes it from the layout.
     *
     * Afterwards, calls on this layout are ignored and the [WebView] is never recreated.
     */
    @MainThread
    fun destroy() {
        when (webViewState) {
            // Mark destroyed even if [destroyWebView] partially failed; using a partially torn-down
            // WebView is unsafe.
            WebViewState.ACTIVE -> destroyWebView(uncheckedWebView, this)
            // The renderer loss already destroyed the WebView; becoming terminal stops recovery on reattach.
            WebViewState.DESTROYED_RECOVERABLE, WebViewState.CRASH_LOOP -> Unit
            WebViewState.DESTROYED_TERMINAL -> {
                Timber.w("destroy called after WebView was destroyed")
                return
            }
        }
        webViewState = WebViewState.DESTROYED_TERMINAL
    }

    companion object {
        /**
         * Two renderer crashes this close together are taken as content that crashes the renderer.
         * Content typically crashes it within seconds of loading.
         */
        @VisibleForTesting
        val CRASH_LOOP_WINDOW = 30.seconds

        private val webViewLayoutParams =
            ViewGroup.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
            )

        /**
         * Clean up and destroy the [webView] to prevent memory leaks.
         *
         * Stops any loading, clears callbacks, and removes the view from its [parent].
         */
        fun destroyWebView(
            webView: WebView,
            parent: ViewGroup? = webView.parent as? ViewGroup,
        ) {
            Timber.d("Destroying WebView")

            runCatching {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.webChromeClient = null
                webView.setDownloadListener(null)
                parent?.removeView(webView)
            }.onFailure { Timber.w(it, "failed to clean up WebView") }

            // attempt to run destroy() even if the above fails
            runCatching { webView.destroy() }.onFailure { Timber.w(it, "failed to destroy WebView") }
        }
    }
}

/**
 * Called after [SafeWebViewLayout.onRenderProcessGone] replaced the [WebView] whose renderer
 * crashed or was killed by the system to free memory.
 *
 * The host of a [SafeWebViewLayout] (its Fragment, else its Activity) **must** implement this
 * interface, unless [SafeWebViewLayout.onWebViewRecreatedListener] is set. In debug builds, a
 * missing implementation throws in [SafeWebViewLayout.onAttachedToWindow].
 */
fun interface OnWebViewRecreatedListener {
    /**
     * Reconfigures the [SafeWebViewLayout] after it replaced its [WebView]. Implementations must
     * reapply, through the layout, all clients, settings and content that were configured on the
     * original [WebView]: [SafeWebViewLayout] only replaces the view.
     *
     * To trigger this on a device, call `webViewLayout.loadUrl("chrome://crash")`, or kill the
     * renderer process: find it with `adb shell ps -A | grep sandboxed_process`.
     */
    fun onWebViewRecreated()
}
