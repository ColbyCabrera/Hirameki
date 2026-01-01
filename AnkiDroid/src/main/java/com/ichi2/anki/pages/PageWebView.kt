/*
 *  Copyright (c) Colby Cabrera <colbycabrera.wd@gmail.com>
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
package com.ichi2.anki.pages

import android.annotation.SuppressLint
import android.view.View
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ichi2.anki.R
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import com.ichi2.themes.Themes
import timber.log.Timber

/**
 * A reusable Compose wrapper for displaying Anki HTML pages via WebView.
 *
 * This composable wraps a WebView using [AndroidView] and manages the local [AnkiServer]
 * lifecycle through [PageWebViewViewModel].
 *
 * @param path The page path to load (e.g., "graphs", "deck-options/123")
 * @param title The title to display in the top app bar, or null for no title
 * @param onNavigateUp Callback for back navigation
 * @param modifier Optional modifier for the composable
 * @param viewModel The ViewModel managing the AnkiServer lifecycle
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PageWebView(
    path: String,
    title: String?,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PageWebViewViewModel = viewModel(),
) {
    var isLoading by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            PageWebViewTopBar(
                title = title, onNavigateUp = onNavigateUp
            )
        }) { padding ->
        Box(
            modifier = modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            AndroidView(
                factory = { ctx ->
                WebView(ctx).apply {
                    with(settings) {
                        javaScriptEnabled = true
                        displayZoomControls = false
                        builtInZoomControls = true
                        setSupportZoom(true)
                    }
                    webViewClient = PageWebViewClient().apply {
                        onPageFinishedCallbacks.add { webView ->
                            isLoading = false
                            webView.visibility = View.VISIBLE
                        }
                    }
                    webChromeClient = PageChromeClient()
                    visibility = View.INVISIBLE
                }
            }, update = { webView ->
                val nightMode = if (Themes.currentTheme.isNightMode) "#night" else ""
                val url = "${viewModel.serverBaseUrl}$path$nightMode"
                if (webView.tag != url) {
                    webView.tag = url
                    Timber.i("PageWebView: Loading %s", url)
                    webView.loadUrl(url)
                }
            }, onRelease = { webView ->
                webView.stopLoading()
                webView.webViewClient = android.webkit.WebViewClient()
                webView.destroy()
            }, modifier = Modifier.fillMaxSize()
            )

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PageWebViewTopBar(
    title: String?,
    onNavigateUp: () -> Unit,
) {
    TopAppBar(title = {
        title?.let {
            Text(
                it,
                style = MaterialTheme.typography.displayMediumEmphasized,
            )
        }
    }, navigationIcon = {
        FilledIconButton(
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
    })
}

@Preview
@Composable
private fun PageWebViewTopBarPreview() {
    AnkiDroidTheme {
        PageWebViewTopBar(title = "Preview Title", onNavigateUp = {})
    }
}
