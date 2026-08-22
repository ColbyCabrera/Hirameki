/****************************************************************************************
 * Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>                         *
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
package com.ichi2.anki.ui.compose.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import kotlinx.coroutines.android.awaitFrame

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
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(focusRequester) {
        awaitFrame()
        try {
            focusRequester.requestFocus()
            keyboardController?.show()
        } catch (_: Exception) {
            // Ignore if layout is in the middle of teardown
        }
    }

    Surface(
        shape = SearchBarDefaults.inputFieldShape,
        color = containerColor,
        modifier = modifier.graphicsLayer {
            alpha = searchAnim
            translationY = searchOffsetPx * (1f - searchAnim)
            scaleX = 0.98f + 0.02f * searchAnim
            scaleY = 0.98f + 0.02f * searchAnim
        },
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_field")
                .focusRequester(focusRequester),
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
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    onActiveChange(false)
                }) {
                    Icon(
                        painter = painterResource(R.drawable.close_24px),
                        contentDescription = stringResource(R.string.close),
                    )
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                keyboardController?.hide()
                focusManager.clearFocus()
                onSearch(query)
            }),
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
            focusRequester = remember { FocusRequester() },
        )
    }
}
