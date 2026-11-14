// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025-2026 Colby Cabrera <colbycabrera.wd@gmail.com>

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
    @DrawableRes val icon: Int,
    @StringRes val labelResId: Int,
) {
    Decks(R.drawable.list_24px, R.string.decks),
    CardBrowser(
        R.drawable.cards_stack_24px,
        R.string.browser,
    ),
    Statistics(R.drawable.bar_chart_24px, R.string.statistics),
    Settings(
        R.drawable.settings_24px,
        R.string.settings,
    ),
    Help(
        R.drawable.help_filled_24px,
        R.string.help,
    ),
    Support(R.drawable.volunteer_activism_filled_24px, R.string.contribute_screen_title),
}

@Composable
fun AnkiNavigationRail(
    selectedItem: AppNavigationItem,
    onNavigate: (AppNavigationItem) -> Unit,
) {
    NavigationRail {
        Column(
            modifier =
                Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxHeight(),
            verticalArrangement =
                Arrangement.spacedBy(
                    12.dp,
                    alignment = Alignment.CenterVertically,
                ),
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
