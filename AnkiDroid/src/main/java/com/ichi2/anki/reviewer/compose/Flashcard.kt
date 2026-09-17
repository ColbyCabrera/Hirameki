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
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.ichi2.anki.R
import com.ichi2.anki.ViewerResourceHandler
import com.ichi2.anki.multimedia.SILENT_PAUSE_DATASET_KEY
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.previewer.stdHtml
import com.ichi2.anki.reviewer.ReviewerJavascriptCommand
import com.ichi2.anki.settings.Prefs
import com.ichi2.themes.ArabicScriptFont
import com.ichi2.themes.Themes
import com.ichi2.utils.toRGBHex
import kotlinx.serialization.json.Json
import timber.log.Timber

/** Name of the object the page reports long presses through; see [listenToPage]. */
@VisibleForTesting
internal const val LONG_PRESS_BRIDGE = "hiramekiLongPress"

/** What the page reports for a long press that selected text. Any other report is a long press that selected nothing. */
@VisibleForTesting
internal const val LONG_PRESS_SELECTED = "selected"

/**
 * Script loaded once into the page shell, beside the reviewer's own.
 *
 * Recolouring: a note type sets its colours in its own css, under any class name it likes, and the
 * app never sees which, so a list of selectors always misses one — that is how a deck's main word
 * stayed blue after `.cloze` and the editor swatches were covered. Matching the rendered colour
 * cannot miss: anything whose computed text colour is a blue takes `--hirameki-primary`. Painting
 * with the variable rather than a fixed colour means a theme change, which swaps the style block in
 * place, recolours everything already themed. Other hues are untouched, so colour coding a deck
 * relies on, like red and green, survives. Without the variable (hirameki css switched off) it does
 * nothing.
 *
 * Replay buttons: a tapped button is marked playing until the app reports the sound has finished,
 * through `window.hiramekiAudioStopped`, because the page itself never hears the native player.
 *
 * Text fit: a card taller than its page shrinks its text before it has to scroll, through
 * `window.hiramekiFitText`, which [runShow] queues behind every show, so it measures the card after
 * reviewer.js has typeset it and in the same task that makes it visible. See the script's own notes.
 *
 * Long presses: the page tells the app, through [LONG_PRESS_BRIDGE], whether a long press selected text,
 * which only the page knows. See the script's own notes.
 *
 * Lives in the shell rather than the style block: a style block swapped in via outerHTML would
 * carry a script that never runs.
 */
