// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.observability

import anki.collection.OpChanges
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.javaType
import kotlin.reflect.jvm.isAccessible

@RunWith(Parameterized::class)
class ChangeManagerTest {
    @JvmField // required for Parameter
    @Parameterized.Parameter
    var property: KProperty1<OpChanges, *>? = null

    @JvmField // required for Parameter
    @Parameterized.Parameter(1)
    var name: String? = null

    @Test
    fun `Property is set in ALL object`() {
        assertThat(name, property!!.call(ChangeManager.ALL), equalTo(true))
    }

    companion object {
        @Parameterized.Parameters(name = "{1}")
        @OptIn(ExperimentalStdlibApi::class)
        @JvmStatic // required for initParameters
        fun initParameters(): Collection<Array<out Any>> {
            val props =
                OpChanges::class.memberProperties.filter { it.returnType.javaType == Boolean::class.java }
            assertThat(props.size, greaterThan(0))

            props.forEach { it.isAccessible = true }
            return props.map {
                arrayOf(it, it.name)
            }
        }
    }
}
