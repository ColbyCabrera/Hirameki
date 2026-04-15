package com.ichi2.anki.ui.compose.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnkiTopAppBar(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    titleText: String? = null,
    titleContent: @Composable () -> Unit = {
        titleText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.displayMediumEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    },
    actions: @Composable (RowScope.() -> Unit) = {},
) {
    TopAppBar(
        modifier = modifier, title = titleContent, navigationIcon = {
            FilledIconButton(
                modifier = Modifier.padding(end = 8.dp),
                onClick = onNavigateUp,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back_24px),
                    contentDescription = stringResource(R.string.back),
                )
            }
        }, actions = actions
    )
}

@Preview(name = "AnkiTopAppBar - Short Title", showBackground = true)
@Composable
private fun AnkiTopAppBarShortTitlePreview() {
    AnkiDroidTheme {
        AnkiTopAppBar(
            titleText = "AnkiDroid",
            onNavigateUp = {},
        )
    }
}

@Preview(name = "AnkiTopAppBar - Long Title", showBackground = true)
@Composable
private fun AnkiTopAppBarLongTitlePreview() {
    AnkiDroidTheme {
        AnkiTopAppBar(
            titleText = "This is a very long title that should be elided if it doesn't fit in the screen",
            onNavigateUp = {},
        )
    }
}

@Preview(name = "AnkiTopAppBar - With Actions", showBackground = true)
@Composable
private fun AnkiTopAppBarWithActionsPreview() {
    AnkiDroidTheme {
        AnkiTopAppBar(
            titleText = "AnkiDroid",
            onNavigateUp = {},
            actions = {
                IconButton(onClick = {}) {
                    Icon(
                        painter = painterResource(R.drawable.star_24px),
                        contentDescription = "Star",
                    )
                }
                IconButton(onClick = {}) {
                    Icon(
                        painter = painterResource(R.drawable.download_24px),
                        contentDescription = "Download",
                    )
                }
            }
        )
    }
}
