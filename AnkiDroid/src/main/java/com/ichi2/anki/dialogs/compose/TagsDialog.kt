/* **************************************************************************************
 * Copyright (c) 2009 Andrew Dubya <andrewdubya@gmail.com>                              *
 * Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2009 Daniel Svard <daniel.svard@gmail.com>                             *
 * Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>
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
package com.ichi2.anki.dialogs.compose

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R
import java.util.Locale

sealed interface TagsState {
    object Loading : TagsState
    data class Loaded(val tags: List<String>) : TagsState
}

/**
 * Normalizes a tag by trimming whitespace, collapsing internal whitespace,
 * and converting to lowercase for consistent comparison.
 */
private fun normalizeTag(tag: String): String {
    // Trim leading/trailing whitespace, split on any whitespace runs, filter out
    // any empty parts (defensive), then rejoin with single spaces and lowercase
    // to mirror backend tag splitting and duplicate detection.
    return tag.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
        .lowercase(Locale.getDefault())
}

private fun isDuplicateTag(
    tag: String, existingTags: List<String>, selectedTags: Set<String>
): Boolean {
    val normalized = normalizeTag(tag)
    val normalizedExisting = existingTags.map { normalizeTag(it) }
    val normalizedSelection = selectedTags.map { normalizeTag(it) }
    return normalized in normalizedExisting || normalized in normalizedSelection
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TagsDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (checked: Set<String>, indeterminate: Set<String>) -> Unit,
    allTags: TagsState,
    initialSelection: Set<String>,
    initialIndeterminate: Set<String> = emptySet(),
    deckTags: Set<String> = emptySet(),
    initialFilterByDeck: Boolean = false,
    onFilterByDeckChanged: (Boolean) -> Unit = {},
    title: String,
    confirmButtonText: String,
    showFilterByDeckToggle: Boolean = false,
    onAddTag: (String) -> Unit
) {
    var checkedTags by remember(initialSelection) { mutableStateOf(initialSelection) }
    var indeterminateTags by remember(initialIndeterminate) { mutableStateOf(initialIndeterminate) }
    var searchQuery by remember { mutableStateOf("") }
    var isToggleChecked by remember(initialFilterByDeck) { mutableStateOf(initialFilterByDeck) }

    val addNewTag = {
        val newTag = searchQuery.trim()
        if (newTag.isNotEmpty()) {
            val existingTagsList = when (allTags) {
                is TagsState.Loaded -> allTags.tags
                else -> emptyList()
            }
            // Check if the normalized tag is not already present in existing tags or current selection
            if (!isDuplicateTag(newTag, existingTagsList, checkedTags + indeterminateTags)) {
                onAddTag(newTag)
                checkedTags = checkedTags + newTag
                // If it was indeterminate (unlikely for new tag), remove it
                indeterminateTags = indeterminateTags - newTag
            }
            searchQuery = ""
        }
    }

    AlertDialog(onDismissRequest = onDismissRequest, title = { Text(text = title) }, text = {
        when (allTags) {
            is TagsState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center
                ) {
                    CircularWavyProgressIndicator(modifier = Modifier.padding(vertical = 32.dp))
                }
            }

            is TagsState.Loaded -> {
                Column {
                    SearchBarRow(
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        isToggleChecked = isToggleChecked,
                        onToggleCheckedChange = {
                            isToggleChecked = it
                            onFilterByDeckChanged(it)
                        },
                        showFilterByDeckToggle = showFilterByDeckToggle,
                        onDone = addNewTag
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.large
                    ) {
                        val filteredTags by remember(
                            allTags, searchQuery, isToggleChecked, deckTags
                        ) {
                            derivedStateOf {
                                allTags.tags.filter {
                                    it.contains(
                                        other = searchQuery, ignoreCase = true
                                    ) && (!isToggleChecked || it in deckTags)
                                }
                            }
                        }
                        val potentialNewTag by remember(
                            searchQuery,
                            allTags,
                            filteredTags,
                            checkedTags,
                            indeterminateTags
                        ) {
                            derivedStateOf {
                                val trimmedQuery = searchQuery.trim()
                                if (trimmedQuery.isEmpty()) {
                                    null
                                } else {
                                    val existingTagsList = allTags.tags
                                    // Show potential new tag only if it doesn't exist in all tags or selection
                                    trimmedQuery.takeIf {
                                        !isDuplicateTag(
                                            trimmedQuery,
                                            existingTagsList,
                                            checkedTags + indeterminateTags
                                        )
                                    }
                                }
                            }
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(vertical = 16.dp)
                        ) {
                            item {
                                if (filteredTags.isEmpty() && potentialNewTag == null) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stringResource(R.string.card_browser_no_tags_found),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        potentialNewTag?.let { newTag ->
                                            FilterChip(
                                                modifier = Modifier.height(
                                                    FilterChipDefaults.Height
                                                ),
                                                selected = false,
                                                onClick = addNewTag,
                                                label = { Text(text = newTag) },
                                                leadingIcon = {
                                                    Icon(
                                                        painter = painterResource(R.drawable.add_24px),
                                                        contentDescription = stringResource(R.string.add_tag),
                                                        modifier = Modifier.size(
                                                            FilterChipDefaults.IconSize
                                                        )
                                                    )
                                                })
                                        }
                                        filteredTags.forEach { tag ->
                                            TagFilterChip(
                                                tag = tag,
                                                isSelected = tag in checkedTags,
                                                isIndeterminate = tag in indeterminateTags,
                                                onClick = {
                                                    when (tag) {
                                                        in indeterminateTags -> {
                                                            // Indeterminate -> Checked
                                                            indeterminateTags =
                                                                indeterminateTags - tag
                                                            checkedTags = checkedTags + tag
                                                        }

                                                        in checkedTags -> {
                                                            // Checked -> Unchecked
                                                            checkedTags = checkedTags - tag
                                                        }

                                                        else -> {
                                                            // Unchecked -> Checked
                                                            checkedTags = checkedTags + tag
                                                        }
                                                    }
                                                })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }, confirmButton = {
        TextButton(onClick = { onConfirm(checkedTags, indeterminateTags) }) {
            Text(text = confirmButtonText)
        }
    }, dismissButton = {
        TextButton(onClick = onDismissRequest) {
            Text(text = stringResource(id = R.string.dialog_cancel))
        }
    })
}

@Composable
private fun TagFilterChip(
    tag: String,
    isSelected: Boolean,
    isIndeterminate: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedCornerRadius by animateDpAsState(
        targetValue = if (isSelected || isIndeterminate) 24.dp else 8.dp,
        label = "corner radius",
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow
        )
    )


    // We manually adjust the icon size by -0.2.dp for the selected state to prevent a slight layout shift.
    // The unselected state uses half-size spacers on both sides for visual balance, while the selected state
    // uses a full-size leading icon. This structure caused a tiny width discrepancy that -0.2.dp corrects.
    FilterChip(
        modifier = modifier.height(FilterChipDefaults.Height),
        selected = isSelected || isIndeterminate,
        onClick = onClick,
        label = { Text(text = tag) },
        leadingIcon = {
            if (isSelected) {
                Icon(
                    painter = painterResource(R.drawable.check_24px),
                    contentDescription = stringResource(R.string.done_icon),
                    modifier = Modifier.size(FilterChipDefaults.IconSize - 0.2.dp)
                )
            } else if (isIndeterminate) {
                Icon(
                    painter = painterResource(R.drawable.remove_24px),
                    contentDescription = stringResource(R.string.tag_indeterminate_state),
                    modifier = Modifier.size(FilterChipDefaults.IconSize - 0.2.dp)
                )
            } else {
                Spacer(Modifier.size(FilterChipDefaults.IconSize / 2))
            }
        },
        shape = RoundedCornerShape(animatedCornerRadius),
        trailingIcon = {
            if (!isSelected && !isIndeterminate) {
                Spacer(Modifier.size(FilterChipDefaults.IconSize / 2))
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.tertiary,
            selectedLabelColor = MaterialTheme.colorScheme.onTertiary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onTertiary
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchBarRow(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isToggleChecked: Boolean,
    onToggleCheckedChange: (Boolean) -> Unit,
    showFilterByDeckToggle: Boolean,
    modifier: Modifier = Modifier,
    onDone: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLargeIncreased,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text(text = stringResource(id = R.string.card_browser_search_tags_hint)) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.search_24px),
                        contentDescription = stringResource(R.string.card_browser_search_hint)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                painter = painterResource(R.drawable.close_24px),
                                contentDescription = stringResource(R.string.close)
                            )
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = Modifier.weight(1f)
            )

            if (showFilterByDeckToggle) {
                val tooltipContent = if (isToggleChecked) {
                    stringResource(R.string.card_browser_filter_tags_by_deck_on_description)
                } else {
                    stringResource(R.string.card_browser_filter_tags_by_deck_off_description)
                }
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        TooltipAnchorPosition.Above
                    ), tooltip = {
                        PlainTooltip {
                            Text(tooltipContent)
                        }
                    }, state = rememberTooltipState()
                ) {
                    FilledTonalIconToggleButton(
                        checked = isToggleChecked,
                        onCheckedChange = onToggleCheckedChange,
                        shapes = IconButtonDefaults.toggleableShapes()
                    ) {
                        Icon(
                            painter = painterResource(
                                if (isToggleChecked) R.drawable.filter_alt_24px else R.drawable.filter_alt_off_24px
                            ), contentDescription = tooltipContent
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TagsDialogPreview() {
    MaterialTheme {
        TagsDialog(
            onDismissRequest = {},
            onConfirm = { _, _ -> },
            allTags = TagsState.Loaded(listOf("tag1", "tag2", "very long tag", "another")),
            initialSelection = setOf("tag1"),
            title = "Filter by Tags",
            confirmButtonText = "OK",
            showFilterByDeckToggle = true,
            onAddTag = {})
    }
}

@Preview(showBackground = true, name = "Indeterminate")
@Composable
private fun TagsDialogIndeterminatePreview() {
    MaterialTheme {
        TagsDialog(
            onDismissRequest = {},
            onConfirm = { _, _ -> },
            allTags = TagsState.Loaded(listOf("tag1", "tag2", "tag3", "tag4")),
            initialSelection = setOf("tag1"),
            initialIndeterminate = setOf("tag3"),
            title = "Filter by Tags",
            confirmButtonText = "OK",
            showFilterByDeckToggle = true,
            onAddTag = {})
    }
}

@Preview(showBackground = true, name = "Loading")
@Composable
private fun TagsDialogLoadingPreview() {
    MaterialTheme {
        TagsDialog(
            onDismissRequest = {},
            onConfirm = { _, _ -> },
            allTags = TagsState.Loading,
            initialSelection = emptySet(),
            title = "Filter by Tags",
            confirmButtonText = "OK",
            showFilterByDeckToggle = true,
            onAddTag = {})
    }
}

@Preview(showBackground = true, name = "Empty")
@Composable
private fun TagsDialogEmptyPreview() {
    MaterialTheme {
        TagsDialog(
            onDismissRequest = {},
            onConfirm = { _, _ -> },
            allTags = TagsState.Loaded(emptyList()),
            initialSelection = emptySet(),
            title = "Filter by Tags",
            confirmButtonText = "OK",
            showFilterByDeckToggle = true,
            onAddTag = {})
    }
}
