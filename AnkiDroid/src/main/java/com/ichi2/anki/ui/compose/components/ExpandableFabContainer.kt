// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.ui.compose.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ichi2.anki.ui.compose.FabPaddingBottom
import com.ichi2.anki.ui.compose.FabPaddingEnd

@Composable
fun ExpandableFabContainer(content: @Composable () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(end = FabPaddingEnd, bottom = FabPaddingBottom),
        contentAlignment = Alignment.BottomEnd,
    ) {
        content()
    }
}
