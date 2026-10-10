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

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * how every tap inside a modal bottom sheet closes it: runs [action], slides the sheet out, and only then calls
 * [onHidden], which is where the caller drops the sheet from composition.
 *
 * a `ModalBottomSheet` only animates out while it stays composed, so a row that clears the caller's show flag
 * itself (or a caller callback that does) removes the sheet on the next frame: it slides in and vanishes. the
 * flag has to wait for the hide, which is also what leaves the state at hidden for the next open.
 *
 * the rows stay clickable while the sheet slides out, so a second click in that window (a quick double tap, an
 * accessibility click) is dropped: a toggle such as suspend or bury would otherwise run twice.
 */
@OptIn(ExperimentalMaterial3Api::class)
fun SheetState.hideAfter(
    scope: CoroutineScope,
    onHidden: () -> Unit,
    action: () -> Unit,
) {
    if (targetValue == SheetValue.Hidden) return
    action()
    // a hide cut short (the sheet dragged back up mid-exit) leaves the sheet on screen, so the flag is only cleared
    // once it really is hidden
    scope.launch { hide() }.invokeOnCompletion { if (!isVisible) onHidden() }
}
