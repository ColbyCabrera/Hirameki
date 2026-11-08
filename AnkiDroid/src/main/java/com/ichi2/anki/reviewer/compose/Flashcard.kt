/*
 * Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com> 2026 Colby Cabrera <colbycabrera.wd@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.reviewer.compose

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ichi2.anki.R
import com.ichi2.anki.ViewerResourceHandler
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.previewer.stdHtml
import com.ichi2.anki.reviewer.ReviewerJavascriptCommand
import com.ichi2.anki.settings.Prefs
import com.ichi2.themes.Themes
import com.ichi2.utils.toRGBHex
import kotlinx.serialization.json.Json
import timber.log.Timber
import kotlin.math.abs

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun Flashcard(
    baseUrl: String,
    questionHtml: String,
    answerHtml: String,
    bodyClass: String,
    isMediaAutoplayEnabled: Boolean,
    javascriptCommand: ReviewerJavascriptCommand?,
    onJavascriptCommandConsumed: (Int) -> Unit,
    /** A tap, double tap or swipe on the card, for the user's gesture settings. */
    onGesture: (CardTouchGesture) -> Unit,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    isAnswerShown: Boolean,
    toolbarHeight: Int = 0,
    /** Changes each time a card is shown, even when the next card's HTML is identical. */
    cardDisplayIndex: Long = 0L,
) {
    val currentBaseUrl by rememberUpdatedState(baseUrl)
    val currentOnJavascriptCommandConsumed by rememberUpdatedState(onJavascriptCommandConsumed)
    val currentOnLinkClick by rememberUpdatedState(onLinkClick)
    val currentOnGesture by rememberUpdatedState(onGesture)

    val context = LocalContext.current
    val sharedPrefs = remember(context) { context.sharedPrefs() }
    val prefKey = stringResource(R.string.apply_hirameki_css_preference)
    var applyHiramekiCssMode by remember {
        mutableStateOf(
            sharedPrefs.getString(prefKey, Prefs.HIRAMEKI_CSS_ALL) ?: Prefs.HIRAMEKI_CSS_ALL,
        )
    }

    val listener =
        remember(sharedPrefs, prefKey) {
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == prefKey) {
                    applyHiramekiCssMode =
                        sharedPrefs.getString(prefKey, Prefs.HIRAMEKI_CSS_ALL) ?: Prefs.HIRAMEKI_CSS_ALL
                }
            }
        }

    DisposableEffect(sharedPrefs, listener) {
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val isNightMode = Themes.currentTheme.isNightMode
    val surfaceColor = MaterialTheme.colorScheme.surface
    val surfaceColorHex = surfaceColor.toArgb().toRGBHex()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceColorHex = onSurfaceColor.toArgb().toRGBHex()
    val surfaceContainerColor = MaterialTheme.colorScheme.surfaceContainer
    val surfaceContainerColorHex = surfaceContainerColor.toArgb().toRGBHex()
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryColorHex = primaryColor.toArgb().toRGBHex()
    val outlineColor = MaterialTheme.colorScheme.outline
    val outlineColorHex = outlineColor.toArgb().toRGBHex()
    val typography = MaterialTheme.typography
    val displayLargeStyle = typography.displayMedium
    val bodyLargeStyle = typography.titleLarge

    val contentKey =
        remember(questionHtml, answerHtml) {
            FlashcardContentKey(questionHtml.hashCode(), answerHtml.hashCode())
        }

    // #143: Android reclaims the renderer process of a backgrounded app's WebViews. A WebView whose
    // renderer is gone never draws again, and an unhandled loss kills the whole app, which then
    // reopened on the deck list instead of the reviewer. Bumping the generation replaces the dead
    // WebView with a new one that loads the same card.
    var rendererGeneration by remember { mutableIntStateOf(0) }

    // A renderer that crashed (rather than being reclaimed) while a card is shown is likely to crash
    // again on it: after a second crash in a row, the card shows an error instead of an endless
    // rebuild loop. Scoped to one showing of one card, so the next card (even with identical HTML,
    // or the same card again after a rating) gets a fresh attempt.
    val showKey = remember(cardDisplayIndex, contentKey) { FlashcardShowKey(cardDisplayIndex, contentKey) }
    val currentShowKey by rememberUpdatedState(showKey)
    val currentIsAnswerShown by rememberUpdatedState(isAnswerShown)
    var crashedShow by remember { mutableStateOf<FlashcardShowKey?>(null) }
    var stalledShow by remember { mutableStateOf<FlashcardShowKey?>(null) }
    LaunchedEffect(showKey) {
        if (crashedShow != showKey) crashedShow = null
        if (stalledShow != showKey) stalledShow = null
    }

    Crossfade(
        targetState = Pair(isAnswerShown, if (isAnswerShown) answerHtml else questionHtml),
        animationSpec = tween(300),
        label = "FlashcardCrossfade",
    ) { (shown, currentHtml) ->
        val currentStyle =
            if (shown) bodyLargeStyle else displayLargeStyle.copy(fontWeight = FontWeight.W500)
        val currentPadding = if (shown) 40 else 36

        val composeStyle =
            remember(
                onSurfaceColorHex,
                surfaceColorHex,
                surfaceContainerColorHex,
                primaryColorHex,
                outlineColorHex,
                currentStyle,
                currentPadding,
                toolbarHeight,
                applyHiramekiCssMode,
            ) {
                if (applyHiramekiCssMode == Prefs.HIRAMEKI_CSS_DISABLED) {
                    """<style id="compose-styles"></style>"""
                } else {
                    val fontSizeStyles =
                        if (applyHiramekiCssMode == Prefs.HIRAMEKI_CSS_NO_FONT_SIZE) {
                            ""
                        } else {
                            """
                            font-size: ${currentStyle.fontSize.value}px;
                            line-height: ${currentStyle.lineHeight.value}px;
                            letter-spacing: ${currentStyle.letterSpacing.value}px;
                            """.trimIndent()
                        }

                    """
                    <style id="compose-styles">
                        @import url('https://fonts.googleapis.com/css2?family=Roboto:ital,wght@0,100..900;1,100..900&display=swap');
                        html {
                            color: ${onSurfaceColorHex}EF;
                            background-color: $surfaceColorHex;
                        }
                        body.card {
                            text-align: center;
                            font-family: "Roboto", sans-serif;
                            $fontSizeStyles
                            font-weight: ${currentStyle.fontWeight?.weight ?: 400};
                            text-wrap: pretty;
                            padding-top: ${currentPadding}px;
                            padding-bottom: ${toolbarHeight}px;
                            margin-left: 10px;
                            margin-right: 10px;
                            background-color: $surfaceColorHex;
                            color: ${onSurfaceColorHex}EF;
                        }
                        body.card .back {
                            font-weight: 400;
                            line-height: 1.4;
                        }
                        body.card.nightMode, body.card.night_mode {
                            background-color: $surfaceColorHex;
                            color: ${onSurfaceColorHex}EF;
                        }
                        hr {
                            opacity: 0.1;
                            margin: 12px 0px;
                        }
                        img {
                            border-radius: 16px;
                        }
                        button {
                            font-family: inherit;
                            font-size: 14px;
                            font-weight: 500;
                            color: $onSurfaceColorHex;
                            background-color: $surfaceContainerColorHex;
                            border: 1px solid ${outlineColorHex}40;
                            border-radius: 12px;
                            padding: 2px 6px;
                            cursor: pointer;
                            transition: background-color 0.2s, box-shadow 0.2s, transform 0.1s;
                            align-items: center;
                            justify-content: center;
                            min-height: 48px;
                            box-shadow: 0 1px 2px rgba(0,0,0,0.05);
                        }
                        button:hover {
                            background-color: ${surfaceContainerColorHex}D9;
                            box-shadow: 0 4px 8px rgba(0,0,0,0.1);
                        }
                        button:active {
                            background-color: ${surfaceContainerColorHex}B3;
                            transform: scale(0.97);
                        }
                        button:focus {
                            outline: 2px solid $primaryColorHex;
                            outline-offset: 2px;
                        }
                        button:disabled {
                            opacity: 0.45;
                            cursor: not-allowed;
                            transform: none;
                        }

                        body.card .replay-button {
                            --replay-button-size: 42px;
                            --replay-button-icon-color: $onSurfaceColorHex;
                            display: inline-flex;
                            align-items: center;
                            justify-content: center;
                            margin: 8px;
                            border-radius: 8px;
                            width: var(--replay-button-size);
                            height: var(--replay-button-size);
                            color: var(--replay-button-icon-color);
                            text-decoration: none;
                            cursor: pointer;
                            transition: transform 0.1s, opacity 0.2s;
                            -webkit-tap-highlight-color: transparent;
                        }
                        body.card .replay-button:hover {
                            opacity: 0.85;
                        }
                        body.card .replay-button:active {
                            opacity: 0.7;
                            transform: scale(0.97);
                        }
                        body.card .replay-button:focus-visible {
                            outline: 2px solid $primaryColorHex;
                            outline-offset: 2px;
                        }
                        body.card .replay-button .play-action {
                            display: block;
                            width: 100%;
                            height: 100%;
                            color: inherit;
                            fill: currentColor;
                        }
                        body.card .replay-button .play-action path {
                            fill: currentColor;
                        }
                    </style>
                    """.trimIndent()
                }
            }
        val styledHtml =
            remember(context, isNightMode, composeStyle) {
                buildStyledHtml(context, isNightMode, composeStyle)
            }
        val hasImageOcclusion = currentHtml.contains("image-occlusion-container")
        val sideToken =
            remember(contentKey, shown) {
                "${contentKey.hashCode()}_$shown".hashCode().toString(16)
            }
        val evalScript =
            remember(shown, currentHtml, answerHtml, bodyClass, hasImageOcclusion, sideToken) {
                buildCardScript(
                    shown,
                    currentHtml,
                    answerHtml,
                    bodyClass,
                    hasImageOcclusion,
                    sideToken,
                )
            }

        if (stalledShow == showKey) {
            RendererFailedMessage(modifier)
            return@Crossfade
        }

        key(rendererGeneration) {
            val generation = rendererGeneration
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams =
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                        settings.javaScriptEnabled = true
                        settings.allowFileAccess = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = !isMediaAutoplayEnabled
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false

                        webChromeClient =
                            object : WebChromeClient() {
                                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                                    Timber
                                        .tag("FlashcardJS")
                                        .d(
                                            "${consoleMessage.message()} -- From line ${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}",
                                        )
                                    return true
                                }
                            }

                        webViewClient =
                            object : WebViewClient() {
                                val resourceHandler = ViewerResourceHandler(context)

                                override fun shouldInterceptRequest(
                                    view: WebView,
                                    request: WebResourceRequest,
                                ): WebResourceResponse? = resourceHandler.shouldInterceptRequest(request)

                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: WebResourceRequest,
                                ): Boolean {
                                    val uri = request.url
                                    val scheme = uri.scheme
                                    val ignoredSchemes = setOf("file", "data", "javascript", "blob")
                                    if (scheme in ignoredSchemes) {
                                        return false
                                    }

                                    val urlString = uri.toString()
                                    val payload = view.tag as? FlashcardPayload
                                    val effectiveBaseUrl = payload?.baseUrl ?: currentBaseUrl
                                    if (urlString.startsWith(effectiveBaseUrl)) {
                                        val path = urlString.removePrefix(effectiveBaseUrl)
                                        if (path.isEmpty() || path.startsWith("#") || path.startsWith("/#")) {
                                            return false
                                        }
                                    }

                                    currentOnLinkClick(urlString)
                                    return true
                                }

                                override fun onPageFinished(
                                    view: WebView,
                                    url: String,
                                ) {
                                    val payload = view.tag as? FlashcardPayload ?: return
                                    payload.shellLoaded = true

                                    val pendingScript = payload.pendingShellScript

                                    if (pendingScript != null) {
                                        view.evaluateJavascript(pendingScript, null)
                                        payload.pendingShellScript = null
                                        payload.scriptExecuted = true
                                    } else if (!payload.scriptExecuted) {
                                        payload.scriptExecuted = true
                                        view.evaluateJavascript(payload.evalScript, null)
                                    }

                                    payload.pendingJavascriptCommand?.let { command ->
                                        view.evaluateJavascript(command.script, null)
                                        payload.lastJavascriptCommandId = command.id
                                        payload.pendingJavascriptCommand = null
                                        currentOnJavascriptCommandConsumed(command.id)
                                    }
                                }

                                override fun onRenderProcessGone(
                                    view: WebView,
                                    detail: RenderProcessGoneDetail,
                                ): Boolean {
                                    // Every WebView on the renderer gets this callback for the same loss
                                    // (two during the question/answer crossfade): the first one handles it
                                    if (generation != rendererGeneration) return true
                                    val crashed = detail.didCrash()
                                    val show = currentShowKey
                                    Timber.w("Flashcard: WebView renderer gone (crashed: %b)", crashed)
                                    if (crashed && show == crashedShow) {
                                        Timber.e("Flashcard: renderer crashed twice in a row on one card")
                                        stalledShow = show
                                    } else if (crashed) {
                                        crashedShow = show
                                    }
                                    rendererGeneration++
                                    // handled: returning false makes Android kill the app
                                    return true
                                }
                            }

                        val gestureListener =
                            CardGestureListener(
                                webView = this,
                                currentDisplay = { currentShowKey to currentIsAnswerShown },
                                onGesture = { currentOnGesture(it) },
                            )
                        setOnScrollChangeListener { _, scrollX, scrollY, oldScrollX, oldScrollY ->
                            gestureListener.onScrollChanged(scrollX - oldScrollX, scrollY - oldScrollY)
                        }

                        // Returns false so the WebView still receives the touch: links, scrolling
                        // and text selection keep working.
                        @SuppressLint("ClickableViewAccessibility")
                        setOnTouchListener { _, event ->
                            gestureListener.onTouchEvent(event)
                            false
                        }

                        setBackgroundColor(Color.TRANSPARENT)
                    }
                },
                update = { webView ->
                    webView.settings.mediaPlaybackRequiresUserGesture = !isMediaAutoplayEnabled
                    val currentPayload = webView.tag as? FlashcardPayload
                    val shellChanged =
                        currentPayload?.isNightMode != isNightMode || currentPayload.composeStyle != composeStyle
                    val shouldReload = currentPayload == null || currentPayload.contentKey != contentKey

                    when {
                        shouldReload -> {
                            webView.tag =
                                FlashcardPayload(
                                    contentKey,
                                    baseUrl,
                                    isNightMode,
                                    composeStyle,
                                    evalScript,
                                    pendingJavascriptCommand = javascriptCommand,
                                )
                            webView.loadDataWithBaseURL(baseUrl, styledHtml, "text/html", "UTF-8", null)
                        }

                        shellChanged -> {
                            currentPayload.baseUrl = baseUrl
                            currentPayload.isNightMode = isNightMode
                            currentPayload.composeStyle = composeStyle
                            currentPayload.evalScript = evalScript
                            val shellScript =
                                buildShellUpdateScript(isNightMode, bodyClass, composeStyle, evalScript)
                            if (javascriptCommand != null && currentPayload.lastJavascriptCommandId != javascriptCommand.id) {
                                currentPayload.pendingJavascriptCommand = javascriptCommand
                            }

                            if (currentPayload.shellLoaded) {
                                webView.evaluateJavascript(shellScript, null)
                                currentPayload.pendingJavascriptCommand?.let { command ->
                                    webView.evaluateJavascript(command.script, null)
                                    currentPayload.lastJavascriptCommandId = command.id
                                    currentPayload.pendingJavascriptCommand = null
                                    currentOnJavascriptCommandConsumed(command.id)
                                }
                            } else {
                                // When FlashcardPayload.shellLoaded is false, onPageFinished runs
                                // FlashcardPayload.pendingShellScript before
                                // FlashcardPayload.pendingJavascriptCommand. That preserves shell-first
                                // ordering, while currentOnJavascriptCommandConsumed only runs after the
                                // command executes and FlashcardPayload.lastJavascriptCommandId becomes the
                                // idempotency key for replay avoidance.
                                currentPayload.pendingShellScript = shellScript
                            }
                        }

                        javascriptCommand != null && currentPayload.lastJavascriptCommandId != javascriptCommand.id -> {
                            if (currentPayload.shellLoaded) {
                                webView.evaluateJavascript(javascriptCommand.script, null)
                                currentPayload.lastJavascriptCommandId = javascriptCommand.id
                                currentPayload.pendingJavascriptCommand = null
                                currentOnJavascriptCommandConsumed(javascriptCommand.id)
                            } else {
                                currentPayload.pendingJavascriptCommand = javascriptCommand
                            }
                        }

                        currentPayload.shellLoaded -> {
                            currentPayload.baseUrl = baseUrl
                            if (currentPayload.evalScript != evalScript) {
                                currentPayload.evalScript = evalScript
                                webView.evaluateJavascript(evalScript, null)
                            }
                        }

                        else -> {
                            currentPayload.baseUrl = baseUrl
                            currentPayload.evalScript = evalScript
                        }
                    }
                },
                onRelease = { webView ->
                    webView.stopLoading()
                    webView.webViewClient = WebViewClient()
                    webView.webChromeClient = null
                    webView.setOnTouchListener(null)
                    webView.setOnScrollChangeListener(null)
                    webView.destroy()
                },
                modifier =
                    modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
            )
        }
    }
}

