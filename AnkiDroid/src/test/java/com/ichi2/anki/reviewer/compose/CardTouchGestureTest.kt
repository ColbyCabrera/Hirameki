/*
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.reviewer.compose

import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class CardTouchGestureTest {
    @Test
    fun `interactive element script looks up the touched point`() {
        val script = interactiveElementScript(0.25f, 0.75f)

        assertContains(script, "0.25 * vv.width")
        assertContains(script, "0.75 * vv.height")
    }

    @Test
    fun `interactive element script stays inside the card`() {
        val script = interactiveElementScript(-0.5f, 1.5f)

        assertContains(script, "0.0 * vv.width")
        assertContains(script, "1.0 * vv.height")
        assertFalse("-0.5" in script)
    }

    @Test
    fun `interactive element script skips the elements upstream skips`() {
        val script = interactiveElementScript(0.5f, 0.5f)

        for (tag in listOf("\"A\"", "\"BUTTON\"", "\"VIDEO\"", "\"SUMMARY\"", "\"INPUT\"", "onclick", "isContentEditable", "tappable")) {
            assertContains(script, tag)
        }
    }
}