private const val HIRAMEKI_PAGE_SCRIPT = """
    <script id="hirameki-page">
    (function () {
        function primary() {
            return getComputedStyle(document.documentElement).getPropertyValue('--hirameki-primary').trim();
        }
        function rgbOf(value) {
            var match = /rgba?\(([^)]+)\)/.exec(value || '');
            if (!match) return null;
            var parts = match[1].split(',').map(function (part) { return parseFloat(part); });
            if (parts.length > 3 && parts[3] === 0) return null;
            return parts;
        }
        function isBlue(rgb) {
            var r = rgb[0] / 255, g = rgb[1] / 255, b = rgb[2] / 255;
            var max = Math.max(r, g, b), min = Math.min(r, g, b), spread = max - min;
            // judged on colourfulness, not saturation alone: pale blues such as anki's night-mode
            // lightblue must count, while grey-blues used for muted text, like slate, must not
            if (max === 0 || spread < 0.15 || spread / max < 0.2) return false;
            var hue = max === r ? ((g - b) / spread) % 6 : max === g ? (b - r) / spread + 2 : (r - g) / spread + 4;
            hue *= 60;
            if (hue < 0) hue += 360;
            return hue >= 185 && hue <= 255;
        }
        // hirameki's own emphasis is already themed: recolouring it would paint the cloze chip's text in the
        // same accent as its background whenever the theme itself is blue
        var OWN_EMPHASIS = '.cloze, .cloze *, mark, mark *, .replay-button, .replay-button *';
        function recolour() {
            if (!document.body || !primary()) return;
            var bodyColour = getComputedStyle(document.body).color;
            var nodes = document.body.querySelectorAll('*');
            for (var i = 0; i < nodes.length; i++) {
                var node = nodes[i];
                if (node.getAttribute('data-hirameki-themed')) continue;
                if (node.matches(OWN_EMPHASIS)) continue;
                var colour = getComputedStyle(node).color;
                var rgb = rgbOf(colour);
                // bold is how most decks mark the word being learnt. only bold that is still the plain body
                // colour takes the accent; bold inside text a deck coloured on purpose keeps that colour
                var plainBold = (node.tagName === 'B' || node.tagName === 'STRONG') && colour === bodyColour;
                if ((rgb && isBlue(rgb)) || plainBold) {
                    node.style.setProperty('color', 'var(--hirameki-primary)', 'important');
                    node.setAttribute('data-hirameki-themed', '1');
                }
            }
        }
        var pending = false;
        function schedule() {
            if (pending) return;
            pending = true;
            requestAnimationFrame(function () { pending = false; recolour(); });
        }
        function clearPlaying() {
            var playing = document.querySelectorAll('.replay-button.hirameki-playing');
            for (var i = 0; i < playing.length; i++) playing[i].classList.remove('hirameki-playing');
        }
        window.hiramekiAudioStopped = clearPlaying;

        // text fit. a card taller than its page shrinks its text before it has to scroll. the supporting text (the size
        // most of the card's text is set in, and anything not clearly larger: sentences, extra fields, the answer,
        // furigana) gives way first, down to FIT_SUPPORT_SCALE of the size the deck gave it. the main word, text set
        // clearly larger than that, gives way only after that, and only when that makes the card fit: down to
        // FIT_MAIN_SCALE, never below the supporting text's own size. a card that fits at no size these allow scrolls
        // at the deck's own sizes: shrunk, it would only scroll a little less, and a card taller than its page for
        // another reason (an image, a deck's min-height) not at all. nothing grows past the deck's own size, and every
        // fit starts again from the deck's sizes, so a card that gets more room gets its text back
        var FIT_SUPPORT_SCALE = 0.85;
        var FIT_MAIN_SCALE = 0.7;
        // css px no text is shrunk below; text a deck already set smaller keeps its size
        var FIT_MIN_PX = 16;
        // text at least this many times the body size counts as the main word. measured from the body, not from the
        // largest text: a card set all in one size plus a small note or furigana made its whole body the main word,
        // which then shrank past the supporting text's floor
        var FIT_MAIN_RATIO = 1.15;
        // halvings of the search between a floor and full size: 6 lands within 0.5% of the best scale
        var FIT_STEPS = 6;
        // sized by rules of their own: math is typeset in ems of the text around it and follows it, and form
        // controls, media and replay buttons keep the size the page gave them
        var FIT_SKIP = 'mjx-container, mjx-container *, math, math *, svg, svg *, .replay-button, .replay-button *, ' +
            'button, button *, input, select, textarea, img, video, audio, canvas, iframe, br, hr, script, style';
        var fitted = [];
        function inlineOf(node, property) {
            return { value: node.style.getPropertyValue(property), priority: node.style.getPropertyPriority(property) };
        }
        function putBack(node, property, inline) {
            if (inline.value) node.style.setProperty(property, inline.value, inline.priority);
            else node.style.removeProperty(property);
        }
        function unfit() {
            for (var i = 0; i < fitted.length; i++) {
                putBack(fitted[i].node, 'font-size', fitted[i].fontSize);
                putBack(fitted[i].node, 'line-height', fitted[i].lineHeight);
            }
            fitted = [];
        }
        function overflows() {
            var root = document.documentElement;
            return root.scrollHeight > root.clientHeight;
        }
        // set by the style block only in the hirameki css mode that sets font sizes: a user who switched font size
        // changes off, or all hirameki css, keeps every card at exactly the sizes its deck gives it
        function fitAllowed() {
            return getComputedStyle(document.documentElement).getPropertyValue('--hirameki-fit').trim() === '1';
        }
        function ownTextLength(node) {
            var length = 0;
            for (var child = node.firstChild; child; child = child.nextSibling) {
                if (child.nodeType === 3) length += child.nodeValue.trim().length;
            }
            return length;
        }
        // every entry gets an explicit size, changed or not: one left to inherit would follow a shrunk parent
        // through its em size, past its own floor
        function sizeTo(supportScale, mainScale) {
            for (var i = 0; i < fitted.length; i++) {
                var entry = fitted[i];
                var px = Math.max(entry.size * (entry.main ? mainScale : supportScale), entry.floor);
                entry.node.style.setProperty('font-size', px + 'px', 'important');
                // a line height in px keeps its proportion, or shrunk text would keep the old lines' height
                if (entry.line) entry.node.style.setProperty('line-height', entry.line * px / entry.size + 'px', 'important');
            }
        }
        // the card fits with apply(floor) and not with apply(1): settles on the largest scale between them that fits
        function largestFitting(floor, apply) {
            var low = floor, high = 1;
            for (var step = 0; step < FIT_STEPS; step++) {
                var middle = (low + high) / 2;
                apply(middle);
                if (overflows()) high = middle; else low = middle;
            }
            apply(low);
        }
        function fitText() {
            unfit();
            var qa = document.getElementById('qa');
            // never in a css mode that leaves font sizes to the deck; nothing to measure before the page is laid out; and
            // not on image occlusion, whose image and masks its own script sizes to the page
            if (!qa || !fitAllowed() || document.documentElement.clientHeight <= 0 || !overflows() ||
                qa.querySelector('#image-occlusion-container')) return;
            var nodes = [qa].concat(Array.prototype.slice.call(qa.querySelectorAll('*')));
            // characters of visible text at each size; text the deck hides has no say in which size is which
            var charsAt = {};
            for (var i = 0; i < nodes.length; i++) {
                var node = nodes[i];
                if (node !== qa && node.matches(FIT_SKIP)) continue;
                var style = getComputedStyle(node);
                var size = parseFloat(style.fontSize);
                if (!(size > 0)) continue;
                var chars = node.getClientRects().length > 0 ? ownTextLength(node) : 0;
                if (chars) charsAt[size] = (charsAt[size] || 0) + chars;
                fitted.push({
                    node: node,
                    size: size,
                    text: chars > 0,
                    // 0 for a 'normal' line height, which follows the font size by itself
                    line: parseFloat(style.lineHeight) || 0,
                    fontSize: inlineOf(node, 'font-size'),
                    lineHeight: inlineOf(node, 'line-height')
                });
            }
            // the body size is the one most of the card's text is set in
            var body = 0, most = 0;
            for (var key in charsAt) {
                if (charsAt[key] > most) {
                    most = charsAt[key];
                    body = parseFloat(key);
                }
            }
            if (!body) return unfit();
            // on a card with no text clearly larger than its body there is no main word: all of its text is supporting
            var hasMain = false, mainFloor = FIT_MIN_PX;
            for (var j = 0; j < fitted.length; j++) {
                fitted[j].main = fitted[j].size >= body * FIT_MAIN_RATIO;
                if (!fitted[j].text) continue;
                if (fitted[j].main) hasMain = true;
                else mainFloor = Math.max(mainFloor, fitted[j].size);
            }
            for (var k = 0; k < fitted.length; k++) {
                if (!hasMain) fitted[k].main = false;
                fitted[k].floor = Math.min(fitted[k].size, fitted[k].main ? mainFloor : FIT_MIN_PX);
            }
            sizeTo(FIT_SUPPORT_SCALE, 1);
            if (!overflows()) return largestFitting(FIT_SUPPORT_SCALE, function (scale) { sizeTo(scale, 1); });
            if (hasMain) {
                sizeTo(FIT_SUPPORT_SCALE, FIT_MAIN_SCALE);
                if (!overflows()) return largestFitting(FIT_MAIN_SCALE, function (scale) { sizeTo(FIT_SUPPORT_SCALE, scale); });
            }
            // no size the rules allow makes the card fit, so it scrolls at the deck's own sizes
            unfit();
        }
        var fitPending = false;
        // before the next paint: resize and animation frame callbacks both run ahead of it
        function scheduleFit() {
            if (fitPending) return;
            fitPending = true;
            requestAnimationFrame(function () { fitPending = false; fitText(); });
        }
        window.hiramekiFitText = fitText;

        function start() {
            recolour();
            // cards arrive by replacing the page content, so watch for it; attributes are not
            // watched, which keeps the recolouring itself from re-triggering the observer
            new MutationObserver(schedule).observe(document.body, { childList: true, subtree: true, characterData: true });
            window.addEventListener('resize', scheduleFit);
            // an image or a font that arrives after its card was shown changes how tall the card is
            document.addEventListener('load', function (event) {
                if (event.target && event.target.tagName === 'IMG') scheduleFit();
            }, true);
            if (document.fonts && document.fonts.addEventListener) document.fonts.addEventListener('loadingdone', scheduleFit);
            document.addEventListener('click', function (event) {
                var button = event.target && event.target.closest && event.target.closest('.replay-button');
                if (!button) return;
                // one sound at a time: a new tap takes the playing state from any other button
                clearPlaying();
                button.classList.add('hirameki-playing');
            }, true);
            // long press. chromium answers one by selecting the word under it, when there is one, and then fires
            // contextmenu, on blank card area too. the app hears whether the press selected text: the moves after it then
            // extend the selection, and must not drag the card along. a selection an earlier press left does not count,
            // only one this press started
            var pressSelected = false;
            window.addEventListener('pointerdown', function () { pressSelected = false; }, true);
            window.addEventListener('selectstart', function () { pressSelected = true; }, true);
            window.addEventListener('contextmenu', function () {
                var selection = window.getSelection();
                var selected = pressSelected && selection && !selection.isCollapsed;
                if (window.$LONG_PRESS_BRIDGE) $LONG_PRESS_BRIDGE.postMessage(selected ? '$LONG_PRESS_SELECTED' : 'none');
            }, true);
        }
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', start);
        } else {
            start();
        }
    })();
    </script>
"""

