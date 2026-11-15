// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.testutil

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Repeat(
    val times: Int,
)

class RepeatRule : TestRule {
    override fun apply(
        base: Statement,
        description: Description,
    ): Statement {
        val repeat = description.getAnnotation(Repeat::class.java)
        return if (repeat != null) {
            RepeatStatement(base, repeat.times)
        } else {
            base
        }
    }

    private class RepeatStatement(
        private val base: Statement,
        private val times: Int,
    ) : Statement() {
        init {
            require(times > 0) { "Repeat count must be > 0" }
        }

        override fun evaluate() {
            repeat(times) {
                base.evaluate()
            }
        }
    }
}
