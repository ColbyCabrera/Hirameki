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

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
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
import com.ichi2.anki.R
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.reviewer.ReviewerJavascriptCommand
import com.ichi2.anki.reviewer.card.CardDisplay
import com.ichi2.anki.reviewer.card.CardPageStyle
import com.ichi2.anki.reviewer.card.CardSideStyle
import com.ichi2.anki.reviewer.card.CardWebViewController
import com.ichi2.anki.settings.Prefs
import com.ichi2.themes.Themes
import com.ichi2.utils.toRGBHex

@Composable
fun Flashcard(
    display: CardDisplay,
    isMediaAutoplayEnabled: Boolean,
    javascriptCommand: ReviewerJavascriptCommand?,
    onJavascriptCommandConsumed: (Int) -> Unit,
    /** A tap, double tap or swipe on the card, for the user's gesture settings. */
    onGesture: (CardTouchGesture) -> Unit,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    toolbarHeight: Int = 0,
) {
    val context = LocalContext.current
    val currentOnLinkClick by rememberUpdatedState(onLinkClick)
    val currentOnGesture by rememberUpdatedState(onGesture)
    val currentOnCommandConsumed by rememberUpdatedState(onJavascriptCommandConsumed)
    val controller =
        remember(context) {
            CardWebViewController(
                context,
                onLinkClick = { currentOnLinkClick(it) },
                onGesture = { currentOnGesture(it) },
                onCommandRun = { currentOnCommandConsumed(it) },
            )
        }
    DisposableEffect(controller) { onDispose { controller.destroy() } }

    val style = rememberCardPageStyle(toolbarHeight)
    SideEffect { controller.update(display, style, isMediaAutoplayEnabled, javascriptCommand) }

    AndroidView(
        factory = { controller.layout },
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
    )
}

@Composable
private fun rememberCardPageStyle(toolbarHeight: Int): CardPageStyle {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val hiramekiCssMode = rememberHiramekiCssMode()
    val isNightMode = Themes.currentTheme.isNightMode
    return remember(colors, typography, hiramekiCssMode, isNightMode, toolbarHeight) {
        CardPageStyle(
            isNightMode = isNightMode,
            surfaceColor = colors.surface.toHex(),
            onSurfaceColor = colors.onSurface.toHex(),
            surfaceContainerColor = colors.surfaceContainer.toHex(),
            primaryColor = colors.primary.toHex(),
            outlineColor = colors.outline.toHex(),
            question = typography.displayMedium.copy(fontWeight = FontWeight.W500).toCardSideStyle(paddingTopPx = 36),
            answer = typography.titleLarge.toCardSideStyle(paddingTopPx = 40),
            toolbarHeightPx = toolbarHeight,
            hiramekiCssMode = hiramekiCssMode,
        )
    }
}

private fun Color.toHex() = toArgb().toRGBHex()

private fun TextStyle.toCardSideStyle(paddingTopPx: Int) =
    CardSideStyle(
        fontSizePx = fontSize.value,
        lineHeightPx = lineHeight.value,
        letterSpacingPx = letterSpacing.value,
        fontWeight = fontWeight?.weight ?: 400,
        paddingTopPx = paddingTopPx,
    )

@Composable
private fun rememberHiramekiCssMode(): String {
    val context = LocalContext.current
    val sharedPrefs = remember(context) { context.sharedPrefs() }
    val prefKey = stringResource(R.string.apply_hirameki_css_preference)

    fun readMode() = sharedPrefs.getString(prefKey, Prefs.HIRAMEKI_CSS_ALL) ?: Prefs.HIRAMEKI_CSS_ALL

    var mode by remember(sharedPrefs, prefKey) { mutableStateOf(readMode()) }
    DisposableEffect(sharedPrefs, prefKey) {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == prefKey) mode = readMode()
            }
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return mode
}