/** Tells the page the replay sound has finished, so the tapped button can stop looking busy. */
private const val AUDIO_STOPPED_SCRIPT = "window.hiramekiAudioStopped && window.hiramekiAudioStopped();"

/** Body classes for the side on show; the style block sizes each side by them. */
private const val QUESTION_SIDE_CLASS = "hirameki-question"
private const val ANSWER_SIDE_CLASS = "hirameki-answer"

/** Room above each side's content, in css px; a stable layout gives the question the answer's. */
private const val QUESTION_PADDING_PX = 36
private const val ANSWER_PADDING_PX = 40

/**
 * Pauses the page's own playing audio and video, for a card face being turned away. Each is marked
 * first: a video's onpause reports a pause to the app, which reads it as the user pausing and stops the
 * answer side's autoplay sequence, and it skips a marked one. The mark is cleared by a listener added
 * after that onpause, so it runs after it. The pause event itself still reaches everything else, the
 * video's own controls included; swallowing it left their play button showing a paused video as playing.
 */
private const val PAUSE_MEDIA_SCRIPT =
    "document.querySelectorAll('audio, video').forEach(function (m) {" +
        " if (m.paused) return;" +
        " m.dataset.$SILENT_PAUSE_DATASET_KEY = '1';" +
        " m.addEventListener('pause', function () { delete m.dataset.$SILENT_PAUSE_DATASET_KEY; }, { once: true });" +
        " m.pause();" +
        " });"

/** Marker the tap probe returns when the tap landed on something the card itself handles. */
private const val INTERACTIVE_TOKEN = "interactive"

/** Selectors for card content that owns its own taps: audio replay, links, form controls. */
private const val INTERACTIVE_SELECTORS = "a,button,input,select,textarea,video,audio,[onclick],.replay-button"

/**
 * Asks the page what sits under a tap, in device pixels, so a tap on a replay button plays audio
 * instead of turning the card over. Conversion happens in the page because only it knows its own
 * pixel ratio and pinch-zoom scale.
 */
private fun interactiveAtPointScript(
    rawX: Float,
    rawY: Float,
): String =
    """
    (function() {
        try {
            var viewport = window.visualViewport;
            var ratio = window.devicePixelRatio || 1;
            var scale = (viewport && viewport.scale) ? viewport.scale : 1;
            var x = $rawX / (ratio * scale) + (viewport ? viewport.offsetLeft : 0);
            var y = $rawY / (ratio * scale) + (viewport ? viewport.offsetTop : 0);
            var element = document.elementFromPoint(x, y);
            return (element && element.closest('$INTERACTIVE_SELECTORS')) ? '$INTERACTIVE_TOKEN' : 'plain';
        } catch (e) {
            return 'plain';
        }
    })()
    """.trimIndent()

/** Name of the object the page reports finished shows through; see [listenToPage]. */
@VisibleForTesting
internal const val PAINT_BRIDGE = "hiramekiPaint"

/** Marks the fade [fadeInScript] plays, so [CANCEL_FADE_SCRIPT] stops that and never a card's own animation. */
private const val FADE_ID = "hirameki-fade"

/** Reports show [seq] once reviewer.js has swapped it in: queued behind the show, not run when it is sent. */
private fun paintReportScript(seq: Long) = "_queueAction(function () { if (window.$PAINT_BRIDGE) $PAINT_BRIDGE.postMessage('$seq'); });"

/**
 * Runs [script] after any queued show, so a command for the new card (a video's autoplay) finds its html.
 * A throw is caught and logged to the page console: left to escape, it would leave reviewer.js's queue
 * rejected, and every later show on the page would silently never run.
 */
private fun queuedScript(script: String) = "_queueAction(function () {\ntry {\n$script\n} catch (e) { console.error(e); }\n});"

/**
 * Stops a fade still running from the last show, so reviewer.js can keep #qa hidden while it swaps: a running
 * animation overrides the inline opacity 0 that hides the swap. Queued rather than run when the show is sent:
 * sent during the last show's preload it found no fade yet, that fade then started just before this swap, and
 * the new html showed through it before it was typeset. On an idle queue it still runs before the next frame.
 */
private const val CANCEL_FADE_SCRIPT =
    "_queueAction(function () { var qa = document.getElementById('qa'); if (qa && qa.getAnimations) " +
        "qa.getAnimations().forEach(function (a) { if (a.id === '$FADE_ID') a.cancel(); }); });"

/**
 * Starts a new card's page at the top. Of reviewer.js's shows only _showQuestion scrolls to the top; _showAnswer
 * only scrolls an #answer element into view, and a card view's back face never shows a question, so a new card's
 * answer opened at the last card's scroll offset. Queued ahead of the show, so #answer's own scroll still wins.
 */
private const val SCROLL_TO_TOP_SCRIPT = "_queueAction(function () { window.scrollTo(0, 0); });"

/** Set on the page by each show sent to reviewer.js, so [SHOWN_PROBE_SCRIPT] can tell the shell from its replacement. */
private const val SHOWN_MARK = "hiramekiShown"

/** True only in the page shell once a show was sent to it: a reloaded shell has no mark, another document no shell. */
private const val SHOWN_PROBE_SCRIPT = "window.$SHOWN_MARK === true"

/** Recovery loads of the shell for one show before the page gives up; a card that navigates away on every show is a loop. */
@VisibleForTesting
internal const val MAX_SHELL_RELOADS = 3

/** Fades swapped-in content in over the unchanged card background; queued, so it starts with the new html. */
private fun fadeInScript(millis: Int) =
    "_queueAction(function () { var qa = document.getElementById('qa'); if (qa && qa.animate) " +
        "qa.animate([{ opacity: 0 }, { opacity: 1 }], { duration: $millis, easing: 'ease-out', id: '$FADE_ID' }); });"

/**
 * Fits the card's text to its page (see [HIRAMEKI_PAGE_SCRIPT]), queued behind a show: reviewer.js makes the card
 * visible at the end of the show, after typesetting its math, and this runs in that same task, before the page paints.
 */
private val FIT_TEXT_SCRIPT = queuedScript("window.hiramekiFitText && window.hiramekiFitText();")

/**
 * Sends the page's current show to reviewer.js, which swaps it in once its fonts and images are loaded,
 * and asks the page to report when it has. A [newCard] starts at the top of the page.
 */
private fun runShow(
    webView: WebView,
    payload: FlashcardPayload,
    fadeMillis: Int,
    newCard: Boolean,
) {
    payload.showSeq += 1
    val scroll = if (newCard) SCROLL_TO_TOP_SCRIPT else ""
    val fade = if (fadeMillis > 0) fadeInScript(fadeMillis) else ""
    // marked last: in a document without reviewer.js the first call throws, so such a document is never marked
    val script =
        "$CANCEL_FADE_SCRIPT\n$scroll\n${payload.evalScript}\n$FIT_TEXT_SCRIPT\n$fade\n" +
            "${paintReportScript(payload.showSeq)}\nwindow.$SHOWN_MARK = true;"
    webView.evaluateJavascript(script, null)
}

