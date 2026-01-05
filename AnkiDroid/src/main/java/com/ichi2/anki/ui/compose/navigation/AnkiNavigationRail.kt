/*
 * Copyright (c) 2025 Colby Cabrera <colbycabrera.wd@gmail.com>
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

package com.ichi2.anki.ui.compose.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R

enum class AppNavigationItem(
    @DrawableRes val icon: Int, @StringRes val labelResId: Int
) {
    Decks(R.drawable.list_24px, R.string.decks), CardBrowser(
        R.drawable.cards_stack_24px,
        R.string.browser
    ),
    Statistics(R.drawable.bar_chart_24px, R.string.statistics), Settings(
        R.drawable.settings_24px,
        R.string.settings
    ),
    Help(
        R.drawable.help_filled_24px,
        R.string.help
    ),
    Support(R.drawable.volunteer_activism_filled_24px, R.string.donate)
}

@Composable
fun AnkiNavigationRail(
    selectedItem: AppNavigationItem, onNavigate: (AppNavigationItem) -> Unit
) {
    NavigationRail {
        Column(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(
                12.dp,
                alignment = Alignment.CenterVertically
            )
        ) {
            AppNavigationItem.entries.forEach { item ->
                NavigationRailItem(
                    icon = {
                        Icon(
                            painter = painterResource(item.icon),
                            contentDescription = stringResource(item.labelResId),
                        )
                    },
                    label = { Text(stringResource(item.labelResId)) },
                    selected = selectedItem == item,
                    onClick = {
                        onNavigate(item)
                    },
                )
            }
        }
    }
}
