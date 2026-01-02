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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularWavyProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ichi2.anki.R
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import com.ichi2.themes.Themes
import kotlinx.coroutines.flow.Flow
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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PageWebView(
    path: String,
    title: String?,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PageWebViewViewModel = viewModel(),
    jsCommands: Flow<String>? = null, // NEW
    topBarActions: @Composable (RowScope.() -> Unit)? = null, // NEW
) {
    val serverState by viewModel.serverState.collectAsState()

    Scaffold(
        topBar = {
            PageWebViewTopBar(
                title = title, onNavigateUp = onNavigateUp, actions = topBarActions
            )
        },
    ) { padding ->
        Box(
            modifier = modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            when (serverState) {
                is ServerState.Running -> {
                    PageWebViewInternal(path = path, viewModel = viewModel, jsCommands = jsCommands)
                }

                is ServerState.Error -> {
                    PageWebViewError()
                }

                ServerState.Stopped -> {
                    CircularWavyProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun PageWebViewError() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(imageVector = Icons.Default.Warning, contentDescription = null)
        Text(
            text = stringResource(R.string.page_web_view_error), modifier = Modifier.padding(16.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PageWebViewInternal(
    path: String,
    viewModel: PageWebViewViewModel,
    jsCommands: Flow<String>? = null,
) {
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(jsCommands, webViewRef) {
        jsCommands?.collect { script ->
            webViewRef?.evaluateJavascript(script, null)
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                    onErrorCallbacks.add { error ->
                        Timber.e("PageWebView error: %s", error.description)
                        hasError = true
                        isLoading = false
                    }
                }
                webChromeClient = PageChromeClient()
                visibility = View.INVISIBLE
                webViewRef = this
            }
        }, update = { webView ->
            val nightMode = if (Themes.currentTheme.isNightMode) "#night" else ""
            val url = "${viewModel.serverBaseUrl}$path$nightMode"
            if (webView.tag != url) {
                webView.tag = url
                isLoading = true
                hasError = false
                webView.visibility = View.INVISIBLE
                Timber.i("PageWebView: Loading %s", url)
                webView.loadUrl(url)
            }
        }, onRelease = { webView ->
            webView.stopLoading()
            webView.webViewClient = android.webkit.WebViewClient()
            webView.destroy()
            webViewRef = null
        }, modifier = Modifier.fillMaxSize()
        )

        if (isLoading) {
            CircularWavyProgressIndicator()
        }

        if (hasError) {
            PageWebViewError()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PageWebViewTopBar(
    title: String?,
    onNavigateUp: () -> Unit,
    actions: @Composable (RowScope.() -> Unit)? = null,
) {
    TopAppBar(title = {
        title?.let {
            Text(
                it,
                style = MaterialTheme.typography.displayMediumEmphasized,
                maxLines = 1
            )
        }
    }, navigationIcon = {
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
    }, actions = {
        actions?.invoke(this)
    })
}

@Preview
@Composable
private fun PageWebViewTopBarPreview() {
    AnkiDroidTheme {
        PageWebViewTopBar(title = "Preview Title", onNavigateUp = {})
    }
}