private fun runCommand(
    webView: WebView,
    payload: FlashcardPayload,
    command: ReviewerJavascriptCommand,
    onConsumed: (Int) -> Unit,
) {
    webView.evaluateJavascript(queuedScript(command.script), null)
    payload.lastJavascriptCommandId = command.id
    payload.pendingJavascriptCommand = null
    onConsumed(command.id)
}

/**
 * Hears what the page reports. A finished show: [onPainted] is told once that state will be on the next draw, with the
 * show's paint key and [showId]. The page's word is not enough: DOM changes reach the screen asynchronously, and
 * postVisualStateCallback is the platform's promise that the next draw shows them. A long press: [onLongPress] is told
 * whether it selected text.
 */
private fun listenToPage(
    webView: WebView,
    baseUrl: String,
    onPainted: (paintKey: Long, show: Int) -> Unit,
    onLongPress: (selectedText: Boolean) -> Unit,
) {
    // lint's RequiresFeature check did not follow an early return here, so the listener calls sit in a helper that
    // only runs once this check has passed
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
        Timber.w("Flashcard: webview cannot message the app; the card view waits out paint timeouts and long presses")
    } else {
        registerPageListeners(webView, baseUrl, onPainted, onLongPress)
    }
}

@SuppressLint("RequiresFeature") // only called from listenToPage, after its WEB_MESSAGE_LISTENER check
private fun registerPageListeners(
    webView: WebView,
    baseUrl: String,
    onPainted: (paintKey: Long, show: Int) -> Unit,
    onLongPress: (selectedText: Boolean) -> Unit,
) {
    val uri = baseUrl.toUri()
    // a new document re-registers; removing an absent listener is a no-op
    WebViewCompat.removeWebMessageListener(webView, PAINT_BRIDGE)
    WebViewCompat.removeWebMessageListener(webView, LONG_PRESS_BRIDGE)
    val origin = "${uri.scheme}://${uri.encodedAuthority}"
    WebViewCompat.addWebMessageListener(webView, PAINT_BRIDGE, setOf(origin)) { view, message, _, isMainFrame, _ ->
        // a card's own iframe can share the origin; only the page itself reports shows
        if (!isMainFrame) return@addWebMessageListener
        // any script on the card can reach this object, and postMessage takes an arraybuffer as readily as
        // a string. reading data() on one throws ("wrong data accessor type") out of a webview callback,
        // which kills the app; a show is only ever reported as a string
        if (message.type != WebMessageCompat.TYPE_STRING) return@addWebMessageListener
        val seq = message.data?.toLongOrNull() ?: return@addWebMessageListener
        val payload = view.tag as? FlashcardPayload ?: return@addWebMessageListener
        // superseded by a later show, which reports for itself
        if (seq != payload.showSeq) return@addWebMessageListener
        val key = payload.paintKey
        val show = showId(payload.evalScript, payload.paintKey)
        view.postVisualStateCallback(
            seq,
            object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) {
                    if (view.tag === payload && !payload.released && requestId == payload.showSeq) onPainted(key, show)
                }
            },
        )
    }
    WebViewCompat.addWebMessageListener(webView, LONG_PRESS_BRIDGE, setOf(origin)) { _, message, _, isMainFrame, _ ->
        // as for show reports: the page itself, and a string only, since reading data() on an arraybuffer throws
        if (!isMainFrame || message.type != WebMessageCompat.TYPE_STRING) return@addWebMessageListener
        onLongPress(message.data == LONG_PRESS_SELECTED)
    }
}

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
    onTap: () -> Unit,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    isAnswerShown: Boolean,
    toolbarHeight: Int = 0,
    /**
     * Render the question with the same typography and padding as the answer. The default sizes the
     * question larger, which reflows the whole card when the answer appears; a card that flips in
     * place needs both sides to occupy the same space.
     */
    useStableLayout: Boolean = false,
    /**
     * Length of the fade-in of content swapped into the page, a new card or a new side. 0 swaps
     * instantly, e.g. hidden by a flip.
     */
    sideChangeDurationMs: Int = 300,
    /**
     * Page background, so the card html matches the surface it is drawn on. Defaults to the theme
     * surface; a card face passes its own container tone.
     */
    pageColor: Color? = null,
    /**
     * Counts replay taps that have finished playing. Each change tells the page to restore its dimmed
     * replay button; a count rather than an on/off flag, because a sound that starts and stops within
     * one frame would never be seen as a flag at all.
     */
    replayFinished: Int = 0,
    /** Whether this page is the one on screen; a page that stops showing pauses its own media. */
    isShowing: Boolean = true,
    /**
     * Told when the page's webview is created (true) and released (false). The page keeps one webview
     * for as long as it is composed, and cards and sides are swapped inside it; only a webview whose
     * renderer died is replaced by a new one.
     */
    onWebView: (webView: WebView, alive: Boolean) -> Unit = { _, _ -> },
    /** Identifies what the page is asked to show; a change re-shows it even when the html is identical. */
    paintKey: Long = 0L,
    /** Told the [paintKey] of content the page has actually drawn, not merely been sent. */
    onPainted: (paintKey: Long) -> Unit = {},
    /** Told, once the page has handled a long press, whether that press selected text, whose moves then extend it. */
    onLongPress: (selectedText: Boolean) -> Unit = {},
) {
    val currentBaseUrl by rememberUpdatedState(baseUrl)
    val currentOnJavascriptCommandConsumed by rememberUpdatedState(onJavascriptCommandConsumed)
    val currentOnLinkClick by rememberUpdatedState(onLinkClick)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnWebView by rememberUpdatedState(onWebView)
    val currentOnPainted by rememberUpdatedState(onPainted)
    val currentOnLongPress by rememberUpdatedState(onLongPress)

    val context = LocalContext.current
    val sharedPrefs = remember(context) { context.sharedPrefs() }
    val prefKey = stringResource(R.string.apply_hirameki_css_preference)
    var applyHiramekiCssMode by remember {
        mutableStateOf(
            sharedPrefs.getString(prefKey, Prefs.HIRAMEKI_CSS_ALL) ?: Prefs.HIRAMEKI_CSS_ALL
        )
    }

    val arabicScriptFontKey = stringResource(R.string.arabic_script_font_key)
    var useArabicScriptFont by remember { mutableStateOf(Prefs.useArabicScriptFont) }

    // both are part of the style block, which the update block swaps into the loaded page, so a change made in
    // settings restyles the card behind them without reloading it
    val listener = remember(sharedPrefs, prefKey, arabicScriptFontKey) {
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == prefKey) {
                applyHiramekiCssMode =
                    sharedPrefs.getString(prefKey, Prefs.HIRAMEKI_CSS_ALL) ?: Prefs.HIRAMEKI_CSS_ALL
            }
            if (key == arabicScriptFontKey) {
                useArabicScriptFont = Prefs.useArabicScriptFont
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
    val surfaceColor = pageColor ?: MaterialTheme.colorScheme.surface
    val surfaceColorHex = surfaceColor.toArgb().toRGBHex()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceColorHex = onSurfaceColor.toArgb().toRGBHex()
    val surfaceContainerColor = MaterialTheme.colorScheme.surfaceContainer
    val surfaceContainerColorHex = surfaceContainerColor.toArgb().toRGBHex()
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryColorHex = primaryColor.toArgb().toRGBHex()
    val outlineColor = MaterialTheme.colorScheme.outline
    val outlineColorHex = outlineColor.toArgb().toRGBHex()
    // selection and links otherwise render in the webview's stock blue, ignoring the wallpaper theme
    val colorScheme = MaterialTheme.colorScheme
    val primaryContainerColorHex = colorScheme.primaryContainer.toArgb().toRGBHex()
    val onPrimaryContainerColorHex = colorScheme.onPrimaryContainer.toArgb().toRGBHex()
    val typography = MaterialTheme.typography
    val displayLargeStyle = typography.displayMedium
    val bodyLargeStyle = typography.titleLarge

    val contentKey = remember(questionHtml, answerHtml) {
        FlashcardContentKey(questionHtml.hashCode(), answerHtml.hashCode())
    }

    val currentHtml = if (isAnswerShown) answerHtml else questionHtml
    // one document for the page's life: the side is a body class that the show call sets with the html
    val pageBodyClass = "$bodyClass ${if (isAnswerShown) ANSWER_SIDE_CLASS else QUESTION_SIDE_CLASS}"
    val questionStyle = if (useStableLayout) bodyLargeStyle else displayLargeStyle.copy(fontWeight = FontWeight.W500)
    val questionPadding = if (useStableLayout) ANSWER_PADDING_PX else QUESTION_PADDING_PX

    val composeStyle = remember(
        onSurfaceColorHex,
        surfaceColorHex,
        surfaceContainerColorHex,
        primaryColorHex,
        primaryContainerColorHex,
        onPrimaryContainerColorHex,
        outlineColorHex,
        questionStyle,
        bodyLargeStyle,
        questionPadding,
        toolbarHeight,
        applyHiramekiCssMode,
        useArabicScriptFont,
    ) {
        if (applyHiramekiCssMode == Prefs.HIRAMEKI_CSS_DISABLED) {
            """<style id="compose-styles"></style>"""
        } else {
            // one answer for the side sizes below and for the page script's text fit, which changes font sizes too
            val setsFontSizes = applyHiramekiCssMode != Prefs.HIRAMEKI_CSS_NO_FONT_SIZE

            fun sideType(
                style: TextStyle,
                paddingTop: Int,
            ): String {
                val size =
                    if (!setsFontSizes) {
                        ""
                    } else {
                        "font-size: ${style.fontSize.value}px; line-height: ${style.lineHeight.value}px; " +
                            "letter-spacing: ${style.letterSpacing.value}px;"
                    }
                return "$size font-weight: ${style.fontWeight?.weight ?: 400}; padding-top: ${paddingTop}px;"
            }
            // vazirmatn follows roboto in the stack and its unicode-range takes only arabic-script characters, so latin
            // keeps roboto and a font a deck sets on its own elements stays the deck's; see ArabicScriptFont (#139)
            val arabicScriptFontFace = if (useArabicScriptFont) ArabicScriptFont.CARD_FONT_FACE else ""
            val arabicScriptFamily = if (useArabicScriptFont) "\"${ArabicScriptFont.CARD_FONT_FAMILY}\", " else ""

            """
            <style id="compose-styles">
                /* no webfont import: android's own roboto is this same face, while fetching it from google on
                   every load of the page worked only online and told google when a card was shown */
                $arabicScriptFontFace
                html {
                    color: ${onSurfaceColorHex}EF;
                    background-color: $surfaceColorHex;
                }
                body.card {
                    text-align: center;
                    font-family: "Roboto", ${arabicScriptFamily}sans-serif;
                    text-wrap: pretty;
                    padding-bottom: ${toolbarHeight}px;
                    margin-left: 10px;
                    margin-right: 10px;
                    background-color: $surfaceColorHex;
                    color: ${onSurfaceColorHex}EF;
                }
                /* each side's type sits under its own class, which reviewer.js sets in the same step as it
                   swaps the html in, so a side is never drawn in the other side's size. :where() keeps these
                   at body.card's specificity, so a deck's own .card rules win or lose exactly as before */
                body.card:where(.$QUESTION_SIDE_CLASS) { ${sideType(questionStyle, questionPadding)} }
                body.card:where(.$ANSWER_SIDE_CLASS) { ${sideType(bodyLargeStyle, ANSWER_PADDING_PX)} }
                body.card .back {
                    font-weight: 400;
                    line-height: 1.4;
                }
                ::selection {
                    background-color: $primaryContainerColorHex;
                    color: $onPrimaryContainerColorHex;
                }
                ::-moz-selection {
                    background-color: $primaryContainerColorHex;
                    color: $onPrimaryContainerColorHex;
                }
                a, a:visited {
                    color: $primaryColorHex;
                    -webkit-tap-highlight-color: ${primaryContainerColorHex}59;
                }
                mark {
                    background-color: ${primaryContainerColorHex}80;
                    color: $onPrimaryContainerColorHex;
                    border-radius: 4px;
                    padding: 0 2px;
                }
                /* anki's stock cloze note type hard-codes blue (lightblue at night) for the target
                   word; a primary-container chip carries the same emphasis and follows the wallpaper */
                .cloze, .nightMode .cloze, .night_mode .cloze {
                    color: $onPrimaryContainerColorHex !important;
                    background-color: ${primaryContainerColorHex}B3;
                    border-radius: 8px;
                    padding: 1px 7px;
                    font-weight: 700;
                }
                /* the note's other deletions are context, not the target */
                .cloze-inactive, .nightMode .cloze-inactive, .night_mode .cloze-inactive {
                    color: inherit !important;
                }
                .cloze-hint {
                    color: $primaryColorHex !important;
                }
                /* the divider between question and answer in the stock templates */
                hr#answer {
                    border: none;
                    height: 2px;
                    background-color: ${primaryColorHex}59;
                    opacity: 1;
                    width: 64%;
                    /* auto side margins centre it at whatever width or max-width the note type
                       gives it; equal percentage margins only centred it at the width they assumed */
                    margin: 16px auto !important;
                    border-radius: 1px;
                }
                /* the editor's "blue" swatch, which is unreadable on a dark card; other colours a
                   deck uses on purpose, like gender colouring, are left alone */
                font[color="blue" i], font[color="#0000ff" i], font[color="#00f" i],
                [style*="color: blue" i], [style*="color:blue" i],
                [style*="color: #0000ff" i], [style*="color:#0000ff" i],
                [style*="color: rgb(0, 0, 255)" i] {
                    color: $primaryColorHex !important;
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
                    color: ${onSurfaceColorHex};
                    background-color: ${surfaceContainerColorHex};
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
                /* hover only where a pointer can hover: on a touchscreen :hover sticks to
                   whatever was tapped last, so a tapped button kept its hover look for good */
                @media (hover: hover) {
                    button:hover {
                        background-color: ${surfaceContainerColorHex}D9;
                        box-shadow: 0 4px 8px rgba(0,0,0,0.1);
                    }
                    body.card .replay-button:hover {
                        opacity: 0.85;
                    }
                }
                button:active {
                    background-color: ${surfaceContainerColorHex}B3;
                    transform: scale(0.97);
                }
                /* keyboard focus only: a tap focuses a button too, and :focus kept the outline */
                button:focus-visible {
                    outline: 2px solid ${primaryColorHex};
                    outline-offset: 2px;
                }
                button:disabled {
                    opacity: 0.45;
                    cursor: not-allowed;
                    transform: none;
                }

                body.card .replay-button {
                    --replay-button-size: 42px;
                    --replay-button-icon-color: ${onSurfaceColorHex};
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
                    position: relative;
                }
                /* the button keeps its 42px look and reaches the 48px minimum touch target through this
                   overlay, which stays inside the button's own margin, so nothing on the card moves. a tap
                   on it resolves to the button itself, so the edge of the target plays instead of flipping */
                body.card .replay-button::before {
                    content: '';
                    position: absolute;
                    inset: -3px;
                }
                body.card .replay-button:active {
                    opacity: 0.7;
                    transform: scale(0.97);
                }
                body.card .replay-button:focus-visible {
                    outline: 2px solid ${primaryColorHex};
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
                /* read by the page script, which repaints a deck's blue text in the theme accent, and fits a card's
                   text to its page only where the fit flag is set */
                :root {
                    --hirameki-primary: $primaryColorHex;
                    ${if (setsFontSizes) "--hirameki-fit: 1;" else ""}
                }
                /* a replay button stays dimmed for exactly as long as its sound plays */
                body.card .replay-button.hirameki-playing {
                    opacity: 0.4;
                }
            </style>
            """.trimIndent()
        }
    }
    val styledHtml = remember(context, isNightMode, composeStyle) {
        buildStyledHtml(context, isNightMode, composeStyle)
    }
    // a scan of the whole card html: once per html, not on every recomposition
    val hasImageOcclusion = remember(currentHtml) { currentHtml.contains("image-occlusion-container") }
    val sideToken = remember(contentKey, isAnswerShown) {
        "${contentKey.hashCode()}_${isAnswerShown}".hashCode().toString(16)
    }
    val evalScript =
        remember(isAnswerShown, currentHtml, answerHtml, pageBodyClass, hasImageOcclusion, sideToken) {
            buildCardScript(
                isAnswerShown, currentHtml, answerHtml, pageBodyClass, hasImageOcclusion, sideToken
            )
        }

    // bumped when the renderer behind the webview dies (#143): it keys the webview, so a fresh one replaces the dead one
    var rendererGeneration by remember { mutableIntStateOf(0) }
    // the show whose renderer crashed and that has not been drawn since, and a show whose fresh renderer then crashed
    // too before drawing it; see onRenderProcessGone
    var crashedShow by remember { mutableStateOf<Int?>(null) }
    var stalledShow by remember { mutableStateOf<Int?>(null) }
    val show = showId(evalScript, paintKey)
    // both hold for one appearance of one show, never for the rest of the session: another show (a new card or side,
    // or the same card shown again) or the page coming back on screen (a card face turned back to, whose show a flip
    // never changes) gets a fresh attempt. a page turned away keeps its stall: rebuilt while hidden, it would crash the
    // renderer that the face now on show shares
    LaunchedEffect(show, isShowing) {
        if (isShowing || crashedShow != show) crashedShow = null
        if (isShowing || stalledShow != show) stalledShow = null
    }

    val pageModifier = modifier
        .fillMaxSize()
        // match the page, so a card face never shows a band of the wrong tone while loading. the colour the style block
        // paints the page with, read from the one place, so the two tones cannot drift apart
        .background(surfaceColor)
    if (stalledShow == show) {
        // only the page's tone: a webview would crash its renderer once more, and with it every other page on that
        // renderer. the effect above lifts the stall
        Box(pageModifier)
        return
    }

    key(rendererGeneration) {
        AndroidView(
            factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.allowFileAccess = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = !isMediaAutoplayEnabled
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                // a hidden card face rasters its page before it is shown, so a flip or an entrance reveals finished
                // tiles. the platform allows it for a few webviews no larger than the screen, as a card's faces are
                settings.offscreenPreRaster = true

                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                        Timber.tag("FlashcardJS")
                            .d("${consoleMessage.message()} -- From line ${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}")
                        return true
                    }
                }

                webViewClient = object : WebViewClient() {
                    val resourceHandler = ViewerResourceHandler(context)

                    override fun shouldInterceptRequest(
                        view: WebView, request: WebResourceRequest
                    ): WebResourceResponse? {
                        return resourceHandler.shouldInterceptRequest(request)
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView, request: WebResourceRequest
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
                            if (path.startsWith("#") || path.startsWith("/#")) {
                                return false
                            }
                            // the page's own address, from a card's href="", "/" or "./", is no link to open elsewhere, and
                            // loaded it replaced the shell every card is swapped into with the server's 404
                            if (path.isEmpty()) return true
                        }

                        currentOnLinkClick(urlString)
                        return true
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        val payload = view.tag as? FlashcardPayload ?: return
                        if (!payload.shellLoaded) {
                            payload.shellLoaded = true
                            // shell, then show, then command: the order the update block keeps once loaded
                            payload.pendingShellScript?.let {
                                view.evaluateJavascript(it, null)
                                payload.pendingShellScript = null
                            }
                            runShow(view, payload, fadeMillis = 0, newCard = false)
                        } else {
                            // a later finish is an in-page #anchor navigation, which keeps the card, or a document that
                            // replaced the shell: a card's location.reload() or form post, which never reach
                            // shouldOverrideUrlLoading. cards are only swapped into the shell, so a replaced one stayed empty
                            // for the rest of the session. the page is asked which, rather than trusting how the load
                            // callbacks of two documents interleave
                            view.evaluateJavascript(SHOWN_PROBE_SCRIPT) { shown ->
                                val stale = view.tag !== payload || payload.released || !payload.shellLoaded
                                if (stale || shown == "true") return@evaluateJavascript
                                if (payload.shellReloads >= MAX_SHELL_RELOADS) {
                                    // a card whose html navigates away every time it is shown: each recovery load
                                    // runs the whole shell and that html again, so loading once more only spins the
                                    // renderer. the face stays empty for this card, as it did before the recovery
                                    // existed, and shellReplaced loads the page again for the next card or side
                                    Timber.e("Flashcard: the card keeps replacing the page, leaving it blank")
                                    payload.shellReplaced = true
                                    return@evaluateJavascript
                                }
                                // the scheme only: the url comes from the card, and a warning reaches release logcat
                                // and the crash reports acra uploads from it
                                Timber.w("Flashcard: shell replaced by a %s document, loading it again", url.toUri().scheme)
                                payload.shellReloads += 1
                                payload.shellLoaded = false
                                loadShell(view, payload.baseUrl, buildStyledHtml(view.context, payload.isNightMode, payload.composeStyle))
                            }
                        }
                        payload.pendingJavascriptCommand?.let {
                            runCommand(view, payload, it, currentOnJavascriptCommandConsumed)
                        }
                    }

                    // #143: the renderer is a separate process, which android reclaims from a backgrounded app and
                    // restarts on every webview update. left unhandled (false), webview kills the whole app with it, or
                    // crashes the app when the renderer crashed, and android then reopened the deck list instead of the
                    // reviewer. a dead webview never draws again: a new generation releases it (onRelease destroys it)
                    // and composes a fresh one, whose first update loads the shell and shows the same card and side
                    override fun onRenderProcessGone(
                        view: WebView,
                        detail: RenderProcessGoneDetail,
                    ): Boolean {
                        val payload = view.tag as? FlashcardPayload
                        // a visual-state callback the dead page still delivers must not report its card painted
                        payload?.released = true
                        val goneShow = payload?.let { showId(it.evalScript, it.paintKey) }
                        Timber.w("Flashcard: renderer gone (crashed: %b)", detail.didCrash())
                        if (detail.didCrash()) {
                            if (goneShow != null && goneShow == crashedShow) {
                                // the fresh renderer crashed on the same show before drawing it, as every further one
                                // would. a drawn show clears crashedShow, so two crashes with a working page between
                                // them (another page on the renderer crashing it, say) are no loop. only crashes count:
                                // the system reclaims a background renderer again and again, whatever the card
                                Timber.e("Flashcard: renderer crashed twice in a row on the same card, leaving it blank")
                                stalledShow = goneShow
                            } else {
                                crashedShow = goneShow
                            }
                        }
                        // also when stalled: a show sent after the crash is another show, and gets a fresh webview
                        rendererGeneration++
                        return true
                    }
                }

                val gestureDetector = GestureDetector(
                    context, object : GestureDetector.SimpleOnGestureListener() {
                        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                            // a tap on a replay button, link or form control belongs to the card's own
                            // html; only taps on blank card area should turn the card over
                            val webView = this@apply
                            webView.evaluateJavascript(interactiveAtPointScript(e.x, e.y)) { result ->
                                if (result?.contains(INTERACTIVE_TOKEN) != true) {
                                    currentOnTap()
                                }
                            }
                            return true
                        }
                    })

                @SuppressLint("ClickableViewAccessibility") setOnTouchListener { _, event ->
                    gestureDetector.onTouchEvent(event)
                    false
                }

                setBackgroundColor(Color.Transparent.toArgb())
                currentOnWebView(this, true)
            }
        }, update = { webView ->
            webView.settings.mediaPlaybackRequiresUserGesture = !isMediaAutoplayEnabled
            val payload = webView.tag as? FlashcardPayload
            val newCommand = javascriptCommand?.takeIf { it.id != payload?.lastJavascriptCommandId }
            when {
                // nothing to show before the first card, whose base url every relative media path needs
                baseUrl.isEmpty() -> Unit
                // the page's only full load. later cards and sides are swapped into this document by reviewer.js,
                // which keeps the last card painted until the next one's images and fonts are in and then replaces
                // #qa in one step. a document per card (the old crossfade) showed the bare page in the theme colour
                // until ~2.4 MB of shell scripts had run again: the blink between cards on decks with their own
                // background, issue #135. only a new base url (the reviewer server's port) needs a new document
                payload == null || payload.baseUrl != baseUrl -> {
                    webView.tag = FlashcardPayload(
                        baseUrl,
                        isNightMode,
                        applyHiramekiCssMode,
                        composeStyle,
                        evalScript,
                        paintKey,
                        pendingJavascriptCommand = newCommand,
                        // seeded from the props they track: left at their defaults, a page made after replays had
                        // finished, or made for a face turned away, saw a change on its first update and sent the
                        // page a replay restore and a media pause before the shell had even loaded
                        replayFinished = replayFinished,
                        showing = isShowing,
                    )
                    listenToPage(
                        webView,
                        baseUrl,
                        onPainted = { paintedKey, drawnShow ->
                            // drawn, the show works: a later crash is no crash loop, so it gets a rebuild, not a stall (#143)
                            if (crashedShow == drawnShow) crashedShow = null
                            currentOnPainted(paintedKey)
                        },
                        onLongPress = { currentOnLongPress(it) },
                    )
                    loadShell(webView, baseUrl, styledHtml)
                }
                // still loading: onPageFinished runs the latest of each
                !payload.shellLoaded -> {
                    if (payload.isNightMode != isNightMode || payload.composeStyle != composeStyle) {
                        payload.isNightMode = isNightMode
                        payload.cssMode = applyHiramekiCssMode
                        payload.composeStyle = composeStyle
                        payload.pendingShellScript = buildShellUpdateScript(isNightMode, composeStyle)
                    }
                    payload.evalScript = evalScript
                    payload.paintKey = paintKey
                    if (newCommand != null) payload.pendingJavascriptCommand = newCommand
                }
                else -> {
                    val nightModeChanged = payload.isNightMode != isNightMode
                    // the page script recolours card html only as it arrives, painting a deck's blue text with the
                    // style block's accent variable. switching hirameki css on or off adds or removes that variable,
                    // so the card on show needs its html again: recoloured, or back in the deck's own colours
                    val cssModeChanged = payload.cssMode != applyHiramekiCssMode
                    if (nightModeChanged || payload.composeStyle != composeStyle) {
                        payload.isNightMode = isNightMode
                        payload.cssMode = applyHiramekiCssMode
                        payload.composeStyle = composeStyle
                        webView.evaluateJavascript(buildShellUpdateScript(isNightMode, composeStyle), null)
                    }
                    // a style-only change (the answer bar's height) needs no re-show: re-showing replayed the swap
                    // and the scroll to #answer. a theme or css mode change re-shows, for card scripts that read the
                    // night-mode class and for the recolouring, but without the fade: the same card has nothing new
                    val newCard = payload.paintKey != paintKey
                    val contentChanged = payload.evalScript != evalScript || newCard
                    // another show is a fresh attempt for the shell-replacement recovery in onPageFinished
                    if (contentChanged) payload.shellReloads = 0
                    if (contentChanged && payload.shellReplaced) {
                        // recovery gave up on a card that replaced the shell on every show, so this document is
                        // not the page. this is another card or side: the page is loaded again and shows it
                        payload.evalScript = evalScript
                        payload.paintKey = paintKey
                        payload.shellReplaced = false
                        payload.shellLoaded = false
                        loadShell(webView, baseUrl, styledHtml)
                    } else if (nightModeChanged || cssModeChanged || contentChanged) {
                        payload.evalScript = evalScript
                        payload.paintKey = paintKey
                        runShow(webView, payload, if (contentChanged) sideChangeDurationMs else 0, newCard)
                    }
                    if (newCommand != null) runCommand(webView, payload, newCommand, currentOnJavascriptCommandConsumed)
                }
            }
            (webView.tag as? FlashcardPayload)?.let { current ->
                // the page dims a tapped replay button itself, but only the app hears the native
                // player finish; every finished replay bumps the count, and each change restores it
                if (current.replayFinished != replayFinished) {
                    webView.evaluateJavascript(AUDIO_STOPPED_SCRIPT, null)
                    current.replayFinished = replayFinished
                }
                // a face turned away keeps its page loaded for the next flip, but must stop playing
                if (current.showing && !isShowing) {
                    webView.evaluateJavascript(PAUSE_MEDIA_SCRIPT, null)
                }
                current.showing = isShowing
            }
        }, onRelease = { webView ->
            // a visual-state callback still in flight must not report a page that is gone
            (webView.tag as? FlashcardPayload)?.released = true
            currentOnWebView(webView, false)
            webView.stopLoading()
            webView.webViewClient = WebViewClient()
            webView.webChromeClient = null
            webView.setOnTouchListener(null)
            webView.destroy()
        }, modifier = pageModifier
        )
    }
}

