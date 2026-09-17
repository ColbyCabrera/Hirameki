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
import android.os.SystemClock
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.CollectionManager.TR
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

    /**
     * rebuilds the page in a fresh webview after its renderer died, see [onRenderProcessGone]. set by the host
     * that owns the webview, as only the host can replace it. `canRebuild` is false when the same page died
     * again within [REBUILD_LOOP_WINDOW_MS] of its last rebuild: the host then shows an error instead
     */
    var onRendererGone: ((canRebuild: Boolean) -> Unit)? = null
    private val pendingStyledCallbacks = mutableListOf<PendingStyledCallback>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentNavigationId = 0
    private var styledNavigationId = -1
    private var startedThemeInjectionNavigationId = -1
    private var shownNavigationId = -1
    private var isReleased = false
    private var isRendererGone = false
    private var pendingVisualStateCallback: PendingVisualStateCallback? = null
    private var cachedMaterial3Colors: Material3Colors? = null
    private var cachedMaterial3ThemeCss: String? = null
    private var cachedMaterial3ThemeAssetCss: String? = null
    private val cachedMaterial3Scripts = mutableMapOf<String, String>()

    private fun loadAsset(
        webView: WebView,
        assetPath: String,
    ): String = try {
        webView.context.assets.open(assetPath).bufferedReader().use { it.readText() }
    } catch (e: IOException) {
        Timber.w(e, "Unable to load asset %s", assetPath)
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

        val assetCss = cachedMaterial3ThemeAssetCss
            ?: loadAsset(webView, MATERIAL3_THEME_CSS_ASSET).also { cachedMaterial3ThemeAssetCss = it }

        val css = with(colors) {
            """
            /* Override ALL Anki + Bootstrap CSS variables with Material 3 */
            :root, :root.night-mode {
                /* Foreground */
                --fg: $textColor !important;
                --fg-subtle: $onSurfaceVariantColor !important;
                --fg-disabled: $outlineColor !important;
                --fg-faint: $outlineColor !important;
                --fg-link: $primaryColor !important;
                /* Canvas / Background */
                --canvas: $bgColor !important;
                --canvas-elevated: $surfaceColor !important;
                --canvas-inset: $surfaceContainerColor !important;
                --canvas-overlay: $surfaceContainerHighColor !important;
                --canvas-code: $surfaceContainerColor !important;
                /* Borders */
                --border: $outlineColor !important;
                --border-subtle: $surfaceContainerHighColor !important;
                --border-strong: $outlineColor !important;
                --border-focus: $primaryColor !important;
                /* Buttons */
                --button-bg: $surfaceContainerColor !important;
                --button-gradient-start: $surfaceContainerColor !important;
                --button-gradient-end: $surfaceContainerColor !important;
                --button-hover-border: $outlineColor !important;
                --button-disabled: $surfaceContainerColor !important;
                --button-primary-bg: $primaryColor !important;
                --button-primary-gradient-start: $primaryColor !important;
                --button-primary-gradient-end: $primaryColor !important;
                --button-primary-disabled: $primaryColor !important;
                /* Shadows */
                --shadow: transparent !important;
                --shadow-inset: transparent !important;
                --shadow-subtle: transparent !important;
                --shadow-focus: $primaryColor !important;
                /* Accents */
                --accent-card: $primaryColor !important;
                --accent-note: $secondaryColor !important;
                --accent-danger: $errorContainerColor !important;
                /* Bootstrap body / text */
                --bs-body-bg: $bgColor !important;
                --bs-body-color: $textColor !important;
                --bs-emphasis-color: $textColor !important;
                --bs-secondary-color: $onSurfaceVariantColor !important;
                --bs-tertiary-color: $outlineColor !important;
                --bs-secondary-bg: $surfaceContainerColor !important;
                --bs-tertiary-bg: $surfaceContainerColor !important;
                /* Bootstrap brand */
                --bs-primary: $primaryColor !important;
                --bs-secondary: $secondaryColor !important;
                --bs-link-color: $primaryColor !important;
                --bs-link-hover-color: $primaryColor !important;
                /* Bootstrap borders */
                --bs-border-color: $outlineColor !important;
                --bs-border-color-translucent: $outlineColor !important;
                /* material 3 roles for the rules in anki_material3_theme.css. no !important:
                   upstream never defines these names, so there is nothing to override */
                --m3-surface: $surfaceColor;
                --m3-surface-container: $surfaceContainerColor;
                --m3-surface-container-high: $surfaceContainerHighColor;
                --m3-surface-container-highest: $surfaceContainerHighestColor;
                --m3-on-surface: $onSurfaceColor;
                --m3-on-surface-variant: $onSurfaceVariantColor;
                --m3-outline: $outlineColor;
                --m3-outline-variant: $outlineVariantColor;
                --m3-primary: $primaryColor;
                --m3-on-primary: $onPrimaryColor;
                --m3-secondary-container: $secondaryContainerColor;
                --m3-on-secondary-container: $onSecondaryContainerColor;
                --m3-tertiary-container: $tertiaryContainerColor;
                --m3-on-tertiary-container: $onTertiaryContainerColor;
                --m3-error-container: $errorContainerColor;
                --m3-on-error-container: $onErrorContainerColor;
                /* switch handles with the check / close icon of the app's AnkiToggle */
                --m3-switch-thumb-on: ${switchThumb(onPrimaryColor, onPrimaryContainerColor, CHECK_ICON_PATH)};
                --m3-switch-thumb-off: ${switchThumb(outlineColor, surfaceContainerHighestColor, CLOSE_ICON_PATH)};
            }

            /* Style Bootstrap switch handle */
            .form-switch .form-check-input:checked {
                background-image: url("data:image/svg+xml,%3csvg xmlns='http://www.w3.org/2000/svg' viewBox='-4 -4 8 8'%3e%3ccircle r='3' fill='${onPrimaryColor.replace("#", "%23")}'/%3e%3c/svg%3e") !important;
            }

            $assetCss
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
        if (isReleased) {
            return
        }
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
        if (isReleased) {
            return
        }

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
        val surfaceContainerHighestColor: String,
        val outlineVariantColor: String,
        val onPrimaryContainerColor: String,
        val secondaryContainerColor: String,
        val onSecondaryContainerColor: String,
        val onErrorContainerColor: String,
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
                surfaceContainerHighestColor = colorHex(
                    webView,
                    com.google.android.material.R.attr.colorSurfaceContainerHighest,
                ),
                outlineVariantColor = colorHex(
                    webView,
                    com.google.android.material.R.attr.colorOutlineVariant,
                ),
                onPrimaryContainerColor = colorHex(
                    webView,
                    com.google.android.material.R.attr.colorOnPrimaryContainer,
                ),
                secondaryContainerColor = colorHex(
                    webView,
                    com.google.android.material.R.attr.colorSecondaryContainer,
                ),
                onSecondaryContainerColor = colorHex(
                    webView,
                    com.google.android.material.R.attr.colorOnSecondaryContainer,
                ),
                onErrorContainerColor = colorHex(
                    webView,
                    com.google.android.material.R.attr.colorOnErrorContainer,
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
        applyMaterial3Scripts(webView)
    }

    /**
     * Runs what [MATERIAL3_THEME_CSS_ASSET] cannot do alone. Every script scopes itself to the deck
     * options page, the only page the stylesheet's rules for them cover, and is a no-op on the others:
     * - [MATERIAL3_MOTION_JS_ASSET] gives the page's popups their exit motion: their nodes leave the
     *   document in the frame the close starts, so it puts each back for one exit animation
     * - [MATERIAL3_REVERT_JS_ASSET] makes a tap on a setting's revert badge restore its default, which
     *   upstream asks for a second tap in a one-item menu for
     * - [MATERIAL3_STEPPERS_JS_ASSET] adds - and + buttons to the number fields, whose own step
     *   buttons anki renders only when the user agent is not android
     */
    private fun applyMaterial3Scripts(webView: WebView) {
        evaluateScriptAsset(webView, MATERIAL3_MOTION_JS_ASSET)
        // the same translated words the menu item the script presses carries, which is the only name the badge has
        // ever had: it is a button holding one icon, with no text, title or aria-label of its own
        val revertLabel = JSONObject.quote(TR.deckConfigRevertButtonTooltip())
        evaluateScriptAsset(webView, MATERIAL3_REVERT_JS_ASSET, prelude = "window.ankiMaterial3RevertLabel = $revertLabel;\n")
        // the steppers' spoken labels in anki's own translations: the page's strings live inside its bundle, where
        // no injected script can reach them
        val stepperLabels =
            JSONObject()
                .put("decrement", TR.actionsDecrementValue())
                .put("increment", TR.actionsIncrementValue())
        evaluateScriptAsset(webView, MATERIAL3_STEPPERS_JS_ASSET, prelude = "window.ankiMaterial3StepperLabels = $stepperLabels;\n")
    }

    private fun evaluateScriptAsset(
        webView: WebView,
        assetPath: String,
        prelude: String = "",
    ) {
        val js = cachedMaterial3Scripts.getOrPut(assetPath) { loadAsset(webView, assetPath) }
        // the asset failed to load, which loadAsset reported; the page goes without what the script adds
        if (js.isEmpty()) return
        webView.evaluateJavascript(prelude + js) {}
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

    /**
     * #143: chromium kills the whole app when any webview on a dead renderer leaves this unhandled: a SIGKILL
     * when the system reclaimed the renderer, a crash when it crashed. these pages share the renderer with the
     * reviewer's card and stay alive under it in the back stack, so the default (false) took the app down with
     * the card. a dead webview never draws again, so [onRendererGone] has the host rebuild the page
     */
    override fun onRenderProcessGone(
        view: WebView,
        detail: RenderProcessGoneDetail,
    ): Boolean {
        val pagePath = view.url?.toUri()?.path
        val crashed = detail.didCrash()
        Timber.w("page renderer gone (crashed: %b): %s", crashed, pagePath)
        // a torn-down page needs no rebuild, and a page that is already being rebuilt needs only one
        if (isReleased || isRendererGone) return true
        isRendererGone = true
        val onGone = onRendererGone
        if (onGone == null) {
            Timber.e("page renderer gone: no host rebuilds this page, it stays blank")
            return true
        }
        val lifecycleOwner = view.findViewTreeLifecycleOwner()
        if (lifecycleOwner == null) {
            Timber.w("page renderer gone: the webview is not on a screen, nothing to rebuild")
            return true
        }
        // a page that crashes every fresh renderer while loading must not be rebuilt forever. only a crash counts,
        // as in Flashcard's handler: android reclaims a backgrounded renderer whenever it needs the memory, over and
        // over and through no fault of the page, so counting a reclaim as a strike force-closed a page on its second
        // one and lost whatever was half-edited on it. tracked per page for the process, a rebuild replaces this
        // client. a renderer that dies before the page commits leaves the webview with no url; those deaths share one
        // entry (""), because a null path was never recorded and such a page was rebuilt without limit
        val rebuildKey = pagePath.orEmpty()
        val lastRebuild = lastRebuildByPath[rebuildKey]
        val canRebuild = !crashed || lastRebuild == null || SystemClock.elapsedRealtime() - lastRebuild >= REBUILD_LOOP_WINDOW_MS
        if (!canRebuild) Timber.e("page renderer gone again soon after a rebuild, not rebuilding %s", pagePath)
        // rebuild once the screen is started: a renderer started in the background tends to be reclaimed again
        // (#8459). on a lifecycle that is already started, addObserver calls onStart at once
        lifecycleOwner.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    owner.lifecycle.removeObserver(this)
                    // the host tore the page down while it waited, so there is nothing left to rebuild
                    if (isReleased) return
                    if (crashed && canRebuild) lastRebuildByPath[rebuildKey] = SystemClock.elapsedRealtime()
                    onGone(canRebuild)
                }
            },
        )
        return true
    }

    companion object {
        private const val MATERIAL3_THEME_CSS_ASSET = "anki_material3_theme.css"
        private const val MATERIAL3_MOTION_JS_ASSET = "anki_material3_motion.js"
        private const val MATERIAL3_REVERT_JS_ASSET = "anki_material3_revert.js"
        private const val MATERIAL3_STEPPERS_JS_ASSET = "anki_material3_steppers.js"
        private const val VISUAL_STATE_CALLBACK_TIMEOUT_MS = 300L

        /**
         * a page whose renderer dies again this soon after its rebuild is taken to kill every renderer.
         * long enough for a slow page load plus the crash, short enough that unrelated deaths rarely fall inside
         */
        @VisibleForTesting
        internal const val REBUILD_LOOP_WINDOW_MS = 30_000L

        /** when each page path ("" before a page has a url) was last rebuilt after a renderer crash, see [onRenderProcessGone] */
        private val lastRebuildByPath = mutableMapOf<String, Long>()

        /** the rebuild times outlive any one client, so a test starts from an empty history */
        @VisibleForTesting(otherwise = VisibleForTesting.NONE)
        fun clearRebuildHistory() = lastRebuildByPath.clear()

        /** material "check" and "close" icons, 24dp viewport */
        private const val CHECK_ICON_PATH = "M9 16.17 4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"
        private const val CLOSE_ICON_PATH =
            "M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"

        /**
         * a 28px m3 switch handle as a css `url()`: a 24px [handleColor] disc around a 16px icon.
         * the colours are baked in because an svg background image cannot read css variables.
         */
        private fun switchThumb(
            handleColor: String,
            iconColor: String,
            iconPath: String,
        ): String {
            fun encode(color: String) = color.replace("#", "%23")
            return "url(\"data:image/svg+xml,%3csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 28 28'%3e" +
                "%3ccircle cx='14' cy='14' r='12' fill='${encode(handleColor)}'/%3e" +
                "%3cpath transform='translate(6 6) scale(.6667)' fill='${encode(iconColor)}' d='$iconPath'/%3e" +
                "%3c/svg%3e\")"
        }
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
