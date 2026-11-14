// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.tests

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.R
import com.ichi2.themes.Themes.setTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException

@RunWith(Parameterized::class)
class LayoutValidationTest : InstrumentedTest() {
    @JvmField // required for Parameter
    @Parameterized.Parameter
    var resourceId = 0

    @JvmField // required for Parameter
    @Parameterized.Parameter(1)
    var name: String? = null

    @Test
    @Throws(Exception::class)
    fun ensureLayout() {
        // This should be fine to run on a device - but WebViews may be instantiated.
        // TODO: GestureDisplay.kt - why was mSwipeView.drawable null
        val targetContext = testContext
        setTheme(targetContext)
        val li = LayoutInflater.from(targetContext)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val root: ViewGroup = LinearLayout(targetContext)
            li.inflate(resourceId, root, true)
        }
    }

    companion object {
        @Parameterized.Parameters(name = "{1}")
        @Throws(
            IllegalAccessException::class,
            InvocationTargetException::class,
            InstantiationException::class,
        )
        @JvmStatic // required for initParameters
        fun initParameters(): Collection<Array<out Any>> {
            val ctor: Constructor<*> = R.layout::class.java.declaredConstructors[0]
            ctor.isAccessible = true // Required for at least API 16, maybe later.
            val layout = ctor.newInstance()

            // There are hidden public fields: abc_list_menu_item_layout for example
            val nonAnkiFieldNames = HashSet<String>()
            nonAnkiFieldNames.addAll(getFieldNames(com.google.android.material.R.layout::class.java))
            nonAnkiFieldNames.addAll(getFieldNames(androidx.preference.R.layout::class.java)) // preference_category_material

            // Names of layouts that should be ignored by the layout inflation test.
            // Currently, ignores layouts that use `FragmentContainerView`
            // with a specified fragment name, as these would currently fail the test, throwing:
            //   UnsupportedOperationException: FragmentContainerView must be within
            //   a FragmentActivity to use android:name="..."
            val ignoredLayoutIds =
                listOf(
                    R.layout.introduction_activity,
                    R.layout.preferences,
                )

            return layout::class.java.fields
                .map { arrayOf<Any>(it.getInt(layout), it.name) }
                .filterNot { (id, name) -> name in nonAnkiFieldNames || id in ignoredLayoutIds }
        }

        private fun <T> getFieldNames(clazz: Class<T>): HashSet<String> {
            val badFields = clazz.fields
            val badFieldNames = HashSet<String>()
            for (f in badFields) {
                badFieldNames.add(f.name)
            }
            return badFieldNames
        }
    }
}