private data class FlashcardContentKey(
    val questionHash: Int,
    val answerHash: Int,
)

/**
 * Identifies a show for the renderer crash guard: the script that swaps the side in, and the paint key, which a card
 * gets anew each time it is shown, so the same card coming back is another show.
 */
private fun showId(
    evalScript: String,
    paintKey: Long,
): Int = 31 * evalScript.hashCode() + paintKey.hashCode()

/**
 * Payload stored in the WebView tag for communication between the update callback and onPageFinished.
 * One per document: a new base url starts a new one.
 */
private class FlashcardPayload(
    val baseUrl: String,
    var isNightMode: Boolean,
    /** The hirameki css mode the page's style block was built for; a change re-shows the card. */
    var cssMode: String,
    var composeStyle: String,
    var evalScript: String,
    /** The paint key of the latest show, reported back once the page has drawn it. */
    var paintKey: Long,
    var pendingJavascriptCommand: ReviewerJavascriptCommand? = null,
    var lastJavascriptCommandId: Int = -1,
    var shellLoaded: Boolean = false,
    var pendingShellScript: String? = null,
    var replayFinished: Int = 0,
    var showing: Boolean = true,
    /** Counts the shows sent to this document; a paint report only counts for the latest. */
    var showSeq: Long = 0L,
    /** Recovery loads of the shell for the show on screen; another show is a fresh attempt. */
    var shellReloads: Int = 0,
    /** Set once recovery gave up: the document is not the shell, so the next show has to load it again. */
    var shellReplaced: Boolean = false,
    /** Set once the webview is released, so a late visual-state callback reports nothing. */
    var released: Boolean = false,
)

