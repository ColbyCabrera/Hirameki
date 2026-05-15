/***************************************************************************************
 * Copyright (c) 2026 Colby Cabrera <colbycabrera@gmail.com>                            *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.ichi2.anki.ui.compose.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme

/**
 * A toggle switch component that wraps the Material 3 [Switch] with
 * check (✓) and close (✕) icons displayed inside the thumb.
 *
 * @param checked Whether the toggle is currently in the "on" state.
 * @param onCheckedChange Callback invoked when the user toggles the switch.
 *   Pass `null` to make the switch non-interactable.
 * @param interactionSource A [MutableInteractionSource] for observing and
 *   emitting [Interaction]s. Hoist this to coordinate haptic feedback or
 *   visual state changes with other components.
 * @param modifier [Modifier] applied to the underlying [Switch].
 * @param enabled Controls the enabled state. When `false`, the switch will
 *   not respond to user input and appears visually disabled.
 * @param colors [SwitchColors] used to resolve thumb and track colors.
 */
@Composable
fun AnkiToggle(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {

    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        thumbContent = {
            if (checked) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            }
        },
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
    )
}

@Preview(name = "Checked")
@Composable
private fun AnkiToggleCheckedPreview() {
    AnkiDroidTheme {
        AnkiToggle(
            checked = true,
            onCheckedChange = {},
            interactionSource = remember { MutableInteractionSource() },
        )
    }
}

@Preview(name = "Unchecked")
@Composable
private fun AnkiToggleUncheckedPreview() {
    AnkiDroidTheme {
        AnkiToggle(
            checked = false,
            onCheckedChange = {},
            interactionSource = remember { MutableInteractionSource() },
        )
    }
}

@Preview(name = "Disabled Checked")
@Composable
private fun AnkiToggleDisabledCheckedPreview() {
    AnkiDroidTheme {
        AnkiToggle(
            checked = true,
            onCheckedChange = null,
            interactionSource = remember { MutableInteractionSource() },
            enabled = false,
        )
    }
}