/**
 * Detects taps, double taps and swipes on the card [webView] for the user's gesture settings (#185).
 */
private class CardGestureListener(
    private val webView: WebView,
    /** Identifies the card and side being shown, see [displayAtTouchDown]. */
    private val currentDisplay: () -> Any,
    private val onGesture: (CardTouchGesture) -> Unit,
) : GestureDetector.SimpleOnGestureListener() {
    private val detector = GestureDetector(webView.context, this)
    private val edgeMarginPx = NO_GESTURE_BORDER_DP * webView.resources.displayMetrics.density
    private var lastXScrollTime = 0L
    private var lastYScrollTime = 0L

    /** A second finger touched during the current gesture, e.g. pinch-to-zoom. */
    private var wasMultiTouch = false

    /**
     * The card and side shown when the touch started. A single tap is only confirmed after the
     * double tap timeout, by which time an answer button may have moved on to the next card.
     */
    private var displayAtTouchDown: Any? = null

    fun onTouchEvent(event: MotionEvent) {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            wasMultiTouch = false
            displayAtTouchDown = currentDisplay()
        }
        if (event.pointerCount > 1) wasMultiTouch = true
        detector.onTouchEvent(event)
    }

    fun onScrollChanged(
        dx: Int,
        dy: Int,
    ) {
        val now = SystemClock.uptimeMillis()
        if (abs(dx) > abs(dy)) lastXScrollTime = now else lastYScrollTime = now
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        if (!wasMultiTouch && !isOnInteractiveElement()) {
            send(CardTouchGesture.Tap(e.x, e.y, webView.width, webView.height))
        }
        return true
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        if (!wasMultiTouch && !isOnInteractiveElement()) {
            send(CardTouchGesture.DoubleTap)
        }
        return true
    }

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float,
    ): Boolean {
        // #5741: a swipe from the screen edge is a system gesture
        if (e1 == null || wasMultiTouch || isTouchingEdge(e1)) return false
        val now = SystemClock.uptimeMillis()
        send(
            CardTouchGesture.Fling(
                dx = e2.x - e1.x,
                dy = e2.y - e1.y,
                velocityX = velocityX,
                velocityY = velocityY,
                isXScrolling = now - lastXScrollTime < SCROLL_TIMEOUT_MS,
                isYScrolling = now - lastYScrollTime < SCROLL_TIMEOUT_MS,
            ),
        )
        return false
    }

    /** Sends [gesture] unless the card or side changed since the touch started. */
    private fun send(gesture: CardTouchGesture) {
        if (currentDisplay() != displayAtTouchDown) return
        onGesture(gesture)
    }

    private fun isTouchingEdge(e: MotionEvent): Boolean =
        e.x < edgeMarginPx ||
            e.y < edgeMarginPx ||
            webView.width - e.x < edgeMarginPx ||
            webView.height - e.y < edgeMarginPx

    /** #6141: a tap on a link, a replay button or an input belongs to the card, not to a gesture. */
    private fun isOnInteractiveElement(): Boolean =
        when (webView.hitTestResult.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE,
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE,
            WebView.HitTestResult.EDIT_TEXT_TYPE,
            -> true
            else -> false
        }

    companion object {
        /** Matches the legacy reviewer's NO_GESTURE_BORDER_DIP. */
        private const val NO_GESTURE_BORDER_DP = 24

        /** A scroll within this window means a fling scrolled the card instead of swiping. */
        private const val SCROLL_TIMEOUT_MS = 300L
    }
}

