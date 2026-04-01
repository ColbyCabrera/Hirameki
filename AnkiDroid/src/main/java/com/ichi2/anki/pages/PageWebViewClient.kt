/*
 *  Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.pages

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.isVisible
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.OnPageFinishedCallback
import com.ichi2.utils.AssetHelper.guessMimeType
import com.ichi2.utils.toRGBHex
import org.json.JSONObject
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * Base WebViewClient to be used on [PageFragment]
 */
open class PageWebViewClient : WebViewClient() {
    val onPageFinishedCallbacks: MutableList<OnPageFinishedCallback> = mutableListOf()
    val onErrorCallbacks: MutableList<OnErrorCallback> = mutableListOf()
    private val pendingStyledCallbacks = mutableListOf<PendingStyledCallback>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentNavigationId = 0
    private var styledNavigationId = -1
    private var startedThemeInjectionNavigationId = -1
    private var shownNavigationId = -1
    private var isReleased = false
    private var pendingVisualStateCallback: PendingVisualStateCallback? = null
    private var cachedMaterial3Colors: Material3Colors? = null
    private var cachedMaterial3ThemeCss: String? = null
    private var cachedDeckOptionsCss: String? = null

    private fun loadDeckOptionsCss(webView: WebView): String = try {
        webView.context.assets.open(DECK_OPTIONS_CSS_ASSET).bufferedReader().use { it.readText() }
    } catch (e: IOException) {
        Timber.w(e, "Unable to load CSS asset %s", DECK_OPTIONS_CSS_ASSET)
        ""
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        val path = request.url.path
        if (request.method != "GET" || path == null) return null
        if (path == "/favicon.png") {
            return WebResourceResponse("image/x-icon", null, ByteArrayInputStream(byteArrayOf()))
        }

        val assetPath = if (path.startsWith("/_app/")) {
            "backend/sveltekit/app/${path.substring(6)}"
        } else if (isSvelteKitPage(path.substring(1))) {
            "backend/sveltekit/index.html"
        } else {
            return null
        }

        try {
            val mimeType = guessMimeType(assetPath)
            val inputStream = view.context.assets.open(assetPath)
            val response = WebResourceResponse(mimeType, null, inputStream)
            if ("immutable" in path) {
                response.responseHeaders = mapOf("Cache-Control" to "max-age=31536000")
            }
            return response
        } catch (_: IOException) {
            Timber.w("Not found %s", assetPath)
        }
        return null
    }

    override fun onPageStarted(
        view: WebView?,
        url: String?,
        favicon: Bitmap?,
    ) {
        super.onPageStarted(view, url, favicon)
        view?.isVisible = false
        cancelPendingVisualStateCallback()
        currentNavigationId += 1
        styledNavigationId = -1
        startedThemeInjectionNavigationId = -1
        shownNavigationId = -1
        pendingStyledCallbacks.clear()
    }

