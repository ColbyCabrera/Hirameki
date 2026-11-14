// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.utils

import android.os.Bundle

/**
 * Collection of useful methods to be used with [android.os.Bundle]
 */
object BundleUtils {
    /**
     * Retrieves a [Long] value from a [Bundle] using a key, returns null if not found
     *
     * can be null to support nullable bundles like [androidx.fragment.app.Fragment.getArguments]
     * @param key the key to use
     * @return the long value, or null if not found
     */
    fun Bundle.getNullableLong(key: String): Long? =
        if (!containsKey(key)) {
            null
        } else {
            getLong(key)
        }

    /**
     * Retrieves a [Long] value from a [Bundle] using a key, throws if not found
     *
     * @param key A string key
     * @return the value associated with [key]
     * @throws IllegalStateException If [key] does not exist in the bundle
     */
    fun Bundle.requireLong(key: String): Long {
        if (!this.containsKey(key)) {
            throw IllegalStateException("key: '$key' not found")
        }
        return getLong(key)
    }

    /**
     * Retrieves a [Int] value from a [Bundle] using a key, returns null if not found
     *
     * can be null to support nullable bundles like [androidx.fragment.app.Fragment.getArguments]
     * @param key the key to use
     * @return the int value, or null if not found
     */
    fun Bundle.getNullableInt(key: String): Int? =
        if (!containsKey(key)) {
            null
        } else {
            getInt(key)
        }
}

/**
 * Retrieves a [Boolean] value from a [Bundle] using a key, throws if not found
 *
 * @param key A string key
 * @return the value associated with [key]
 * @throws IllegalStateException If [key] does not exist in the bundle
 */
fun Bundle.requireBoolean(key: String): Boolean {
    check(containsKey(key)) { "key: '$key' not found" }
    return getBoolean(key)
}
