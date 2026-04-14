package com.ichi2.anki.ui.compose.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnkiTopAppBar(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    titleText: String? = null,
    titleContent: @Composable () -> Unit = {
        titleText?.let {
            Text(
                text = it, style = MaterialTheme.typography.displayMediumEmphasized, maxLines = 1
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
