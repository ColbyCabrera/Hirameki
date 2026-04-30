/* **************************************************************************************
 * Copyright (c) 2025 AnkiDroid Open Source Team                                       *
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
package com.ichi2.anki.notetype.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.motionScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R
import com.ichi2.anki.notetype.ManageNoteTypeUiModel
import com.ichi2.anki.ui.compose.components.MorphingCardCount
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val MORPHING_SHAPES = listOf(
    MaterialShapes.Circle,
    MaterialShapes.Pill,
    MaterialShapes.SoftBurst,
    MaterialShapes.Pentagon,
    MaterialShapes.Sunny,
    MaterialShapes.Square,
    MaterialShapes.Slanted,
    MaterialShapes.Arch,
    MaterialShapes.Cookie4Sided,
    MaterialShapes.Cookie6Sided,
    MaterialShapes.Cookie7Sided,
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Cookie12Sided,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.Clover8Leaf,
    MaterialShapes.SoftBoom,
    MaterialShapes.Ghostish,
    MaterialShapes.Puffy,
    MaterialShapes.Flower
)

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class
)
@Composable
fun NoteTypeItem(
    noteType: ManageNoteTypeUiModel,
    onClick: () -> Unit,
    isSelected: Boolean = false,
    isInMultiSelectMode: Boolean = false,
    onLongClick: () -> Unit = {},
) {
    val animatedContainerColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }, animationSpec = motionScheme.defaultEffectsSpec(), label = "containerColor"
    )

    val animatedCountsContainerColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.tertiaryContainer
        }, animationSpec = motionScheme.defaultEffectsSpec(), label = "countsContainerColor"
    )

    val animatedCountsContentColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onTertiaryContainer
        }, animationSpec = motionScheme.defaultEffectsSpec(), label = "countsContentColor"
    )


    val animatedCornerRadius by animateDpAsState(
        targetValue = if (isSelected) 48.dp else 24.dp,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "cornerRadius"
    )

    Surface(
        shape = RoundedCornerShape(animatedCornerRadius),
        color = animatedContainerColor,
    ) {
        ListItem(
            modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
        ), headlineContent = {
            Text(
                text = noteType.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }, supportingContent = {
            Text(
                text = pluralStringResource(
                    R.plurals.model_browser_of_type, noteType.useCount, noteType.useCount
                ), style = MaterialTheme.typography.bodySmall
            )
        }, leadingContent = {
            MorphingCardCount(
                cardCount = noteType.useCount,
                containerColor = animatedCountsContainerColor,
                contentColor = animatedCountsContentColor,
                shapes = MORPHING_SHAPES
            )
        }, trailingContent = {
            if (isInMultiSelectMode) {
                Checkbox(
                    modifier = Modifier.padding(horizontal = 15.dp), // match total width of IconButton
                    checked = isSelected,
                    onCheckedChange = null, // handled by row click
                )
            } else {
                IconButton(
                    onClick = onClick,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(id = R.string.more_options),
                    )
                }
            }
        }, colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        )
        )
    }
}

@Preview
@Composable
fun NoteTypeItemPreview() {
    AnkiDroidTheme {
        NoteTypeItem(
            noteType = ManageNoteTypeUiModel(0, "Basic", 10), onClick = {})
    }
}

@Preview
@Composable
fun NoteTypeItemSelectedPreview() {
    AnkiDroidTheme {
        NoteTypeItem(
            noteType = ManageNoteTypeUiModel(0, "Basic", 10),
            onClick = {},
            isSelected = true,
            isInMultiSelectMode = true,
        )
    }
}
