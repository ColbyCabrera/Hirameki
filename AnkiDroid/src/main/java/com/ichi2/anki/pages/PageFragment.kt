// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.pages

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.CallSuper
import androidx.annotation.LayoutRes
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.showThemedToast
import com.ichi2.themes.Themes
import timber.log.Timber
import kotlin.reflect.KClass

/**
 * Base class for displaying Anki HTML pages
 */
open class PageFragment(
    @LayoutRes contentLayoutId: Int = R.layout.page_fragment,
) : Fragment(contentLayoutId),
    PostRequestHandler {
    lateinit var webView: WebView
    private lateinit var server: AnkiServer

    /** Kept so teardown can release it without touching a WebView destroyed by [onRendererGone]. */
    private lateinit var pageWebViewClient: PageWebViewClient

    /**
     * A loading indicator for the page. May be shown before the WebView is loaded to
     * stop flickering
     *
     * @exception IllegalStateException if accessed before [onViewCreated]
     */
    val pageLoadingIndicator: CircularProgressIndicator
        get() = requireView().findViewById(R.id.page_loading)

    /**
     * Override this to set a custom [WebViewClient] to the page.
     * This is called in [onViewCreated].
     *
     * @param savedInstanceState If non-null, this fragment is being re-constructed
     * from a previous saved state as given here.
     */
    protected open fun onCreateWebViewClient(savedInstanceState: Bundle?) = PageWebViewClient()

    protected open fun onWebViewCreated(webView: WebView) {}

    /**
     * When the webview calls `BridgeCommand("foo")`, the PageFragment execute `bridgeCommands["foo"]`.
     * By default, only bridge command is allowed, subclasses must redefine it if they expect bridge commands.
     */
    open val bridgeCommands: Map<String, () -> Unit> = mapOf()

    /**
     * Ensures that [pageWebViewClient] can receive `bridgeCommand` requests and execute the command from [bridgeCommands].
     */
    private fun setupBridgeCommand(pageWebViewClient: PageWebViewClient) {
        if (bridgeCommands.isEmpty()) {
            return
        }
        webView.addJavascriptInterface(
            object : Any() {
                @JavascriptInterface
                fun bridgeCommandImpl(request: String) {
                    bridgeCommands.getOrDefault(request) {
                        Timber.d("Unknown request received %s", request)
                    }()
                }
            },
            "bridgeCommandInterface",
        )
        pageWebViewClient.onPageFinishedCallbacks.add { webView ->
            webView.evaluateJavascript(
                "bridgeCommand = function(request){ bridgeCommandInterface.bridgeCommandImpl(request); };",
            ) {}
        }
    }

    @CallSuper
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        val pageWebViewClient = onCreateWebViewClient(savedInstanceState)
        this.pageWebViewClient = pageWebViewClient
        pageWebViewClient.onRendererGone = { crashed -> onRendererGone(crashed) }
        server = AnkiServer(this).also { it.start() }
        pageWebViewClient.serverUrl = server.baseUrl().toUri()
        webView =
            view.findViewById<WebView>(R.id.webview).apply {
                with(settings) {
                    javaScriptEnabled = true
                    displayZoomControls = false
                    builtInZoomControls = true
                    setSupportZoom(true)
                }
                webViewClient = pageWebViewClient
                webChromeClient = PageChromeClient()
            }
        setupBridgeCommand(pageWebViewClient)
        onWebViewCreated(webView)

        val arguments = requireArguments()
        val path = requireNotNull(arguments.getString(PATH_ARG_KEY)) { "'$PATH_ARG_KEY' missing" }
        val title = arguments.getString(TITLE_ARG_KEY)

        val nightMode = if (Themes.currentTheme.isNightMode) "#night" else ""
        val url = "${server.baseUrl()}$path$nightMode".toUri()
        Timber.i("Loading $url")
        webView.loadUrl(url.toString())

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = systemBars.top)
            insets
        }

        view.findViewById<MaterialToolbar>(R.id.toolbar)?.apply {
            if (title != null) {
                setTitle(title)
            }
            setNavigationOnClickListener {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    override suspend fun handlePostRequest(
        uri: String,
        bytes: ByteArray,
    ): ByteArray {
        val methodName =
            if (uri.startsWith(AnkiServer.ANKI_PREFIX)) {
                uri.substring(AnkiServer.ANKI_PREFIX.length)
            } else {
                throw IllegalArgumentException("unhandled request: $uri")
            }
        return activity.handleUiPostRequest(methodName, bytes) ?: handleCollectionPostRequest(
            methodName,
            bytes,
        ) ?: throw IllegalArgumentException("unhandled method: $methodName")
    }

    /** See [PageWebViewClient.onRenderProcessGone]: this page's WebView is dead. */
    private fun onRendererGone(crashed: Boolean) {
        // a WebView whose renderer is gone must be removed and destroyed before anything calls it
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
        val activity = activity ?: return
        if (crashed) {
            // a rebuilt page would likely crash its renderer again: close the page instead
            val message = getString(R.string.webview_crash_nonfatal, getString(R.string.webview_crash_unknown))
            showThemedToast(activity, message, shortLength = false)
            activity.finish()
        } else {
            // reclaimed by the system: recreating the activity rebuilds this page in a new WebView
            activity.recreate()
        }
    }

    override fun onDestroyView() {
        // not webView.webViewClient: no WebView method may be called after WebView.destroy()
        if (this::pageWebViewClient.isInitialized) {
            pageWebViewClient.release()
        }
        server.stop()
        super.onDestroyView()
    }

    companion object {
        const val PATH_ARG_KEY = "path"
        const val TITLE_ARG_KEY = "title"

        fun getIntent(
            context: Context,
            path: String,
            title: String? = null,
            clazz: KClass<out PageFragment> = PageFragment::class,
        ): Intent {
            val arguments =
                Bundle().apply {
                    putString(PATH_ARG_KEY, path)
                    putString(TITLE_ARG_KEY, title)
                }
            return SingleFragmentActivity.getIntent(context, clazz, arguments).apply {
                putExtra(SingleFragmentActivity.EXTRA_APPLY_INSETS_PADDING, false)
            }
        }
    }
}