/** Shown in place of a card whose WebView renderer crashed twice in a row. */
@Composable
private fun RendererFailedMessage(modifier: Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.webview_crash_loop_dialog_title),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Payload stored in the WebView tag for communication between the update callback and onPageFinished.
 */
private data class FlashcardContentKey(
    val questionHash: Int,
    val answerHash: Int,
)

/** One showing of one card: see the crash-loop handling in [Flashcard]. */
private data class FlashcardShowKey(
    val cardDisplayIndex: Long,
    val content: FlashcardContentKey,
)

private class FlashcardPayload(
    val contentKey: FlashcardContentKey,
    var baseUrl: String,
    var isNightMode: Boolean,
    var composeStyle: String,
    var evalScript: String,
    var pendingJavascriptCommand: ReviewerJavascriptCommand? = null,
    var lastJavascriptCommandId: Int = -1,
    var scriptExecuted: Boolean = false,
    var shellLoaded: Boolean = false,
    var pendingShellScript: String? = null,
)

private val EXTRA_JS_ASSETS = listOf("backend/js/reviewer_extras_bundle.js")
private const val REVIEWER_EXTRAS_CSS_LINK =
    """<link rel="stylesheet" type="text/css" href="file:///android_asset/backend/css/reviewer_extras.css">"""

private fun buildStyledHtml(
    context: Context,
    isNightMode: Boolean,
    composeStyle: String,
): String {
    val shell = stdHtml(context, EXTRA_JS_ASSETS, isNightMode)
    return shell.replace("</head>", "$REVIEWER_EXTRAS_CSS_LINK\n$composeStyle\n</head>")
}