    private fun buildMaterial3ThemeCss(webView: WebView): String {
        val colors = Material3Colors.from(webView)
        cachedMaterial3ThemeCss?.takeIf { colors == cachedMaterial3Colors }?.let { return it }

        val deckOptionsCss = cachedDeckOptionsCss ?: loadDeckOptionsCss(webView).also { cachedDeckOptionsCss = it }

        val css = with(colors) {
            """
            /* Override ALL Anki + Bootstrap CSS variables with Material 3 */
            :root, :root.night-mode {
                /* Foreground */
                --fg: $textColor;
                --fg-subtle: $onSurfaceVariantColor;
                --fg-disabled: $outlineColor;
                --fg-faint: $outlineColor;
                --fg-link: $primaryColor;
                /* Canvas / Background */
                --canvas: $bgColor;
                --canvas-elevated: $surfaceColor;
                --canvas-inset: $surfaceContainerColor;
                --canvas-overlay: $surfaceContainerHighColor;
                --canvas-code: $surfaceContainerColor;
                /* Borders */
                --border: $outlineColor;
                --border-subtle: $surfaceContainerHighColor;
                --border-strong: $outlineColor;
                --border-focus: $primaryColor;
                /* Buttons */
                --button-bg: $surfaceContainerColor;
                --button-gradient-start: $surfaceContainerColor;
                --button-gradient-end: $surfaceContainerColor;
                --button-hover-border: $outlineColor;
                --button-disabled: $surfaceContainerColor;
                --button-primary-bg: $primaryColor;
                --button-primary-gradient-start: $primaryColor;
                --button-primary-gradient-end: $primaryColor;
                --button-primary-disabled: $primaryColor;
                /* Shadows */
                --shadow: transparent;
                --shadow-inset: transparent;
                --shadow-subtle: transparent;
                --shadow-focus: $primaryColor;
                /* Accents */
                --accent-card: $primaryColor;
                --accent-note: $secondaryColor;
                --accent-danger: $errorContainerColor;
                /* Bootstrap body / text */
                --bs-body-bg: $bgColor;
                --bs-body-color: $textColor;
                --bs-emphasis-color: $textColor;
                --bs-secondary-color: $onSurfaceVariantColor;
                --bs-tertiary-color: $outlineColor;
                --bs-secondary-bg: $surfaceContainerColor;
                --bs-tertiary-bg: $surfaceContainerColor;
                /* Bootstrap brand */
                --bs-primary: $primaryColor;
                --bs-secondary: $secondaryColor;
                --bs-link-color: $primaryColor;
                --bs-link-hover-color: $primaryColor;
                /* Bootstrap borders */
                --bs-border-color: $outlineColor;
                --bs-border-color-translucent: $outlineColor;
                /* Deck options */
                --deck-options-bg: $bgColor;
                --deck-options-text: $textColor;
                --deck-options-surface: $surfaceColor;
                --deck-options-on-surface: $onSurfaceColor;
                --deck-options-surface-container: $surfaceContainerColor;
                --deck-options-outline: $outlineColor;
                --deck-options-primary: $primaryColor;
                --deck-options-on-primary: $onPrimaryColor;
                --deck-options-tertiary-container: $tertiaryContainerColor;
                --deck-options-on-tertiary-container: $onTertiaryContainerColor;
                --deck-options-on-surface-variant: $onSurfaceVariantColor;
            }

            body {
                background-color: $bgColor !important;
                color: $textColor !important;
            }

            a, a:link, a:visited {
                color: $primaryColor !important;
            }

            /* Bootstrap component-scoped variable overrides */
            .deck-options-page .form-control, .deck-options-page .form-select {
                background-color: $surfaceContainerColor !important;
                color: $onSurfaceColor !important;
                border-color: $outlineColor !important;
            }

            .deck-options-page .form-control:focus, .deck-options-page .form-select:focus {
                border-color: $primaryColor !important;
                box-shadow: 0 0 0 1px $primaryColor !important;
            }

            .deck-options-page .modal-content {
                --bs-modal-bg: $surfaceColor;
                --bs-modal-color: $onSurfaceColor;
                --bs-modal-border-color: $outlineColor;
                background-color: $surfaceColor !important;
            }

            /* Range box header - improved layout */
            .range-box {
                background: $surfaceColor !important;
                border-color: $outlineColor !important;
                display: flex !important;
                flex-wrap: wrap !important;
                align-items: center !important;
                justify-content: center !important;
                gap: 4px !important;
                padding: 0px 16px 4px !important;
            }

            /* InputBox containers inside range-box */
            .range-box > div {
                display: flex !important;
                align-items: center !important;
                justify-content: center !important;
                gap: 4px !important;
                background-color: $surfaceContainerColor !important;
                border-radius: 24px !important;
                padding: 4px 4px !important;
                max-width: 100% !important;
            }

            /* First InputBox - make search field go to new line */
            .range-box > div:first-of-type {
                flex-wrap: wrap !important;
            }

            /* Search field should take full width when wrapped */
            .range-box > div:first-of-type input[type="text"] {
                flex: 1 1 100% !important;
                margin-top: 8px !important;
            }

            /* Labels inside range-box - chip style */
            .range-box label {
                display: inline-flex !important;
                align-items: center !important;
                gap: 6px !important;
                padding: 8px 12px !important;
                border-radius: 20px !important;
                cursor: pointer !important;
                transition: background-color 0.2s !important;
            }

            .range-box label:hover {
                background-color: $outlineColor !important;
            }

            /* Selected radio label highlight */
            .range-box input[type="radio"]:checked + label,
            .range-box label:has(input[type="radio"]:checked) {
                background-color: $tertiaryContainerColor !important;
                color: $onTertiaryContainerColor !important;
            }

            /* Search input styling */
            .range-box input[type="text"],
            #statisticsSearchText {
                background-color: $surfaceContainerColor !important;
                color: $onSurfaceColor !important;
                border: 1px solid $outlineColor !important;
                border-radius: 20px !important;
                padding: 8px 16px !important;
                min-width: 150px !important;
            }

            /* Hide loading spinner styling */
            .range-box .spin {
                color: $primaryColor !important;
            }

            /* Radio buttons - accent color only */
            .deck-options-page input[type="radio"] {
                accent-color: $primaryColor !important;
            }

            /* Checkboxes - accent color only */
            .deck-options-page input[type="checkbox"] {
                accent-color: $primaryColor !important;
            }

            .deck-options-page input[type="range"] {
                accent-color: $primaryColor !important;
            }

            /* Text inputs - colors only */
            .deck-options-page input[type="text"], .deck-options-page input[type="search"], .deck-options-page select, .deck-options-page textarea {
                background-color: $surfaceContainerColor !important;
                color: $onSurfaceColor !important;
                border-color: $outlineColor !important;
            }

            /* Labels - color only */
            .deck-options-page label {
                color: $textColor !important;
            }

            $deckOptionsCss

            .graphs-container {
                background-color: $bgColor !important;
            }

            /* Graph cards - Material 3 card styling */
            .graphs-container > * {
                background-color: $surfaceColor !important;
                color: $onSurfaceColor !important;
                border: 1px solid $surfaceContainerColor !important;
                border-radius: 16px !important;
                padding: 16px !important;
                box-shadow: none !important;
            }

            /* Card headings */
            .graphs-container h2,
            .graphs-container h3,
            .graphs-container .title {
                color: $textColor !important;
            }

            /* Graph/chart SVG elements */
            svg text, .axis text {
                fill: $textColor !important;
            }

            svg .axis path, svg .axis line {
                stroke: $outlineColor !important;
            }
        """.trimIndent()
        }

        cachedMaterial3Colors = colors
        cachedMaterial3ThemeCss = css
        return css
    }

