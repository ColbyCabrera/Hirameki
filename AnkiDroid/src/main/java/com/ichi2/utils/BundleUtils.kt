/*
 Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
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
    fun Bundle.getNullableLong(key: String): Long? = if (!containsKey(key)) {
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
    fun Bundle.getNullableInt(key: String): Int? = if (!containsKey(key)) {
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

/**
 * Returns a new [Bundle] with the given key/value pairs as elements.
 *
 * Convenience method, allowing a `null` pair to mean 'exclude from the bundle'
 *
 * ```kotlin
 * bundleOfNotNull(
 *     optional?.let { KEY to it }
 * )
 * ```
 *
 * @throws IllegalArgumentException When a value is not a supported type of [Bundle].
 */
fun bundleOfNotNull(vararg pairs: Pair<String, Any>?): Bundle = Bundle().apply {
    for (pair in pairs) {
        if (pair == null) {
            continue
        }
        val (key, value) = pair
        when (value) {
            is Boolean -> putBoolean(key, value)
            is Byte -> putByte(key, value)
            is Char -> putChar(key, value)
            is Double -> putDouble(key, value)
            is Float -> putFloat(key, value)
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Short -> putShort(key, value)
            is String -> putString(key, value)
            is CharSequence -> putCharSequence(key, value)
            is Bundle -> putBundle(key, value)
            is BooleanArray -> putBooleanArray(key, value)
            is ByteArray -> putByteArray(key, value)
            is CharArray -> putCharArray(key, value)
            is DoubleArray -> putDoubleArray(key, value)
            is FloatArray -> putFloatArray(key, value)
            is IntArray -> putIntArray(key, value)
            is LongArray -> putLongArray(key, value)
            is ShortArray -> putShortArray(key, value)
            is Array<*> -> {
                val componentType = requireNotNull(value::class.java.componentType) { "Expected array with non-null component type for key \"$key\"" }
                @Suppress("UNCHECKED_CAST") when {
                    android.os.Parcelable::class.java.isAssignableFrom(componentType) -> {
                        putParcelableArray(key, value as Array<android.os.Parcelable>)
                    }

                    String::class.java.isAssignableFrom(componentType) -> {
                        putStringArray(key, value as Array<String>)
                    }

                    CharSequence::class.java.isAssignableFrom(componentType) -> {
                        putCharSequenceArray(key, value as Array<CharSequence>)
                    }

                    Int::class.java.isAssignableFrom(componentType) -> {
                        // Int is a primitive in Kotlin, but Array<Int> is Integer[]
                        throw IllegalArgumentException("Unsupported bundle component Array<Int> for key \"$key\". Use IntArray instead.")
                    }

                    else -> {
                        val valueType = componentType.canonicalName
                        throw IllegalArgumentException(
                            "Unsupported bundle component Array<$valueType> for key \"$key\"",
                        )
                    }
                }
            }

            is android.util.Size -> putSize(key, value)
            is android.util.SizeF -> putSizeF(key, value)
            is android.os.IBinder -> putBinder(key, value)
            is android.os.Parcelable -> putParcelable(key, value)
            is java.io.Serializable -> putSerializable(key, value)
            else -> {
                val valueType = value.javaClass.canonicalName
                throw IllegalArgumentException("Unsupported bundle component ($valueType) for key \"$key\"")
            }
        }
    }
}
