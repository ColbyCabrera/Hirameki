/*
 *  Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>
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

import android.graphics.Bitmap
import android.webkit.ValueCallback
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.isVisible
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.OnPageFinishedCallback
import com.ichi2.utils.AssetHelper.guessMimeType
import com.ichi2.utils.toRGBHex
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * Base WebViewClient to be used on [PageFragment]
 */
open class PageWebViewClient : WebViewClient() {
    val onPageFinishedCallbacks: MutableList<OnPageFinishedCallback> = mutableListOf()
    val onErrorCallbacks: MutableList<OnErrorCallback> = mutableListOf()

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        val path = request.url.path
        if (request.method != "GET" || path == null) return null
        if (path == "/favicon.png") {
            return WebResourceResponse("image/x-icon", null, ByteArrayInputStream(byteArrayOf()))
        }

        val assetPath =
            if (path.startsWith("/_app/")) {
                "backend/sveltekit/app/${path.substring(6)}"
            } else if (isSvelteKitPage(path.substring(1))) {
                "backend/sveltekit/index.html"
            } else {
                return null
            }

        try {
            val mimeType = guessMimeType(assetPath)
            val inputStream = view.context.assets.open(assetPath)
            val response = WebResourceResponse(mimeType, null, inputStream)
            if ("immutable" in path) {
                response.responseHeaders = mapOf("Cache-Control" to "max-age=31536000")
            }
            return response
        } catch (_: IOException) {
            Timber.w("Not found %s", assetPath)
        }
        return null
    }

    override fun onPageStarted(
        view: WebView?,
        url: String?,
        favicon: Bitmap?,
    ) {
        super.onPageStarted(view, url, favicon)
        view?.let { webView ->
            // Extract Material 3 colors for theming
            val bgColor = MaterialColors.getColor(webView, android.R.attr.colorBackground).toRGBHex()
            val textColor = MaterialColors.getColor(webView, com.google.android.material.R.attr.colorOnBackground).toRGBHex()
            val primaryColor = MaterialColors.getColor(webView, com.google.android.material.R.attr.colorPrimary).toRGBHex()
            val onPrimaryColor = MaterialColors.getColor(webView, com.google.android.material.R.attr.colorOnPrimary).toRGBHex()
            val surfaceColor = MaterialColors.getColor(webView, com.google.android.material.R.attr.colorSurface).toRGBHex()
            val onSurfaceColor = MaterialColors.getColor(webView, com.google.android.material.R.attr.colorOnSurface).toRGBHex()
            val surfaceVariantColor = MaterialColors.getColor(webView, com.google.android.material.R.attr.colorSurfaceVariant).toRGBHex()
            val outlineColor = MaterialColors.getColor(webView, com.google.android.material.R.attr.colorOutline).toRGBHex()
            val secondaryColor = MaterialColors.getColor(webView, com.google.android.material.R.attr.colorSecondary).toRGBHex()
            val tertiaryContainerColor = MaterialColors.getColor(webView, com.google.android.material.R.attr.colorTertiaryContainer).toRGBHex()
            
            // Inject comprehensive Material 3 theming CSS
            webView.evaluateAfterDOMContentLoaded(
                """
                (function() {
                    var css = `
                        /* CSS Variables for Material 3 */
                        :root {
                            --m3-background: $bgColor;
                            --m3-on-background: $textColor;
                            --m3-primary: $primaryColor;
                            --m3-on-primary: $onPrimaryColor;
                            --m3-surface: $surfaceColor;
                            --m3-on-surface: $onSurfaceColor;
                            --m3-surface-variant: $surfaceVariantColor;
                            --m3-outline: $outlineColor;
                            --m3-secondary: $secondaryColor;
                            --m3-tertiary-container: $tertiaryContainerColor;
                            /* Override Anki's CSS variables */
                            --fg: $textColor;
                            --canvas: $bgColor;
                            --border: $outlineColor;
                        }
                        
                        /* Base styling - colors only */
                        body {
                            background-color: $bgColor !important;
                            color: $textColor !important;
                        }
                        
                        /* Links - colors only */
                        a, a:link, a:visited {
                            color: $primaryColor !important;
                        }
                        
                        /* Range box header - colors only */
                        .range-box {
                            background: $surfaceColor !important;
                            border-color: $outlineColor !important;
                        }
                        
                        /* Radio buttons - accent color only */
                        input[type="radio"] {
                            accent-color: $primaryColor !important;
                        }
                        
                        /* Checkboxes - accent color only */
                        input[type="checkbox"] {
                            accent-color: $primaryColor !important;
                        }
                        
                        /* Text inputs - colors only */
                        input[type="text"], input[type="search"], select, textarea {
                            background-color: $surfaceVariantColor !important;
                            color: $onSurfaceColor !important;
                            border-color: $outlineColor !important;
                        }
                        
                        /* Labels - color only */
                        label {
                            color: $textColor !important;
                        }
                        
                        .graphs-container {
                            background-color: $bgColor !important;
                        }
                        
                        /* Graph cards - Material 3 card styling */
                        .graphs-container > * {
                            background-color: $surfaceColor !important;
                            color: $onSurfaceColor !important;
                            border: 1px solid $surfaceVariantColor !important;
                            border-radius: 16px !important;
                            padding: 16px !important;
                        }
                        
                        /* Card headings */
                        .graphs-container h2,
                        .graphs-container h3,
                        .graphs-container .title {
                            color: $textColor !important;
                        }
                        
                        /* Graph/chart SVG elements */
                        svg text, .axis text {
                            fill: $textColor !important;
                        }
                        
                        svg .axis path, svg .axis line {
                            stroke: $outlineColor !important;
                        }
                    `;
                    var style = document.createElement('style');
                    style.id = 'material3-theme';
                    style.appendChild(document.createTextNode(css));
                    document.head.appendChild(style);
                    console.log('Material 3 theming applied');
                })();
                """.trimIndent(),
            )
        }
    }

    /**
     * Shows the WebView after the page is loaded
     *
     * This may be overridden if additional 'screen ready' logic is provided by the backend
     * @see DeckOptions
     */
    open fun onShowWebView(webView: WebView) {
        Timber.v("Displaying WebView")
        webView.isVisible = true
    }

    override fun onPageFinished(
        view: WebView?,
        url: String?,
    ) {
        super.onPageFinished(view, url)
        if (view == null) return
        onPageFinishedCallbacks.forEach { callback ->
            try {
                callback.onPageFinished(view)
            } catch (e: Exception) {
                Timber.e(e, "onPageFinishedCallback threw an exception")
            }
        }
        /** [PageFragment.webView] is invisible by default to avoid flashes while
         * the page is loaded, and can be made visible again after it finishes loading */
        onShowWebView(view)
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        super.onReceivedError(view, request, error)
        if (request.isForMainFrame) {
            onErrorCallbacks.forEach {
                try {
                    it.onError(error)
                } catch (e: Exception) {
                    Timber.e(e, "onErrorCallback threw an exception")
                }
            }
        }
    }
}

fun isSvelteKitPage(path: String): Boolean {
    val pageName = path.substringBefore("/")
    return when (pageName) {
        "graphs",
        "congrats",
        "card-info",
        "change-notetype",
        "deck-options",
        "import-anki-package",
        "import-csv",
        "import-page",
        "image-occlusion",
        -> true
        else -> false
    }
}

fun WebView.evaluateAfterDOMContentLoaded(
    script: String,
    resultCallback: ValueCallback<String>? = null,
) {
    evaluateJavascript(
        """
        var codeToRun = function() { 
            $script
        }
        
        if (document.readyState === "loading") {
          document.addEventListener("DOMContentLoaded", codeToRun);
        } else {
          codeToRun();
        }
        """.trimIndent(),
        resultCallback,
    )
}
