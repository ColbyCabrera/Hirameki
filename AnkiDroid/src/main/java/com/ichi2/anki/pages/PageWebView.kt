// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025-2026 Colby Cabrera <colbycabrera.wd@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2026 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.pages

import android.annotation.SuppressLint
import android.view.View
import android.webkit.WebView
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ichi2.anki.R
import com.ichi2.anki.ui.compose.components.AnkiTopAppBar
import com.ichi2.anki.utils.openUrl
import com.ichi2.themes.Themes
import com.ichi2.utils.OutdatedWebView
import com.ichi2.utils.findOutdatedWebView
import kotlinx.coroutines.flow.Flow
import timber.log.Timber

/**
 * A reusable Compose wrapper for displaying Anki HTML pages via WebView.
 *
 * This composable wraps a WebView using [AndroidView] and handles POST requests
 * through [PageWebViewViewModel] via [PostRequestBridge].
 *
 * @param path The page path to load (e.g., "graphs", "deck-options/123")
 * @param title The title to display in the top app bar, or null for no title
 * @param onNavigateUp Callback for back navigation
 * @param modifier Optional modifier for the composable
 * @param viewModel The ViewModel handling page POST requests
 */
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
    val context = LocalContext.current
    val outdatedWebView = remember(context) { findOutdatedWebView(context) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AnkiTopAppBar(
                titleText = title,
                onNavigateUp = onNavigateUp,
                actions = { topBarActions?.invoke(this) },
            )
        },
    ) { padding ->
        Box(
            modifier =
                modifier
                    .padding(padding)
                    .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (outdatedWebView == null) {
                PageWebViewInternal(
                    path = path,
                    postRequestHandler = viewModel,
                    jsCommands = jsCommands,
                )
            } else {
                OutdatedWebViewDialog(outdatedWebView, onDismissRequest = onNavigateUp)
            }
        }
    }
}

/** The pages can't send requests without [PostRequestBridge], which requires a modern WebView */
@Composable
private fun OutdatedWebViewDialog(
    outdatedWebView: OutdatedWebView,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        text = { Text(outdatedWebView.message) },
        confirmButton = {
            TextButton(
                onClick = {
                    context.openUrl(outdatedWebView.learnMoreUrl)
                    onDismissRequest()
                },
            ) {
                Text(stringResource(R.string.scoped_storage_learn_more))
            }
        },
    )
}

@Composable
private fun PageWebViewError() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(imageVector = Icons.Default.Warning, contentDescription = null)
        Text(
            text = stringResource(R.string.page_web_view_error),
            modifier = Modifier.padding(16.dp),
        )
    }
}

@VisibleForTesting
@Composable
internal fun PageWebViewInternal(
    path: String,
    postRequestHandler: PostRequestHandler? = null,
    jsCommands: Flow<String>? = null,
) {
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // #143: bumped when the system reclaims the page's renderer; a new WebView replaces the dead one
    var rendererGeneration by remember { mutableIntStateOf(0) }

    // after a renderer crash, the dead WebView is removed (onRelease destroys it) and the error stays
    var rendererCrashed by remember { mutableStateOf(false) }
    val backgroundColor = MaterialTheme.colorScheme.background
    val backgroundColorArgb = remember(backgroundColor) { backgroundColor.toArgb() }

    val lifecycleOwner = LocalLifecycleOwner.current
    val bridgeScope = rememberCoroutineScope()
    val bridge =
        remember(postRequestHandler, bridgeScope) {
            postRequestHandler?.let { PostRequestBridge(it, bridgeScope) }
        }
    LaunchedEffect(jsCommands, webViewRef, lifecycleOwner) {
        jsCommands?.let { commands ->
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                commands.collect { script ->
                    webViewRef?.evaluateJavascript(script, null)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (!rendererCrashed) {
            key(rendererGeneration) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            with(settings) {
                                javaScriptEnabled = true
                                displayZoomControls = false
                                builtInZoomControls = true
                                setSupportZoom(true)
                            }
                            setBackgroundColor(backgroundColorArgb)
                            bridge?.attachTo(this)
                            webViewClient =
                                PageWebViewClient().apply {
                                    onPageFinishedCallbacks.add { webView ->
                                        isLoading = false
                                        webView.visibility = View.VISIBLE
                                    }
                                    onErrorCallbacks.add { error ->
                                        Timber.e("PageWebView error: %s", error.description)
                                        hasError = true
                                        isLoading = false
                                    }
                                    onRendererGone = { crashed ->
                                        if (crashed) {
                                            // a rebuilt page would likely crash again: show the error instead
                                            rendererCrashed = true
                                            hasError = true
                                            isLoading = false
                                        } else {
                                            rendererGeneration++
                                        }
                                    }
                                }
                            webChromeClient = PageChromeClient()
                            visibility = View.INVISIBLE
                            webViewRef = this
                        }
                    },
                    update = { webView ->
                        val nightMode = if (Themes.currentTheme.isNightMode) "#night" else ""
                        val url = "${PostRequestBridge.BASE_URL}$path$nightMode"
                        if (webView.tag != url) {
                            webView.tag = url
                            isLoading = true
                            hasError = false
                            webView.visibility = View.INVISIBLE
                            Timber.i("PageWebView: Loading %s", url)
                            webView.loadUrl(url)
                        }
                    },
                    onRelease = { webView ->
                        webView.stopLoading()
                        (webView.webViewClient as? PageWebViewClient)?.release()
                        webView.webViewClient = android.webkit.WebViewClient()
                        webView.destroy()
                        // a rebuild may have set a new WebView already
                        if (webViewRef === webView) webViewRef = null
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (isLoading) {
            CircularWavyProgressIndicator()
        }

        if (hasError) {
            PageWebViewError()
        }
    }
}