/**
 * Builds the JavaScript to show the question or answer side of a card.
 *
 * IMPORTANT: We must NOT embed _showQuestion/_showAnswer in `<script>` tags
 * in the HTML because IO card HTML contains `</script>` which prematurely
 * terminates the script tag, causing raw text to be displayed.
 */
private fun buildCardScript(
    isAnswer: Boolean,
    currentHtml: String,
    answerHtml: String,
    bodyClass: String,
    hasImageOcclusion: Boolean,
    sideToken: String,
): String {
    val showCardScript =
        if (isAnswer) {
            "_showAnswer(${Json.encodeToString(currentHtml)}, ${Json.encodeToString(bodyClass)});"
        } else {
            "_showQuestion(${Json.encodeToString(currentHtml)}, ${Json.encodeToString(answerHtml)}, ${
                Json.encodeToString(
                    bodyClass,
                )
            });"
        }
    return if (hasImageOcclusion) {
        val intercept = IO_SETUP_INTERCEPT.replace($$"${sideToken}", sideToken)
        val postLoad = IO_POST_LOAD_SCRIPT.replace($$"${sideToken}", sideToken)
        "$intercept\n$showCardScript\n$postLoad"
    } else {
        showCardScript
    }
}

/**
 * Builds JavaScript that patches the DOM in-place for a theme change,
 * avoiding a full WebView reload (which causes a blank flash).
 *
 * Updates the root element classes/attributes, replaces the compose-styles
 * CSS content, and re-runs the card display script with the new body class.
 */
