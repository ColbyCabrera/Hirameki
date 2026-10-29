// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2026 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.utils

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.webkit.WebView
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.content.pm.PackageInfoCompat
import androidx.webkit.WebViewCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.R
import com.ichi2.anki.pages.PostRequestBridge
import com.ichi2.anki.utils.openUrl
import timber.log.Timber

/** The Android package version code which corresponds to the WebView version. */
internal const val OLDEST_WORKING_WEBVIEW_VERSION_CODE = 584500000L

/**
 * The minimum supported WebView version (Human readable).
 *
 * 116 is the first stable version with all the APIs used by [PostRequestBridge].
 * Launched in [August 15, 2023](https://chromiumdash.appspot.com/fetch_milestone_schedule?mstone=116).
 */
internal const val OLDEST_WORKING_WEBVIEW_VERSION = 116

/**
 * Shows a dialog if the current WebView version is older than the last supported version.
 */
fun checkWebviewVersion(activity: AnkiActivity) {
    showDialogIfWebViewOutdated(activity)
}

/**
 * Shows a dialog if the current WebView version is older than the last supported version,
 * or if it lacks the APIs used by [PostRequestBridge], as non-Google WebViews may.
 */
fun showDialogIfWebViewOutdated(
    context: Context,
    onOutdated: () -> Unit = {},
): Boolean {
    if (WebViewCompat.getCurrentWebViewPackage(context) == null) return false
    val outdatedVersion = getChromeLikeWebViewVersionIfOutdated(context)
    val message =
        when {
            outdatedVersion != null ->
                context.getString(R.string.webview_update_message, outdatedVersion, OLDEST_WORKING_WEBVIEW_VERSION)
            !PostRequestBridge.isSupported() -> context.getString(R.string.webview_unsupported_message)
            else -> return false
        }

    // Provide guidance to the user if the WebView is outdated
    val webviewPackageInfo = getAndroidSystemWebViewPackageInfo(context.packageManager)
    val legacyWebViewPackageInfo = getLegacyWebViewPackageInfo(context.packageManager)
    // TODO modify the alert dialog text to handle the usage of developer builds for system WebView
    if (legacyWebViewPackageInfo != null) {
        Timber.w("WebView is outdated. %s: %s", legacyWebViewPackageInfo.packageName, legacyWebViewPackageInfo.versionName)
        showOutdatedWebViewDialog(context, message, R.string.link_legacy_webview_update, onOutdated)
    } else {
        Timber.w("WebView is outdated. %s: %s", webviewPackageInfo?.packageName, webviewPackageInfo?.versionName)
        showOutdatedWebViewDialog(context, message, R.string.link_webview_update, onOutdated)
    }
    return true
}

@MainThread
fun getWebviewUserAgent(context: Context): String? {
    try {
        return WebView(context).settings.userAgentString
    } catch (e: Throwable) {
        CrashReportService.sendExceptionReport(e, "WebViewUtils", "some issue occurred while extracting webview user agent")
    }
    return null
}

/*
 * Returns a Chrome-like WebView version if it is outdated, otherwise null if
 * cannot be determined at all or if okay
 */
private fun getChromeLikeWebViewVersionIfOutdated(context: Context): Int? {
    // If we cannot get the package information at all, return null
    val webviewPackageInfo = getAndroidSystemWebViewPackageInfo(context.packageManager) ?: return null
    val webviewVersion =
        webviewPackageInfo.versionName ?: run {
            Timber.w("Failed to obtain WebView version")
            return null
        }
    val versionCode = PackageInfoCompat.getLongVersionCode(webviewPackageInfo)
    return checkWebViewVersionComponents(webviewPackageInfo.packageName, webviewVersion, versionCode, getWebviewUserAgent(context))
}

@VisibleForTesting
fun checkWebViewVersionComponents(
    packageName: String,
    webviewVersion: String,
    versionCode: Long,
    userAgent: String?,
): Int? {
    // Checking the version code works for most webview packages
    if (versionCode >= OLDEST_WORKING_WEBVIEW_VERSION_CODE) {
        Timber.d(
            "WebView is up to date. %s: %s(%s)",
            packageName,
            webviewVersion,
            versionCode.toString(),
        )
        return null
    }

    // Sometimes the webview version code appears too old, and the package name does as well,
    // but it's a webview that advertises modern capabilities via User-Agent in "Chrome" section
    // Our warning is purely advisory, so, let's let those through if User-Agent looks okay
    userAgent?.let {
        val chromeRegex = """Chrome/(\d+)""".toRegex()
        val matchResult = chromeRegex.find(userAgent)?.groupValues?.get(1)
        matchResult?.toInt()?.let {
            if (it < OLDEST_WORKING_WEBVIEW_VERSION) {
                // If we got here, even the User-Agent says it's incompatible, return something
                // potentially useful to the user as a browser version
                return it
            }
        }
    }

    return null
}

private fun showOutdatedWebViewDialog(
    context: Context,
    message: String,
    @StringRes learnMoreUrl: Int,
    onDismiss: () -> Unit = {},
) {
    MaterialAlertDialogBuilder(context).show {
        setMessage(message)
        setPositiveButton(R.string.scoped_storage_learn_more) { _, _ ->
            context.openUrl(learnMoreUrl)
        }
        setOnDismissListener { onDismiss() }
    }
}

private fun getLegacyWebViewPackageInfo(packageManager: PackageManager): PackageInfo? =
    try {
        packageManager.getPackageInfo("com.android.webview", 0)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

/**
 * Returns a [PackageInfo] from the current system WebView, or `null` if unavailable
 */
private fun getAndroidSystemWebViewPackageInfo(packageManager: PackageManager): PackageInfo? {
    fun getPackage(packageName: String): PackageInfo? =
        try {
            packageManager.getPackageInfo(packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }

    // The WebView is called com.android.webview by default.
    // Partner devices which ship with Google applications ship the Google-specific version
    // of the WebView called com.google.android.webview.
    // https://issues.chromium.org/issues/40419837#comment10

    return getPackage("com.google.android.webview")
        ?: getPackage("com.android.webview") // com.android.webview is used on API 24
}