private val EXTRA_JS_ASSETS = listOf("backend/js/reviewer_extras_bundle.js")
private const val REVIEWER_EXTRAS_CSS_LINK =
    """<link rel="stylesheet" type="text/css" href="file:///android_asset/backend/css/reviewer_extras.css">"""

private fun buildStyledHtml(context: Context, isNightMode: Boolean, composeStyle: String): String {
    val shell = stdHtml(context, EXTRA_JS_ASSETS, isNightMode)
    return shell.replace("</head>", "$REVIEWER_EXTRAS_CSS_LINK\n$composeStyle\n$HIRAMEKI_PAGE_SCRIPT\n</head>")
}

/** Loads the page shell, the page's one document: every card after it is swapped in by reviewer.js. */
private fun loadShell(
    webView: WebView,
    baseUrl: String,
    html: String,
) = webView.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)

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
    val showCardScript = if (isAnswer) {
        "_showAnswer(${Json.encodeToString(currentHtml)}, ${Json.encodeToString(bodyClass)});"
    } else {
        "_showQuestion(${Json.encodeToString(currentHtml)}, ${Json.encodeToString(answerHtml)}, ${
            Json.encodeToString(
                bodyClass
            )
        });"
    }
    return if (hasImageOcclusion) {
        val intercept = IO_SETUP_INTERCEPT.replace($$"${sideToken}", sideToken)
        // queued behind the show: the page keeps the last card's html until reviewer.js swaps this one in,
        // so run at once it would find the last card's container and set that one up instead
        val postLoad = queuedScript(IO_POST_LOAD_SCRIPT.replace($$"${sideToken}", sideToken))
        "$intercept\n$showCardScript\n$postLoad"
    } else {
        showCardScript
    }
}

