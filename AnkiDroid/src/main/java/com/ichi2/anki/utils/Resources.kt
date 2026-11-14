// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2023 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentActivity
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.common.annotations.NeedsTest

/**
 * @param resId must be a [StringRes] or a [PluralsRes]
 */
fun Resources.getFormattedStringOrPlurals(
    resId: Int,
    quantity: Int,
): String =
    when (getResourceTypeName(resId)) {
        "string" -> getString(resId, quantity)
        "plurals" -> getQuantityString(resId, quantity, quantity)
        else -> throw IllegalArgumentException("Provided resId is not a valid @StringRes or @PluralsRes")
    }

/**
 * @see [Resources.getFormattedStringOrPlurals]
 */
fun Context.getFormattedStringOrPlurals(
    resId: Int,
    quantity: Int,
): String = resources.getFormattedStringOrPlurals(resId, quantity)

@SuppressLint("DiscouragedApi") // Resources.getIdentifier: Use of this function is discouraged
// because resource reflection makes it harder to perform build optimizations and compile-time
// verification of code. It is much more efficient to retrieve resources by identifier
// (e.g. `R.foo.bar`) than by name (e.g. `getIdentifier("bar", "foo", null)`)
private fun Context.getSystemBoolean(resName: String, fallback: Boolean): Boolean =
    try {
        val id = Resources.getSystem().getIdentifier(resName, "bool", "android")
        if (id != 0) {
            createPackageContext("android", 0).resources.getBoolean(id)
        } else {
            fallback
        }
    } catch (e: Exception) {
        CrashReportService.sendExceptionReport(e, "Context::getSystemBoolean")
        fallback
    }

@NeedsTest("true if the navbar is transparent and needs a scrim, false if not")
val FragmentActivity.navBarNeedsScrim: Boolean
    get() = getSystemBoolean("config_navBarNeedsScrim", true)

// https://m3.material.io/foundations/layout/applying-layout/window-size-classes
// adopted smallestScreenWidthDp instead of screenWidthDp
// to avoid layout changes when rotating the device
fun Resources.isWindowCompact() = configuration.smallestScreenWidthDp < 600
