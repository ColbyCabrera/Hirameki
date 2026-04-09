/*
 *  Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>
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

package com.ichi2.anki.testutil

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Repeat(val times: Int)

class RepeatRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement {
        val repeat = description.getAnnotation(Repeat::class.java)
        return if (repeat != null) {
            RepeatStatement(base, repeat.times)
        } else {
            base
        }
    }

    private class RepeatStatement(private val base: Statement, private val times: Int) :
        Statement() {
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