/**
 * Builds JavaScript that patches the DOM in-place for a theme or style change,
 * avoiding a full WebView reload (which causes a blank flash).
 *
 * Updates the root element classes/attributes and replaces the compose-styles CSS
 * content. The body class is left to the show call, which sets it in the same step
 * as the card html: set here, a new side's class reached the page while a queued
 * show still had the last side on screen, drawn in the new side's type. The caller
 * re-shows the card when night mode or the hirameki css mode changed.
 */
private fun buildShellUpdateScript(
    isNightMode: Boolean,
    composeCssContent: String,
): String {
    val docClass = if (isNightMode) "night-mode" else ""
    val baseTheme = if (isNightMode) "dark" else "light"
    // a json string is also a js string literal, so the css arrives exactly as built. the js template literal this
    // replaced escaped only backslashes and backticks, and ran any dollar-brace in the css (a font face, say) as code
    val cssLiteral = Json.encodeToString(composeCssContent)
    return """
        document.documentElement.className = '$docClass';
        document.documentElement.setAttribute('data-bs-theme', '$baseTheme');
        {
            // a block, not a top-level const: every evaluateJavascript call shares the page's global
            // scope, so a second shell update redeclaring `s` threw a SyntaxError and applied nothing
            const s = document.getElementById('compose-styles');
            if (s) s.outerHTML = $cssLiteral;
        }
        // the style decides the card's room (the answer bar's height is part of it), so the text is fitted again
        window.hiramekiFitText && window.hiramekiFitText();
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
 * IMPORTANT: _showQuestion/_showAnswer are ASYNC (queued via a Promise chain in reviewer.js), and
 * the page keeps the last card's HTML in #qa until the show swaps the new one in. [buildCardScript]
 * therefore queues this script behind the show, so the container it finds is the new card's. It then
 * waits for the image to load, THEN applies layout dimensions, THEN calls the original setup() exactly
 * once. The observer covers a container that the card's own scripts only add after the show.
 */
private val IO_POST_LOAD_SCRIPT: String = $$"""
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
