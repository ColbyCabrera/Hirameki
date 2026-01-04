/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ColorBrushButton(
    brush: BrushInfo,
    isSelected: Boolean,
    onClick: (View) -> Unit,
    onLongClick: () -> Unit,
    colorNormal: Color,
    colorHighlight: Color,
    minTouchTargetSize: Dp = 48.dp
) {
    val view = LocalView.current
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val brushContentDescription = stringResource(
        if (isSelected) R.string.brush_content_description_selected
        else R.string.brush_content_description, brush.width.roundToInt()
    )

    Box(
        modifier = Modifier
            .requiredSize(minTouchTargetSize)
            .clip(RoundedCornerShape(100))
            .background(backgroundColor)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = brushContentDescription
            }
            .combinedClickable(
                onClick = { onClick(view) }, onLongClick = onLongClick
            )
            .padding(4.dp), contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            shape = CircleShape,
            color = Color(brush.color),
            border = BorderStroke(
                width = 2.dp,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
        ) { }
        Box(
            modifier = Modifier
                .clip(MaterialTheme.shapes.extraExtraLarge)
                .background(MaterialTheme.colorScheme.tertiary),
        ) {
            Text(
                modifier = Modifier.padding(vertical = 0.dp, horizontal = 4.dp),
                text = brush.width.roundToInt().toString(),
                color = MaterialTheme.colorScheme.onTertiary,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
fun AddBrushButton(
    onClick: () -> Unit, colorNormal: Color, tooltip: String, minTouchTargetSize: Dp = 48.dp
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .requiredSize(minTouchTargetSize)
            .clip(RoundedCornerShape(100))
            .clickable(
                onClick = onClick
            )
            .semantics { contentDescription = tooltip }) {
        Icon(
            painter = painterResource(id = R.drawable.add_24px),
            contentDescription = null,
            tint = colorNormal
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
            colorHighlight = Color.LightGray
        )
        ColorBrushButton(
            brush = BrushInfo(android.graphics.Color.BLUE, 25f),
            isSelected = true,
            onClick = {},
            onLongClick = {},
            colorNormal = Color.Black,
            colorHighlight = Color.LightGray
        )
    }
}
