/*
 *  Copyright (c) 2026 Hirameki contributors
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
package com.ichi2.anki.drawing

import android.graphics.Path
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DrawingViewModelTest : RobolectricTest() {
    private lateinit var viewModel: DrawingViewModel

    @Before
    override fun setUp() {
        super.setUp()
        viewModel = DrawingViewModel()
    }

    @Test
    fun `undo and redo walk the strokes back and forth`() {
        val first = Path().apply { lineTo(10f, 10f) }
        val second = Path().apply { lineTo(20f, 20f) }
        assertThat("an empty canvas has nothing to undo", viewModel.canUndo.value, equalTo(false))
        viewModel.addPath(first)
        viewModel.addPath(second)
        assertThat(viewModel.canUndo.value, equalTo(true))

        viewModel.undo()
        assertThat(strokes(), equalTo(listOf(first)))
        assertThat(viewModel.canUndo.value, equalTo(true))
        assertThat(viewModel.canRedo.value, equalTo(true))

        viewModel.undo()
        assertThat(strokes(), equalTo(emptyList<Path>()))
        assertThat("undoing the last stroke ends undo", viewModel.canUndo.value, equalTo(false))

        viewModel.redo()
        viewModel.redo()
        assertThat("redo restores strokes in drawing order", strokes(), equalTo(listOf(first, second)))
        assertThat(viewModel.canUndo.value, equalTo(true))
        assertThat(viewModel.canRedo.value, equalTo(false))
    }

    @Test
    fun `a new stroke or a clear drops the redo history`() {
        val first = Path().apply { lineTo(10f, 10f) }
        viewModel.addPath(first)
        viewModel.undo()

        viewModel.addPath(Path().apply { lineTo(30f, 30f) })
        assertThat("a new stroke ends redo", viewModel.canRedo.value, equalTo(false))

        viewModel.undo()
        viewModel.clearCanvas()
        assertThat(strokes(), equalTo(emptyList<Path>()))
        assertThat("clearing ends undo", viewModel.canUndo.value, equalTo(false))
        assertThat("clearing ends redo", viewModel.canRedo.value, equalTo(false))
        viewModel.redo()
        assertThat("nothing comes back after a clear", strokes(), equalTo(emptyList<Path>()))
    }

    private fun strokes(): List<Path> = viewModel.paths.value.map { it.path }
}
