/*
 *  Copyright (c) Colby Cabrera <colbycabrera.wd@gmail.com>
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

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ichi2.anki.R

/**
 * Compose wrapper for the Card Info page.
 *
 * Uses [PageWebView] to display card information and statistics.
 *
 * @param cardId The ID of the card to display info for
 * @param onNavigateUp Callback for back navigation
 */
@Composable
fun CardInfoScreen(
    cardId: Long,
    onNavigateUp: () -> Unit,
) {
    PageWebView(
        path = "card-info/$cardId",
        title = stringResource(R.string.card_info_title),
        onNavigateUp = onNavigateUp,
    )
}
