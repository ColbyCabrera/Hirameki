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

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R
import com.ichi2.anki.notetype.ManageNoteTypeUiModel
import com.ichi2.anki.ui.compose.components.MorphingCardCount
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NoteTypeActionBottomSheet(
    noteType: ManageNoteTypeUiModel,
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    onShowFields: () -> Unit,
    onEditCards: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        NoteTypeActionBottomSheetContent(
            noteType = noteType,
            onDismissRequest = onDismissRequest,
            onShowFields = onShowFields,
            onEditCards = onEditCards,
            onRename = onRename,
            onDelete = onDelete
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NoteTypeActionBottomSheetContent(
    noteType: ManageNoteTypeUiModel,
    onDismissRequest: () -> Unit,
    onShowFields: () -> Unit,
    onEditCards: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val firstRowButtonHeight = ButtonDefaults.LargeContainerHeight
    val secondRowButtonHeight = ButtonDefaults.MediumContainerHeight

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Contextual Preview
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            shape = MaterialTheme.shapes.extraExtraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MorphingCardCount(
                    cardCount = noteType.useCount,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Column {
                    Text(
                        modifier = Modifier.basicMarquee(),
                        text = noteType.name,
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        maxLines = 1
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.model_browser_of_type, noteType.useCount, noteType.useCount
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionItem(
                modifier = Modifier.weight(1f),
                icon = painterResource(R.drawable.cards_stack_24px),
                label = null,
                contentDescription = stringResource(id = R.string.cards),
                height = firstRowButtonHeight,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                onClick = {
                    onEditCards()
                    onDismissRequest()
                })
            ActionItem(
                icon = painterResource(R.drawable.list_24px),
                label = stringResource(id = R.string.fields),
                height = firstRowButtonHeight,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                onClick = {
                    onShowFields()
                    onDismissRequest()
                })

        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionItem(
                modifier = Modifier.weight(1f),
                icon = painterResource(R.drawable.edit_24px),
                label = stringResource(id = R.string.rename),
                height = secondRowButtonHeight,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ),
                onClick = {
                    onRename()
                    onDismissRequest()
                })
            ActionItem(
                modifier = Modifier.weight(1f),
                icon = painterResource(R.drawable.delete_24px),
                height = secondRowButtonHeight,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                label = null,
                contentDescription = stringResource(id = R.string.model_browser_delete),
                onClick = {
                    onDelete()
                    onDismissRequest()
                })
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ActionItem(
    icon: Painter,
    label: String?,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    contentDescription: String? = label,
) {
    val showTooltip = label == null && contentDescription != null

    val buttonContent = @Composable {
        Icon(
            painter = icon,
            contentDescription = if (label == null) contentDescription else null,
            modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
        )

        if (label != null) {
            Spacer(modifier = Modifier.width(ButtonDefaults.iconSpacingFor(height)))
            Text(
                text = label,
                style = ButtonDefaults.textStyleFor(height),
            )
        }
    }

    if (showTooltip) {
        val tooltipState = rememberTooltipState()
        DisposableEffect(Unit) {
            onDispose {
                tooltipState.dismiss()
            }
        }
        Box(modifier = modifier) {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                    positioning = TooltipAnchorPosition.Above
                ),
                tooltip = { PlainTooltip { Text(contentDescription) } },
                state = tooltipState,
            ) {
                FilledTonalButton(
                    onClick = onClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(height),
                    shapes = ButtonDefaults.shapesFor(height),
                    colors = colors,
                    contentPadding = ButtonDefaults.contentPaddingFor(height),
                    content = { buttonContent() })
            }
        }
    } else {
        FilledTonalButton(
            onClick = onClick,
            modifier = modifier.height(height),
            shapes = ButtonDefaults.shapesFor(height),
            colors = colors,
            contentPadding = ButtonDefaults.contentPaddingFor(height),
            content = { buttonContent() })
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
fun NoteTypeActionBottomSheetContentPreview() {
    AnkiDroidTheme {
        Surface {
            NoteTypeActionBottomSheetContent(
                noteType = ManageNoteTypeUiModel(1L, "Basic", 42),
                onDismissRequest = {},
                onShowFields = {},
                onEditCards = {},
                onRename = {},
                onDelete = {})
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
fun NoteTypeActionBottomSheetPreview() {
    AnkiDroidTheme {
        NoteTypeActionBottomSheet(
            noteType = ManageNoteTypeUiModel(1L, "Basic", 42),
            sheetState = rememberModalBottomSheetState(),
            onDismissRequest = {},
            onShowFields = {},
            onEditCards = {},
            onRename = {},
            onDelete = {})
    }
}
