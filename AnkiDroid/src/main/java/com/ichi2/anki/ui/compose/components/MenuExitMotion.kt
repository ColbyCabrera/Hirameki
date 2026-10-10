/*
 *  Copyright (c) 2026 Hirameki contributors
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
package com.ichi2.anki.ui.compose.components

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember

/**
 * how every compose menu in the app closes: wrap the one `DropdownMenu` or `ExposedDropdownMenu` call, and
 * pass the same [expanded] the menu gets.
 *
 * material3 menus run one transition both ways: scale on the fastSpatial spring, alpha on fastEffects. under
 * `MotionScheme.expressive()` fastEffects is spring(stiffness 3800), so a closing menu is ~80% transparent
 * after 50ms and all but gone by 100ms, before its shrink shows: opening reads as animated, closing as
 * vanishing. while closing, the alpha takes the theme's defaultEffects spring instead (~43% opaque at 50ms,
 * ~10% at 100ms), so the shrink stays visible. the entrance is untouched, and the popup is removed on the same
 * frame as before, since the scale spring still decides when the transition ends.
 *
 * the scheme has to flip in the same composition pass as the menu's own transition target: a transition only
 * adopts a new spec when its target changes, so a scheme that reached the popup a frame later would be ignored
 * by the exit already running. that is why [expanded] must be the very value handed to the menu.
 *
 * the exit only plays while the menu call stays composed, so no `if (expanded)` around it, and the data its
 * content reads has to outlive the dismiss, or the popup changes size while it closes.
 */
@Composable
fun MenuExitMotion(
    expanded: Boolean,
    content: @Composable () -> Unit,
) {
    val base = MaterialTheme.motionScheme
    val closing =
        remember(base) {
            object : MotionScheme by base {
                override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = base.defaultEffectsSpec()
            }
        }
    // material3 has no public way to provide a motion scheme alone, only a nested theme. that theme also
    // re-provides the ripple, the text selection colors and a bodyLarge text style, so the caller's own are
    // handed back and the menu differs in motion only (a menu in a dialog's text slot would otherwise lose
    // the dialog's text style)
    val textStyle = LocalTextStyle.current
    val indication = LocalIndication.current
    val selectionColors = LocalTextSelectionColors.current
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        motionScheme = if (expanded) base else closing,
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography,
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides textStyle,
            LocalIndication provides indication,
            LocalTextSelectionColors provides selectionColors,
            content = content,
        )
    }
}
