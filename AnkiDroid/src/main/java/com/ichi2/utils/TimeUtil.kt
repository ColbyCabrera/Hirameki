// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>

package com.ichi2.utils

import androidx.annotation.CheckResult
import com.ichi2.anki.common.time.TimeManager
import timber.log.Timber

/**
 * Usage:
 *
 * ```kotlin
 * val result = measureTime("operation") { operation() }
 * ```
 * -> `D/TimeUtilKt executed mHtmlGenerator in 23ms`
 */
fun <T> measureTime(
    functionName: String? = "",
    function: () -> T,
): T {
    val startTime = TimeManager.time.intTimeMS()
    val result = function()
    val endTime = TimeManager.time.intTimeMS()
    Timber.d(
        "executed %sin %dms",
        if (functionName.isNullOrEmpty()) "" else "$functionName ",
        endTime - startTime,
    )
    return result
}

/**
 * Measures the time to execute a `suspend` function
 *
 * Usage:
 *
 * ```kotlin
 * val result = coMeasureTime("operation") { operation() }
 * ```
 * -> `D/TimeUtilKt executed mHtmlGenerator in 23ms`
 */
suspend fun <T> coMeasureTime(
    functionName: String? = "",
    function: suspend () -> T,
): T {
    val startTime = TimeManager.time.intTimeMS()
    val result = function()
    val endTime = TimeManager.time.intTimeMS()
    Timber.d(
        "executed %sin %dms",
        if (functionName.isNullOrEmpty()) "" else "$functionName ",
        endTime - startTime,
    )
    return result
}

/**
 * Used to time an operation across two function calls
 *
 * ```kotlin
 * private val renderStopwatch: Stopwatch = Stopwatch.init("page render")
 *
 * fun start() {
 *     renderStopwatch.reset()
 * }
 *
 * fun stop() {
 *     renderStopwatch.logElapsed()
 * }
 * ```
 *
 * -> `D/Stopwatch executed page render in 67ms`
 */
class Stopwatch(
    private val executionName: String?,
) {
    private var startTime = TimeManager.time.intTimeMS()

    fun logElapsed() {
        val endTime = TimeManager.time.intTimeMS()
        Timber.d(
            "executed %sin %dms",
            if (executionName.isNullOrEmpty()) "" else "$executionName ",
            endTime - startTime,
        )
    }

    fun reset() {
        startTime = TimeManager.time.intTimeMS()
    }

    companion object {
        /** initializes the stopwatch to ensure `stop()` before `start()` won't crash */
        @CheckResult
        fun init(executionName: String? = null) = Stopwatch(executionName)
    }
}
