// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
// SPDX-FileCopyrightText: Copyright (c) 2025-2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.ui.windows.reviewer.whiteboard.compose

import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R
import com.ichi2.anki.ui.windows.reviewer.whiteboard.BrushInfo
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ColorBrushButton(
    brush: BrushInfo,
    isSelected: Boolean,
    onClick: (View) -> Unit,
    onLongClick: () -> Unit,
    colorNormal: Color,
    colorHighlight: Color,
    minTouchTargetSize: Dp = 48.dp,
) {
    val view = LocalView.current
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val brushContentDescription =
        stringResource(
            if (isSelected) {
                R.string.brush_content_description_selected
            } else {
                R.string.brush_content_description
            },
            brush.width.roundToInt(),
        )

    Box(
        modifier =
            Modifier
                .requiredSize(minTouchTargetSize)
                .clip(RoundedCornerShape(100))
                .background(backgroundColor)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = brushContentDescription
                }.combinedClickable(
                    onClick = { onClick(view) },
                    onLongClick = onLongClick,
                ).padding(4.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(4.dp),
            shape = CircleShape,
            color = Color(brush.color),
            border =
                BorderStroke(
                    width = 2.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                ),
        ) { }
        Box(
            modifier =
                Modifier
                    .clip(MaterialTheme.shapes.extraExtraLarge)
                    .background(MaterialTheme.colorScheme.tertiary),
        ) {
            Text(
                modifier = Modifier.padding(vertical = 0.dp, horizontal = 4.dp),
                text = brush.width.roundToInt().toString(),
                color = MaterialTheme.colorScheme.onTertiary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
fun AddBrushButton(
    onClick: () -> Unit,
    colorNormal: Color,
    tooltip: String,
    minTouchTargetSize: Dp = 48.dp,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .requiredSize(minTouchTargetSize)
                .clip(RoundedCornerShape(100))
                .clickable(
                    onClick = onClick,
                ).semantics { contentDescription = tooltip },
    ) {
        Icon(
            painter = painterResource(id = R.drawable.add_24px),
            contentDescription = null,
            tint = colorNormal,
        )
    }
}

@Preview
@Composable
fun PreviewColorBrushButton() {
    Column {
        ColorBrushButton(
            brush = BrushInfo(android.graphics.Color.RED, 12f),
            isSelected = false,
            onClick = {},
            onLongClick = {},
            colorNormal = Color.Black,
            colorHighlight = Color.LightGray,
        )
        ColorBrushButton(
            brush = BrushInfo(android.graphics.Color.BLUE, 25f),
            isSelected = true,
            onClick = {},
            onLongClick = {},
            colorNormal = Color.Black,
            colorHighlight = Color.LightGray,
        )
    }
}
