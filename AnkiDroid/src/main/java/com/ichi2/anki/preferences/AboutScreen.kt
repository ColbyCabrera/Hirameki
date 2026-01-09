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

package com.ichi2.anki.preferences

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.parseAsHtml
import com.ichi2.anki.R
import com.ichi2.anki.ui.compose.components.RoundedPolygonShape
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val SoftBurstShape = RoundedPolygonShape(MaterialShapes.SoftBurst)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AboutScreen(
    versionText: String,
    buildDateText: String,
    backendText: String,
    fsrsText: String,
    contributorsText: String,
    licenseText: String,
    onBackClick: () -> Unit,
    onCopyDebugClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "AboutIconRotation")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
        ),
        label = "AboutIconRotationAngle",
    )

    Scaffold(
        modifier = modifier, topBar = {
            TopAppBar(title = {
                Text(
                    text = stringResource(R.string.pref_cat_about_title),
                    style = MaterialTheme.typography.displayMediumEmphasized,
                )
            }, navigationIcon = {
                FilledIconButton(
                    modifier = Modifier.padding(end = 8.dp),
                    onClick = onBackClick,
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
            })
        }) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 64.dp, bottom = 24.dp)
                        .size(124.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                rotationZ = rotation
                            }
                            .background(
                                MaterialTheme.colorScheme.tertiaryContainer,
                                shape = SoftBurstShape,
                            ),
                    )
                    Image(
                        modifier = Modifier.size(60.dp),
                        painter = painterResource(R.drawable.info_24px),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onTertiaryContainer),
                    )
                }

                Text(
                    text = stringResource(
                        R.string.about_fork_of,
                        stringResource(R.string.app_name)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(24.dp))

                SectionTitle(stringResource(R.string.about_version))

                // Versions
                VersionText(versionText)
                VersionText(buildDateText)
                VersionText(backendText)

                if (fsrsText.isNotEmpty()) {
                    VersionText(fsrsText)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Contributors
                    SectionTitle(stringResource(R.string.contributors_title))
                    HtmlTextView(
                        text = contributorsText
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // License
                    SectionTitle(stringResource(R.string.license))
                    HtmlTextView(
                        text = licenseText
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Buttons
                TextButton(onClick = onCopyDebugClick) {
                    Text(stringResource(R.string.feedback_copy_debug))
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun VersionText(text: String) {
    if (text.isNotEmpty()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 2.dp)
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun HtmlTextView(text: String) {
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val textSizeSp = MaterialTheme.typography.bodyMedium.fontSize.value

    AndroidView(
        factory = { context ->
            TextView(context).apply {
                movementMethod = LinkMovementMethod.getInstance()
                setTextColor(textColor)
                textSize = textSizeSp
            }
        }, update = {
            it.text = text.parseAsHtml()
        }, modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Preview
@Composable
private fun AboutScreenPreview() {
    AnkiDroidTheme {
        AboutScreen(
            versionText = "2.x",
            buildDateText = "13 Apr 2023",
            backendText = "(anki 23.10.1 / ...)",
            fsrsText = "(FSRS 0.6.4)",
            contributorsText = "Contributors...",
            licenseText = "License...",
            onBackClick = {},
            onCopyDebugClick = {})
    }
}