private fun buildShellUpdateScript(
    isNightMode: Boolean,
    bodyClass: String,
    composeCssContent: String,
    evalScript: String,
): String {
    val docClass = if (isNightMode) "night-mode" else ""
    val baseTheme = if (isNightMode) "dark" else "light"
    // Escape backticks and backslashes for safe embedding in a JS template literal
    val escapedCss = composeCssContent.replace("\\", "\\\\").replace("`", "\\`")
    return """
        document.documentElement.className = '$docClass';
        document.documentElement.setAttribute('data-bs-theme', '$baseTheme');
        document.body.className = '$bodyClass';
        const s = document.getElementById('compose-styles');
        if (s) s.outerHTML = `$escapedCss`;
        $evalScript
        """.trimIndent()
}

/**
 * Intercepts anki.imageOcclusion.setup() with a no-op BEFORE _showQuestion runs.
 *
 * Why: _showQuestion is async (queued via a Promise chain). When the queued work
 * resolves, it sets innerHTML and then executes the card's inline scripts, including
 * `<script>anki.imageOcclusion.setup()</script>`. setup() waits for the image to load,
 * then uses requestAnimationFrame to size the canvas and draw masks. But if the image
 * is cached, setup() completes (~16ms) before our layout poll fires, resulting
 * in a 0x0 canvas — masks invisible 95% of the time.
 *
 * By intercepting setup() here (before _showQuestion queues), the card's inline script
 * becomes a no-op. Our [IO_POST_LOAD_SCRIPT] then applies layout dimensions and calls
 * the original setup() exactly once, guaranteeing correct canvas sizing.
 */
