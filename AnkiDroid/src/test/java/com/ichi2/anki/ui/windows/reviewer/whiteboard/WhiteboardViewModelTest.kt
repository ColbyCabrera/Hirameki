/*
 *  Copyright (c) 2026 the Anki-Android contributors
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
package com.ichi2.anki.ui.windows.reviewer.whiteboard

import android.content.Context
import android.graphics.Path
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WhiteboardViewModelTest : RobolectricTest() {
    private lateinit var viewModel: WhiteboardViewModel

    @Before
    override fun setUp() {
        super.setUp()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sharedPreferences = context.getSharedPreferences("whiteboard_test_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().clear().commit()
        val repository = WhiteboardRepository(sharedPreferences)
        viewModel = WhiteboardViewModel(repository)
        viewModel.loadState(isDarkMode = false)
    }

    @Test
    fun `reset clears paths and clears undo and redo stacks`() {
        val path = Path().apply { lineTo(10f, 10f) }
        viewModel.addPath(path)

        assertThat("Paths should contain 1 path after drawing", viewModel.paths.value, hasSize(1))
        assertThat("Can undo should be true", viewModel.canUndo.value, equalTo(true))

        viewModel.reset()

        assertThat("Paths should be empty after reset", viewModel.paths.value.isEmpty(), equalTo(true))
        assertThat("Can undo should be false after reset", viewModel.canUndo.value, equalTo(false))
        assertThat("Can redo should be false after reset", viewModel.canRedo.value, equalTo(false))
    }

    @Test
    fun `clearCanvas empties paths but preserves undo history`() {
        val path = Path().apply { lineTo(20f, 20f) }
        viewModel.addPath(path)

        assertThat("Paths should contain 1 path", viewModel.paths.value, hasSize(1))

        viewModel.clearCanvas()

        assertThat("Paths should be empty after clearCanvas", viewModel.paths.value.isEmpty(), equalTo(true))
        assertThat("Can undo should remain true after clearCanvas", viewModel.canUndo.value, equalTo(true))

        viewModel.undo()

        assertThat("Paths should be restored after undo", viewModel.paths.value, hasSize(1))
    }

    @Test
    fun `undo and redo work as expected for drawing actions`() {
        val path1 = Path().apply { lineTo(10f, 10f) }
        val path2 = Path().apply { lineTo(20f, 20f) }

        viewModel.addPath(path1)
        viewModel.addPath(path2)

        assertThat("Paths should contain 2 paths", viewModel.paths.value, hasSize(2))

        viewModel.undo()
        assertThat("Paths should contain 1 path after 1 undo", viewModel.paths.value, hasSize(1))
        assertThat("Can redo should be true", viewModel.canRedo.value, equalTo(true))

        viewModel.redo()
        assertThat("Paths should contain 2 paths after redo", viewModel.paths.value, hasSize(2))
    }
}
