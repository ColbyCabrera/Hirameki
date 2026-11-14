// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.utils

import kotlinx.coroutines.Job
import timber.log.Timber

/**
 * Prevent multiple instances of a method being executed simultaneously
 */
object OnlyOnce {
    private val blockedFunctions = mutableSetOf<Any>()

    enum class Method {
        ANSWER_CARD,
        UNIT_TEST,
    }

    /**
     * Prevents multiple instances of a method being executed simultaneously
     *
     * If the provided method is not running, run it
     * If the provided method is running, do nothing more
     */
    fun preventSimultaneousExecutions(
        name: Method,
        function: () -> Job,
    ) {
        if (!blockedFunctions.add(name)) {
            Timber.w("simultaneously executions of $name blocked")
            return
        }
        Timber.v("executing $name")
        function().invokeOnCompletion {
            Timber.v("completed $name")
            blockedFunctions.remove(name)
        }
    }
}