private const val IO_SETUP_INTERCEPT: String = $$"""
(() => {
    const sideToken = '${sideToken}';
    globalThis.__ioCurrentSide = sideToken;

    if (typeof globalThis.anki?.imageOcclusion?.setup === 'function') {
        // Guard: preserve original ONLY once
        globalThis.__ioOriginalSetup ??= globalThis.anki.imageOcclusion.setup;
        
        // Intercept: return resolved promise if active side, else fallback
        globalThis.anki.imageOcclusion.setup = function(...args) {
            if (globalThis.__ioCurrentSide === sideToken) return Promise.resolve();
            if (typeof globalThis.__ioOriginalSetup === 'function') {
                return globalThis.__ioOriginalSetup.apply(this, args);
            }
            return Promise.resolve();
        };
    }
})();
"""

/**
 * Post-load JavaScript for Image Occlusion layout and setup.
 *
 * IMPORTANT: _showQuestion/_showAnswer are ASYNC (queued via a Promise chain in reviewer.js).
 * When this script runs, the card HTML has NOT yet been injected into #qa.
 * We must poll for the image-occlusion-container to appear, THEN wait for the image to load,
 * THEN apply layout dimensions, THEN call the original setup() exactly once.
 */
private val IO_POST_LOAD_SCRIPT: String =
    $$"""
(() => {
    const sideToken = '${sideToken}';
    let observer = null;
    let timeoutId = null;

    const cleanup = () => {
        if (observer) {
            observer.disconnect();
            observer = null;
        }
        
        if (timeoutId) {
            clearTimeout(timeoutId);
            timeoutId = null;
        }
        
        if (globalThis.__ioCurrentSide === sideToken) {
            if (globalThis.__ioOriginalSetup) {
                globalThis.anki.imageOcclusion.setup = globalThis.__ioOriginalSetup;
                delete globalThis.__ioOriginalSetup;
            }
            delete globalThis.__ioCurrentSide;
        }
    };

    const waitForContainer = () => {
        if (globalThis.__ioCurrentSide !== sideToken) return;

        const container = document.getElementById('image-occlusion-container');
        if (container) return processContainer(container);

        observer = new MutationObserver((mutations, obs) => {
            if (globalThis.__ioCurrentSide !== sideToken) return cleanup();
            
            const target = document.getElementById('image-occlusion-container');
            if (target) {
                // Disconnect observer & timeout, but DO NOT call full cleanup() yet.
                // We need globalThis.__ioCurrentSide to stay alive for processContainer!
                if (observer) { observer.disconnect(); observer = null; }
                if (timeoutId) { clearTimeout(timeoutId); timeoutId = null; }
                
                processContainer(target);
            }
        });

        const targetNode = document.getElementById('qa') || document.body;
        observer.observe(targetNode, { childList: true, subtree: true });

        timeoutId = setTimeout(() => {
            console.warn("AnkiDroid IO: Container wait timed out.");
            cleanup();
        }, 1000);
    };

    const processContainer = (container) => {
        if (globalThis.__ioCurrentSide !== sideToken) return;
        
        const image = container.querySelector('img');
        if (!image) return cleanup();

        if (image.complete && image.naturalWidth > 0) {
            applyLayout(container, image);
        } else {
            image.addEventListener('load', () => applyLayout(container, image));
            image.addEventListener('error', cleanup);
        }
    };

    const applyLayout = (container, image) => {
        if (globalThis.__ioCurrentSide !== sideToken) return;

        try {
            if (image.naturalWidth <= 0 || image.naturalHeight <= 0) return cleanup();

            const width = Math.max(1, container.parentElement?.clientWidth || window.innerWidth || 0);
            const height = Math.max(1, Math.round(width * image.naturalHeight / image.naturalWidth));

            Object.assign(container.style, {
                display: 'block',
                width: width + 'px',
                height: height + 'px',
                minHeight: height + 'px',
                maxWidth: '100%',
                aspectRatio: image.naturalWidth + ' / ' + image.naturalHeight
            });

            Object.assign(image.style, {
                width: width + 'px',
                height: height + 'px'
            });

            const canvas = document.getElementById('image-occlusion-canvas');
            if (canvas) {
                Object.assign(canvas.style, {
                    width: width + 'px',
                    height: height + 'px'
                });
            }

            // Force layout reflow
            void container.offsetHeight;

            // Call the original setup() exactly once for this side
            if (globalThis.__ioOriginalSetup) {
                const original = globalThis.__ioOriginalSetup;
                cleanup(); // Restore original setup before invocation
                original.call(globalThis.anki.imageOcclusion);
            }
        } catch(e) {
            console.error(e);
            cleanup(); // Unconditional restoration on error
        }
    };

    waitForContainer();
})();
    """.trimIndent()
