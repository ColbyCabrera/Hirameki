package com.ichi2.anki.ui.compose.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarDefaults.InputField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme

/**
 * A shared search bar component that provides a consistent look and feel across the app.
 *
 * @param query The current search query.
 * @param onQueryChange Called when the query changes.
 * @param onSearch Called when the search is submitted.
 * @param onActiveChange Called when the search bar becomes active or inactive.
 * @param placeholder The placeholder text to display.
 * @param focusRequester The focus requester for the search bar.
 * @param modifier The modifier for the search bar.
 * @param searchAnim The animation progress (0f to 1f) for opening/closing transitions.
 * @param containerColor The background color of the search bar container.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnkiSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onActiveChange: (Boolean) -> Unit,
    placeholder: String,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    searchAnim: Float = 1f,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    val density = LocalDensity.current
    val searchOffsetPx = with(density) { (-8).dp.toPx() }

    ProvideTextStyle(MaterialTheme.typography.bodyLarge) {
        SearchBar(
            inputField = {
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }
                InputField(
                    query = query,
                    onQueryChange = onQueryChange,
                    onSearch = onSearch,
                    expanded = true,
                    onExpandedChange = { },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .graphicsLayer {
                            alpha = searchAnim
                            translationY = searchOffsetPx * (1f - searchAnim)
                            scaleX = 0.98f + 0.02f * searchAnim
                            scaleY = 0.98f + 0.02f * searchAnim
                        },
                    placeholder = { Text(placeholder) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.search_24px),
                            contentDescription = placeholder,
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = {
                            onQueryChange("")
                            onActiveChange(false)
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.close_24px),
                                contentDescription = stringResource(R.string.close),
                            )
                        }
                    },
                )
            },
            expanded = false,
            onExpandedChange = { },
            modifier = modifier.graphicsLayer {
                alpha = searchAnim
            },
            shape = SearchBarDefaults.inputFieldShape,
            colors = SearchBarDefaults.colors(
                containerColor = containerColor,
            ),
            content = { },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AnkiSearchBarPreview() {
    AnkiDroidTheme {
        AnkiSearchBar(
            query = "Search query",
            onQueryChange = {},
            onSearch = {},
            onActiveChange = {},
            placeholder = "Search...",
            focusRequester = remember { FocusRequester() })
    }
}
