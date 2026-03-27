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

        val assetPath = if (path.startsWith("/_app/")) {
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
            val bgColor =
                MaterialColors.getColor(webView, android.R.attr.colorBackground).toRGBHex()
            val textColor = MaterialColors.getColor(
                webView, com.google.android.material.R.attr.colorOnBackground
            ).toRGBHex()
            val primaryColor =
                MaterialColors.getColor(webView, androidx.appcompat.R.attr.colorPrimary).toRGBHex()
            val onPrimaryColor =
                MaterialColors.getColor(webView, com.google.android.material.R.attr.colorOnPrimary)
                    .toRGBHex()
            val surfaceColor =
                MaterialColors.getColor(webView, com.google.android.material.R.attr.colorSurface)
                    .toRGBHex()
            val onSurfaceColor =
                MaterialColors.getColor(webView, com.google.android.material.R.attr.colorOnSurface)
                    .toRGBHex()
            val surfaceContainerColor = MaterialColors.getColor(
                webView, com.google.android.material.R.attr.colorSurfaceContainer
            ).toRGBHex()
            val outlineColor =
                MaterialColors.getColor(webView, com.google.android.material.R.attr.colorOutline)
                    .toRGBHex()
            val secondaryColor =
                MaterialColors.getColor(webView, com.google.android.material.R.attr.colorSecondary)
                    .toRGBHex()
            val tertiaryContainerColor = MaterialColors.getColor(
                webView, com.google.android.material.R.attr.colorTertiaryContainer
            ).toRGBHex()
            val onTertiaryContainerColor = MaterialColors.getColor(
                webView, com.google.android.material.R.attr.colorOnTertiaryContainer
            ).toRGBHex()
            val onSurfaceVariantColor = MaterialColors.getColor(
                webView, com.google.android.material.R.attr.colorOnSurfaceVariant
            ).toRGBHex()
            val surfaceContainerHighColor = MaterialColors.getColor(
                webView, com.google.android.material.R.attr.colorSurfaceContainerHigh
            ).toRGBHex()
                val errorContainerColor = MaterialColors.getColor(
                webView, com.google.android.material.R.attr.colorErrorContainer
            ).toRGBHex()

            // Inject comprehensive Material 3 theming CSS
            webView.evaluateAfterDOMContentLoaded(
                """
                (function() {
                    var css = `
                        /* Override ALL Anki + Bootstrap CSS variables with Material 3 */
                        :root, :root.night-mode {
                            /* Foreground */
                            --fg: $textColor;
                            --fg-subtle: $onSurfaceVariantColor;
                            --fg-disabled: $outlineColor;
                            --fg-faint: $outlineColor;
                            --fg-link: $primaryColor;
                            /* Canvas / Background */
                            --canvas: $bgColor;
                            --canvas-elevated: $surfaceColor;
                            --canvas-inset: $surfaceContainerColor;
                            --canvas-overlay: $surfaceContainerHighColor;
                            --canvas-code: $surfaceContainerColor;
                            /* Borders */
                            --border: $outlineColor;
                            --border-subtle: $surfaceContainerHighColor;
                            --border-strong: $outlineColor;
                            --border-focus: $primaryColor;
                            /* Buttons */
                            --button-bg: $surfaceContainerColor;
                            --button-gradient-start: $surfaceContainerColor;
                            --button-gradient-end: $surfaceContainerColor;
                            --button-hover-border: $outlineColor;
                            --button-disabled: $surfaceContainerColor;
                            --button-primary-bg: $primaryColor;
                            --button-primary-gradient-start: $primaryColor;
                            --button-primary-gradient-end: $primaryColor;
                            --button-primary-disabled: $primaryColor;
                            /* Shadows */
                            --shadow: transparent;
                            --shadow-inset: transparent;
                            --shadow-subtle: transparent;
                            --shadow-focus: $primaryColor;
                            /* Accents */
                            --accent-card: $primaryColor;
                            --accent-note: $secondaryColor;
                            --accent-danger: $errorContainerColor;
                            /* Bootstrap body / text */
                            --bs-body-bg: $bgColor;
                            --bs-body-color: $textColor;
                            --bs-emphasis-color: $textColor;
                            --bs-secondary-color: $onSurfaceVariantColor;
                            --bs-tertiary-color: $outlineColor;
                            --bs-secondary-bg: $surfaceContainerColor;
                            --bs-tertiary-bg: $surfaceContainerColor;
                            /* Bootstrap brand */
                            --bs-primary: $primaryColor;
                            --bs-secondary: $secondaryColor;
                            --bs-link-color: $primaryColor;
                            --bs-link-hover-color: $primaryColor;
                            /* Bootstrap borders */
                            --bs-border-color: $outlineColor;
                            --bs-border-color-translucent: $outlineColor;
                        }
                        
                        body {
                            background-color: $bgColor !important;
                            color: $textColor !important;
                        }
                        
                        a, a:link, a:visited {
                            color: $primaryColor !important;
                        }
                        
                        /* Bootstrap component-scoped variable overrides */
                        .form-control, .form-select {
                            background-color: $surfaceContainerColor !important;
                            color: $onSurfaceColor !important;
                            border-color: $outlineColor !important;
                        }
                        
                        .form-control:focus, .form-select:focus {
                            border-color: $primaryColor !important;
                            box-shadow: 0 0 0 1px $primaryColor !important;
                        }
                        
                        .modal-content {
                            --bs-modal-bg: $surfaceColor;
                            --bs-modal-color: $onSurfaceColor;
                            --bs-modal-border-color: $outlineColor;
                            background-color: $surfaceColor !important;
                        }
                        
                        /* Range box header - improved layout */
                        .range-box {
                            background: $surfaceColor !important;
                            border-color: $outlineColor !important;
                            display: flex !important;
                            flex-wrap: wrap !important;
                            align-items: center !important;
                            justify-content: center !important;
                            gap: 4px !important;
                            padding: 0px 16px 4px !important;
                        }
                        
                        /* InputBox containers inside range-box */
                        .range-box > div {
                            display: flex !important;
                            align-items: center !important;
                            justify-content: center !important;
                            gap: 4px !important;
                            background-color: $surfaceContainerColor !important;
                            border-radius: 24px !important;
                            padding: 4px 4px !important;
                            max-width: 100% !important;
                        }
                        
                        /* First InputBox - make search field go to new line */
                        .range-box > div:first-of-type {
                            flex-wrap: wrap !important;
                        }
                        
                        /* Search field should take full width when wrapped */
                        .range-box > div:first-of-type input[type="text"] {
                            flex: 1 1 100% !important;
                            margin-top: 8px !important;
                        }
                        
                        /* Labels inside range-box - chip style */
                        .range-box label {
                            display: inline-flex !important;
                            align-items: center !important;
                            gap: 6px !important;
                            padding: 8px 12px !important;
                            border-radius: 20px !important;
                            cursor: pointer !important;
                            transition: background-color 0.2s !important;
                        }
                        
                        .range-box label:hover {
                            background-color: $outlineColor !important;
                        }
                        
                        /* Selected radio label highlight */
                        .range-box input[type="radio"]:checked + label,
                        .range-box label:has(input[type="radio"]:checked) {
                            background-color: $tertiaryContainerColor !important;
                            color: $onTertiaryContainerColor !important;
                        }
                        
                        /* Search input styling */
                        .range-box input[type="text"],
                        #statisticsSearchText {
                            background-color: $surfaceContainerColor !important;
                            color: $onSurfaceColor !important;
                            border: 1px solid $outlineColor !important;
                            border-radius: 20px !important;
                            padding: 8px 16px !important;
                            min-width: 150px !important;
                        }
                        
                        /* Hide loading spinner styling */
                        .range-box .spin {
                            color: $primaryColor !important;
                        }
                        
                        /* Radio buttons - accent color only */
                        input[type="radio"] {
                            accent-color: $primaryColor !important;
                        }
                        
                        /* Checkboxes - accent color only */
                        input[type="checkbox"] {
                            accent-color: $primaryColor !important;
                        }

                        input[type="range"] {
                            accent-color: $primaryColor !important;
                        }
                        
                        /* Text inputs - colors only */
                        input[type="text"], input[type="search"], select, textarea {
                            background-color: $surfaceContainerColor !important;
                            color: $onSurfaceColor !important;
                            border-color: $outlineColor !important;
                        }
                        
                        /* Labels - color only */
                        label {
                            color: $textColor !important;
                        }

                        /* Deck options page layout */
                        .deck-options-page,
                        .deck-options-page .container-columns,
                        .deck-options-page .row-columns {
                            background-color: $bgColor !important;
                            color: $textColor !important;
                            box-shadow: none !important;
                        }

                        /* Deck options sections and dialogs */
                        .deck-options-page details,
                        .deck-options-page .modal-content,
                        .deck-options-page .radio-group,
                        .deck-options-page .button-bar {
                            background-color: $surfaceColor !important;
                            color: $onSurfaceColor !important;
                            border: 1px solid $surfaceContainerColor !important;
                            box-shadow: none !important;
                        }

                        .deck-options-page details {
                            padding: 16px !important;
                        }

                        .deck-options-page details > summary {
                            color: $textColor !important;
                            cursor: pointer !important;
                            font-weight: 600 !important;
                        }

                        .deck-options-page details[open] > summary {
                            margin-bottom: 12px !important;
                        }

                        .deck-options-page .modal-header,
                        .deck-options-page .modal-footer {
                            border-color: $outlineColor !important;
                        }

                        .deck-options-page .btn {
                            background-color: $primaryColor !important;
                            color: $onPrimaryColor !important;
                            border: none !important;
                            border-radius: 20px !important;
                            box-shadow: none !important;
                            background-image: none !important;
                        }

                        .deck-options-page .btn-primary {
                            --bs-btn-color: $onPrimaryColor !important;
                            --bs-btn-hover-color: $onPrimaryColor !important;
                            --bs-btn-active-color: $onPrimaryColor !important;
                            --bs-btn-disabled-color: $onPrimaryColor !important;
                            --bs-btn-bg: $primaryColor;
                            --bs-btn-hover-bg: $primaryColor;
                            --bs-btn-active-bg: $primaryColor;
                            --bs-btn-disabled-bg: $primaryColor;
                            --bs-btn-border-color: transparent;
                            --bs-btn-hover-border-color: transparent;
                            --bs-btn-active-border-color: transparent;
                            --bs-btn-disabled-border-color: transparent;
                            color: $onPrimaryColor !important;
                        }

                        .deck-options-page .btn-primary,
                        .deck-options-page .btn-primary:hover,
                        .deck-options-page .btn-primary:focus-visible,
                        .deck-options-page .btn-primary:active,
                        .deck-options-page .btn-primary:disabled,
                        .deck-options-page .btn-primary * {
                            color: $onPrimaryColor !important;
                        }

                        .label-button.primary,
                        .label-button.primary:hover,
                        .label-button.primary:focus,
                        .label-button.primary:focus-visible,
                        .label-button.primary:active,
                        .label-button.primary:disabled,
                        .label-button.primary * {
                            color: $onPrimaryColor !important;
                        }

                        .deck-options-page .revert .badge,
                        .deck-options-page .revert .badge:hover,
                        .deck-options-page .revert .badge:focus,
                        .deck-options-page .revert .badge:focus-visible,
                        .deck-options-page .revert .badge:active {
                            background-color: transparent !important;
                            border: 0 !important;
                            outline: 0 !important;
                            box-shadow: none !important;
                            color: $onSurfaceVariantColor !important;
                        }

                        .deck-options-page .revert .badge:hover,
                        .deck-options-page .revert .badge:focus-visible {
                            color: $onSurfaceColor !important;
                        }

                        .deck-options-page .form-control,
                        .deck-options-page .form-select,
                        .deck-options-page input[type="text"],
                        .deck-options-page input[type="number"],
                        .deck-options-page input[type="date"],
                        .deck-options-page textarea {
                            background-color: $surfaceContainerColor !important;
                            color: $onSurfaceColor !important;
                            border-color: $outlineColor !important;
                            box-shadow: none !important;
                        }

                        .deck-options-page .form-control:focus,
                        .deck-options-page .form-select:focus,
                        .deck-options-page input[type="text"]:focus,
                        .deck-options-page input[type="number"]:focus,
                        .deck-options-page input[type="date"]:focus,
                        .deck-options-page textarea:focus {
                            border-color: $primaryColor !important;
                            box-shadow: 0 0 0 1px $primaryColor !important;
                        }

                        .deck-options-page .col-form-label,
                        .deck-options-page .form-label,
                        .deck-options-page .header,
                        .deck-options-page summary,
                        .deck-options-page .day {
                            color: $textColor !important;
                        }

                        .deck-options-page .dropdown-divider {
                            border-color: $outlineColor !important;
                            opacity: 1 !important;
                        }

                        .deck-options-page .alert {
                            background-color: $surfaceContainerColor !important;
                            color: $onSurfaceColor !important;
                            border: 1px solid $outlineColor !important;
                            border-radius: 16px !important;
                        }
                        
                        .graphs-container {
                            background-color: $bgColor !important;
                        }
                        
                        /* Graph cards - Material 3 card styling */
                        .graphs-container > * {
                            background-color: $surfaceColor !important;
                            color: $onSurfaceColor !important;
                            border: 1px solid $surfaceContainerColor !important;
                            border-radius: 16px !important;
                            padding: 16px !important;
                            box-shadow: none !important;
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
                    var existingStyle = document.getElementById('material3-theme');
                    if (existingStyle) {
                        existingStyle.textContent = css;
                    } else {
                        var style = document.createElement('style');
                        style.id = 'material3-theme';
                        style.textContent = css;
                        document.head.appendChild(style);
                    }
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

    override fun onReceivedError(
        view: WebView, request: WebResourceRequest, error: WebResourceError
    ) {
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
