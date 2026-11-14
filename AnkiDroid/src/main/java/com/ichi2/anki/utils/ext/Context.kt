// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.utils.ext

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.TypedArray
import android.net.Uri
import android.provider.Settings
import android.util.AttributeSet
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import timber.log.Timber
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Run a [block] on a [TypedArray] receiver that is recycled at the end.
 * @return The return value of the block.
 *
 * @see android.content.res.Resources.Theme.obtainStyledAttributes
 * @see androidx.core.content.withStyledAttributes
 */
@OptIn(ExperimentalContracts::class)
inline fun <T> Context.usingStyledAttributes(
    set: AttributeSet?,
    attrs: IntArray,
    @AttrRes defStyleAttr: Int = 0,
    @StyleRes defStyleRes: Int = 0,
    block: TypedArray.() -> T,
): T {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

    val typedArray = obtainStyledAttributes(set, attrs, defStyleAttr, defStyleRes)
    return typedArray.block().also { typedArray.recycle() }
}

/**
 * Opens the app's system Settings page so a permanently-denied permission can be granted.
 */
fun Context.openAppSettingsScreen() {
    Timber.i("launching ACTION_APPLICATION_DETAILS_SETTINGS")
    val intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        )
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Timber.w(e, "Unable to open application Settings")
    }
}
