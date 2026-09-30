/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
        val root: ViewGroup = LinearLayout(targetContext)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
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