    private fun applyMaterial3Theme(
        webView: WebView,
        onApplied: () -> Unit,
    ) {
        val css = buildMaterial3ThemeCss(webView)
        val visualStateRequestId = currentNavigationId.toLong()

        // onPageFinished guarantees that the document is ready to receive the style update.
        webView.evaluateJavascript(
            """
            (function() {
                var css = ${JSONObject.quote(css)};
                var existingStyle = document.getElementById('material3-theme');
                if (existingStyle) {
                    existingStyle.textContent = css;
                } else {
                    var style = document.createElement('style');
                    style.id = 'material3-theme';
                    style.textContent = css;
                    (document.head || document.documentElement).appendChild(style);
                }
                console.log('Material 3 theming applied');
                return true;
            })();
            """.trimIndent(),
        ) {
            if (isReleased) {
                return@evaluateJavascript
            }

            scheduleVisualStateCallbackTimeout(webView, visualStateRequestId, onApplied)
        }
    }

    private fun scheduleVisualStateCallbackTimeout(
        webView: WebView,
        requestId: Long,
        onApplied: () -> Unit,
    ) {
        cancelPendingVisualStateCallback()

        val timeoutRunnable = Runnable {
            completeVisualStateCallback(requestId, onApplied)
        }
        pendingVisualStateCallback = PendingVisualStateCallback(requestId, timeoutRunnable)
        mainHandler.postDelayed(timeoutRunnable, VISUAL_STATE_CALLBACK_TIMEOUT_MS)

        try {
            webView.postVisualStateCallback(
                requestId,
                object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) {
                        completeVisualStateCallback(requestId, onApplied)
                    }
                },
            )
        } catch (e: RuntimeException) {
            Timber.w(e, "Failed to register visual state callback for request %d", requestId)
            completeVisualStateCallback(requestId, onApplied)
        }
    }

    private fun completeVisualStateCallback(
        requestId: Long,
        onApplied: () -> Unit,
    ) {
        val pendingCallback = pendingVisualStateCallback ?: return
        if (pendingCallback.requestId != requestId) {
            return
        }

        mainHandler.removeCallbacks(pendingCallback.timeoutRunnable)
        pendingVisualStateCallback = null
        if (isReleased) {
            return
        }

        onApplied()
    }

    private fun cancelPendingVisualStateCallback() {
        pendingVisualStateCallback?.let { pendingCallback ->
            mainHandler.removeCallbacks(pendingCallback.timeoutRunnable)
        }
        pendingVisualStateCallback = null
    }

    fun release() {
        isReleased = true
        cancelPendingVisualStateCallback()
        pendingStyledCallbacks.clear()
        onPageFinishedCallbacks.clear()
        onErrorCallbacks.clear()
    }

    /**
     * Runs [action] only if page styling completes for the current navigation.
     *
     * If [styledNavigationId] already matches [currentNavigationId], [action] executes
     * immediately. Otherwise, the callback is queued and will run when styling completes for the
     * current navigation.
     *
     * Queued callbacks are discarded if navigation changes before styling completes, because
     * [onPageStarted] clears [pendingStyledCallbacks]. Callers must tolerate dropped callbacks or
     * re-register after navigation changes.
     */
    fun runWhenPageStyled(
        webView: WebView,
        action: (WebView) -> Unit,
    ) {
        val navigationId = currentNavigationId
        if (styledNavigationId == navigationId) {
            action(webView)
            return
        }
        pendingStyledCallbacks.add(PendingStyledCallback(navigationId, action))
    }

    private fun completeThemeApplication(
        webView: WebView,
        navigationId: Int,
    ) {
        if (navigationId != currentNavigationId) {
            return
        }

        styledNavigationId = navigationId

        val readyCallbacks = pendingStyledCallbacks.toList()
        pendingStyledCallbacks.clear()
        readyCallbacks.forEach { it.action(webView) }

        if (shownNavigationId != navigationId) {
            shownNavigationId = navigationId
            onShowWebView(webView)
        }
    }

    private data class PendingStyledCallback(
        val navigationId: Int,
        val action: (WebView) -> Unit,
    )

    private data class Material3Colors(
        val bgColor: String,
        val textColor: String,
        val primaryColor: String,
        val onPrimaryColor: String,
        val surfaceColor: String,
        val onSurfaceColor: String,
        val surfaceContainerColor: String,
        val outlineColor: String,
        val secondaryColor: String,
        val tertiaryContainerColor: String,
        val onTertiaryContainerColor: String,
        val onSurfaceVariantColor: String,
        val surfaceContainerHighColor: String,
        val errorContainerColor: String,
    ) {
        companion object {
            fun from(webView: WebView): Material3Colors = Material3Colors(
                bgColor = colorHex(webView, android.R.attr.colorBackground),
                textColor = colorHex(webView, com.google.android.material.R.attr.colorOnBackground),
                primaryColor = colorHex(webView, androidx.appcompat.R.attr.colorPrimary),
                onPrimaryColor = colorHex(
                    webView, com.google.android.material.R.attr.colorOnPrimary
                ),
                surfaceColor = colorHex(webView, com.google.android.material.R.attr.colorSurface),
                onSurfaceColor = colorHex(
                    webView, com.google.android.material.R.attr.colorOnSurface
                ),
                surfaceContainerColor = colorHex(
                    webView, com.google.android.material.R.attr.colorSurfaceContainer
                ),
                outlineColor = colorHex(webView, com.google.android.material.R.attr.colorOutline),
                secondaryColor = colorHex(
                    webView, com.google.android.material.R.attr.colorSecondary
                ),
                tertiaryContainerColor = colorHex(
                    webView,
                    com.google.android.material.R.attr.colorTertiaryContainer,
                ),
                onTertiaryContainerColor = colorHex(
                    webView,
                    com.google.android.material.R.attr.colorOnTertiaryContainer,
                ),
                onSurfaceVariantColor = colorHex(
                    webView,
                    com.google.android.material.R.attr.colorOnSurfaceVariant,
                ),
                surfaceContainerHighColor = colorHex(
                    webView,
                    com.google.android.material.R.attr.colorSurfaceContainerHigh,
                ),
                errorContainerColor = colorHex(
                    webView,
                    com.google.android.material.R.attr.colorErrorContainer,
                ),
            )

            private fun colorHex(
                webView: WebView,
                colorAttribute: Int,
            ): String = MaterialColors.getColor(webView, colorAttribute).toRGBHex()
        }
    }

    private data class PendingVisualStateCallback(
        val requestId: Long,
        val timeoutRunnable: Runnable,
    )

    private fun ensureThemeApplied(webView: WebView) {
        val navigationId = currentNavigationId
        if (styledNavigationId == navigationId) {
            completeThemeApplication(webView, navigationId)
            return
        }
        if (startedThemeInjectionNavigationId == navigationId) {
            return
        }

        startedThemeInjectionNavigationId = navigationId
        applyMaterial3Theme(webView) {
            completeThemeApplication(webView, navigationId)
        }
    }

    /**
     * Shows the WebView after the page is loaded
     *
     * This may be overridden if additional 'screen ready' logic is provided by the backend
     * @see DeckOptions
     */
    open fun onShowWebView(webView: WebView) {
        Timber.v("Displaying WebView")
        webView.isVisible = true
    }

    override fun onPageFinished(
        view: WebView?,
        url: String?,
    ) {
        super.onPageFinished(view, url)
        if (view == null) return
        onPageFinishedCallbacks.toList().forEach { callback ->
            try {
                callback.onPageFinished(view)
            } catch (e: Exception) {
                Timber.e(e, "onPageFinishedCallback threw an exception")
            }
        }
        /** [PageFragment.webView] is invisible by default to avoid flashes while
         * the page is loaded, and can be made visible again after it finishes loading */
        ensureThemeApplied(view)
    }

    override fun onReceivedError(
        view: WebView, request: WebResourceRequest, error: WebResourceError
    ) {
        super.onReceivedError(view, request, error)
        if (request.isForMainFrame) {
            onErrorCallbacks.toList().forEach {
                try {
                    it.onError(error)
                } catch (e: Exception) {
                    Timber.e(e, "onErrorCallback threw an exception")
                }
            }
        }
    }

    companion object {
        private const val DECK_OPTIONS_CSS_ASSET = "anki_deck_options.css"
        private const val VISUAL_STATE_CALLBACK_TIMEOUT_MS = 300L
    }
}

fun isSvelteKitPage(path: String): Boolean {
    val pageName = path.substringBefore("/")
    return when (pageName) {
        "graphs",
        "congrats",
        "card-info",
        "change-notetype",
        "deck-options",
        "import-anki-package",
        "import-csv",
        "import-page",
        "image-occlusion",
            -> true

        else -> false
    }
}
