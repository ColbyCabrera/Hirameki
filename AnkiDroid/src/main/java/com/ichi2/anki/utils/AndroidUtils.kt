// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.anki.showThemedToast
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.utils.AdaptionUtil
import com.ichi2.utils.copyToClipboard
import timber.log.Timber

/**
 * Acquire a wake lock and release it after running [block].
 *
 * @param levelAndFlags Combination of wake lock level and flag values defining
 *   the requested behavior of the WakeLock
 * @param tag Your class name (or other tag) for debugging purposes
 * @return The return value of `block`
 *
 * @see android.os.PowerManager.newWakeLock
 */
inline fun <T> withWakeLock(
    levelAndFlags: Int,
    tag: String,
    block: () -> T,
): T {
    val context = AnkiDroidApp.instance
    val wakeLock =
        ContextCompat
            .getSystemService(context, PowerManager::class.java)!!
            .newWakeLock(levelAndFlags, context.packageName + ":" + tag)

    wakeLock.acquire()

    return try {
        block()
    } finally {
        wakeLock.release()
    }
}

fun Context.openUrl(
    @StringRes url: Int,
) {
    openUrl(getString(url))
}

fun Context.openUrl(url: String) {
    openUrl(url.toUri())
}

fun Context.openUrl(uri: Uri) {
    if (!AdaptionUtil.hasWebBrowser(this)) {
        val noBrowserMessage = getString(R.string.no_browser_msg, uri.toString())
        if (this is FragmentActivity) {
            showSnackbar(noBrowserMessage) {
                setAction(android.R.string.copyUrl) {
                    copyToClipboard(uri.toString())
                }
            }
        } else {
            showThemedToast(this, noBrowserMessage, shortLength = false)
        }
        return
    }
    try {
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (ex: Exception) {
        Timber.w("No app found to handle opening an external url from ${this::class.java.name}")
        if (this is FragmentActivity) {
            showSnackbar(R.string.activity_start_failed)
        } else {
            showThemedToast(this, R.string.activity_start_failed, false)
        }
    }
}

// necessary for Fragments that are BaseSnackbarBuilderProvider to work correctly
fun Fragment.openUrl(uri: Uri) {
    if (!AdaptionUtil.hasWebBrowser(requireContext())) {
        showSnackbar(getString(R.string.no_browser_msg, uri.toString()))
        return
    }
    try {
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (ex: ActivityNotFoundException) {
        Timber.w("No app found to handle opening an external url from ${Fragment::class.java.name}")
        showSnackbar(R.string.activity_start_failed)
    }
}

fun Fragment.openUrl(
    @StringRes stringRes: Int,
) = openUrl(requireContext().getString(stringRes).toUri())
