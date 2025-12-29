/****************************************************************************************
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2009 Casey Link <unnamedrambler@gmail.com>                             *
 * Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>                          *
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
package com.ichi2.anki.deckpicker.compose

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.motionScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R
import com.ichi2.anki.deckpicker.DisplayDeckNode
import com.ichi2.anki.ui.compose.components.RoundedPolygonShape

private val expandedDeckCardRadius = 14.dp
private val collapsedDeckCardRadius = 70.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val CloverShape = RoundedPolygonShape(MaterialShapes.Clover4Leaf)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val GhostishShape = RoundedPolygonShape(MaterialShapes.Ghostish)

@OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
@Composable
fun DeckItem(
    deck: DisplayDeckNode,
    modifier: Modifier = Modifier,
    onDeckClick: (DisplayDeckNode) -> Unit,
    onExpandClick: (DisplayDeckNode) -> Unit,
    onDeckOptions: (DisplayDeckNode) -> Unit,
    onRename: (DisplayDeckNode) -> Unit,
    onExport: (DisplayDeckNode) -> Unit,
    onDelete: (DisplayDeckNode) -> Unit,
    onRebuild: (DisplayDeckNode) -> Unit,
    onEmpty: (DisplayDeckNode) -> Unit,
) {
    var isContextMenuOpen by remember { mutableStateOf(false) }

    val cornerRadius by animateDpAsState(
        targetValue = if (!deck.collapsed && deck.canCollapse) expandedDeckCardRadius else collapsedDeckCardRadius,
        animationSpec = motionScheme.defaultEffectsSpec()
    )

    val content = @Composable {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (deck.depth == 0) {
                        Modifier.clip(RoundedCornerShape(cornerRadius))
                    } else {
                        Modifier
                    }
                )
                .combinedClickable(
                    onClick = { onDeckClick(deck) },
                    onLongClick = { isContextMenuOpen = true })
                .padding(horizontal = 8.dp, vertical = if (deck.depth > 0) 4.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Add space between the edge of the deck for the circle shape
            if (deck.depth > 0) {
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = deck.lastDeckNameComponent,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                style = if (deck.depth == 0) MaterialTheme.typography.titleLargeEmphasized else MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier
                    .height(70.dp)
                    .padding(start = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CardCountsContainer(
                    cardCount = deck.newCount,
                    contentDescription = "${stringResource(R.string.total_new)}: ${deck.newCount}",
                    shape = CloverShape,
                    containerColor = MaterialTheme.colorScheme.secondaryFixedDim,
                )

                CardCountsContainer(
                    cardCount = deck.revCount,
                    contentDescription = "${stringResource(R.string.review)}: ${deck.revCount}",
                    shape = GhostishShape,
                    containerColor = MaterialTheme.colorScheme.secondary,
                )
            }


            if (deck.canCollapse) {
                IconButton(
                    onClick = { onExpandClick(deck) },
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(36.dp)
                ) {
                    Icon(
                        painter = painterResource(
                            if (deck.collapsed) R.drawable.ic_expand_more_black_24dp else R.drawable.ic_expand_less_black_24dp,
                        ),
                        contentDescription = if (deck.collapsed) stringResource(R.string.expand) else stringResource(
                            R.string.collapse
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(44.dp))
            }
            DropdownMenu(
                expanded = isContextMenuOpen,
                onDismissRequest = { isContextMenuOpen = false },
                shape = MaterialTheme.shapes.large
            ) {
                if (deck.filtered) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rebuild_cram_label)) },
                        onClick = {
                            onRebuild(deck)
                            isContextMenuOpen = false
                        },
                        leadingIcon = {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                        })
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.empty_cram_label)) },
                        onClick = {
                            onEmpty(deck)
                            isContextMenuOpen = false
                        },
                        leadingIcon = {
                            Icon(Icons.Filled.Close, contentDescription = null)
                        })
                } else {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rename_deck)) },
                        onClick = {
                            onRename(deck)
                            isContextMenuOpen = false
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.edit_24px),
                                contentDescription = null
                            )
                        })
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.export_deck)) },
                        onClick = {
                            onExport(deck)
                            isContextMenuOpen = false
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.share_24px),
                                contentDescription = null
                            )
                        })
                }
                DropdownMenuItem(text = { Text(stringResource(R.string.deck_options)) }, onClick = {
                    onDeckOptions(deck)
                    isContextMenuOpen = false
                }, leadingIcon = {
                    Icon(painter = painterResource(R.drawable.tune_24px), contentDescription = null)
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.contextmenu_deckpicker_delete_deck)) }, onClick = {
                    onDelete(deck)
                    isContextMenuOpen = false
                }, leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.delete_24px), contentDescription = null
                    )
                })
            }
        }
    }


    when (deck.depth) {
        0 -> {
            content()
        }

        1 -> {
            Card(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                shape = RoundedCornerShape(cornerRadius),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                content()
            }
        }

        else -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 2.dp)
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                content()
            }
        }
    }
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CardCountsContainer(
    cardCount: Int,
    contentDescription: String,
    shape: Shape,
    containerColor: Color = MaterialTheme.colorScheme.secondary,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(shape)
            .background(containerColor)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
            }, contentAlignment = Alignment.Center
    ) {
        Text(
            text = cardCount.toString(),
            color = MaterialTheme.colorScheme.onSecondary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .padding(0.dp)
                .basicMarquee()
        )
    }
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
fun CardCountsContainerPreview() {
    CardCountsContainer(
        cardCount = 10, contentDescription = "New: 10", shape = CloverShape
    )
}
