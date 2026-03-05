/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki

import android.content.Intent
import androidx.core.content.edit
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.exception.UnknownDatabaseVersionException
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.testutils.DbUtils
import com.ichi2.testutils.common.Flaky
import com.ichi2.testutils.common.OS
import com.ichi2.utils.ResourceLoader
import kotlinx.coroutines.flow.first
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@KotlinCleanup("SPMockBuilder")
@RunWith(ParameterizedRobolectricTestRunner::class)
class DeckPickerOnDiskTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @ParameterizedRobolectricTestRunner.Parameter
    @JvmField // required for Parameter
    var qualifiers: String? = null

    companion object {
        @ParameterizedRobolectricTestRunner.Parameters
        @JvmStatic // required for initParameters
        fun initParameters(): Collection<String> = listOf("normal", "xlarge")
    }

    @Before
    fun before() {
        RuntimeEnvironment.setQualifiers(qualifiers)
        getPreferences().edit {
            putBoolean(IntroductionActivity.INTRODUCTION_SLIDES_SHOWN, true)
        }
    }

    @Test
    @Flaky(OS.WINDOWS)
    fun version16CollectionOpens() = runTest {
        try {
            setupColV16()
            InitialActivityWithConflictTest.setupForValid(targetContext)
            val deckPicker: DeckPicker = super.startActivityNormallyOpenCollectionWithIntent(
                DeckPicker::class.java,
                Intent(),
            )
            advanceRobolectricLooper()
            assertThat(
                "Collection should now be open",
                CollectionManager.isOpenUnsafe(),
            )
            assertThat(
                CollectionType.SCHEMA_V_16.isCollection(
                    col,
                ),
                equalTo(true),
            )
            assertThat(
                "Decks should be visible",
                deckPicker.viewModel.flowOfDeckList.first().data.size,
                equalTo(1),
            )
        } finally {
            InitialActivityWithConflictTest.setupForDefault()
        }
    }

    @Ignore("needs refactoring")
    @Test
    fun corruptVersion16CollectionShowsDatabaseError() {
        try {
            setupColV16()

            // corrupt col
            DbUtils.performQuery(targetContext, "drop table decks")
            InitialActivityWithConflictTest.setupForValid(targetContext)
            val deckPicker = super.startActivityNormallyOpenCollectionWithIntent(
                DeckPicker::class.java,
                Intent(),
            )
            advanceRobolectricLooper()
            assertThat(
                "Collection should not be open",
                !CollectionManager.isOpenUnsafe(),
            )
            val dialogFragment =
                deckPicker.supportFragmentManager.fragments.firstOrNull { it is com.ichi2.anki.dialogs.DatabaseErrorDialog } as? com.ichi2.anki.dialogs.DatabaseErrorDialog
            assertNotNull(dialogFragment)
            val dialogType = androidx.core.os.BundleCompat.getParcelable(
                dialogFragment.requireArguments(), "dialog", DatabaseErrorDialogType::class.java
            )
            assertThat(
                "An error dialog should be displayed",
                dialogType,
                equalTo(DatabaseErrorDialogType.DIALOG_LOAD_FAILED),
            )
        } finally {
            InitialActivityWithConflictTest.setupForDefault()
        }
    }

    @Test
    fun futureSchemaShowsError() {
        try {
            setupColV250()
            InitialActivityWithConflictTest.setupForValid(targetContext)
            val deckPicker = super.startActivityNormallyOpenCollectionWithIntent(
                DeckPicker::class.java,
                Intent(),
            )
            advanceRobolectricLooper()
            assertThat(
                "Collection should not be open",
                !CollectionManager.isOpenUnsafe(),
            )
            val dialogFragment =
                deckPicker.supportFragmentManager.fragments.firstOrNull { it is com.ichi2.anki.dialogs.DatabaseErrorDialog } as? com.ichi2.anki.dialogs.DatabaseErrorDialog
            assertNotNull(dialogFragment)
            val dialogType = androidx.core.os.BundleCompat.getParcelable(
                dialogFragment.requireArguments(), "dialog", DatabaseErrorDialogType::class.java
            )
            assertThat(
                "An error dialog should be displayed",
                dialogType,
                equalTo(DatabaseErrorDialogType.INCOMPATIBLE_DB_VERSION),
            )
            assertThat(
                CollectionHelper.getDatabaseVersion(targetContext),
                equalTo(250),
            )
        } catch (e: UnknownDatabaseVersionException) {
            assertThat("no exception should be thrown", false, equalTo(true))
        } finally {
            InitialActivityWithConflictTest.setupForDefault()
        }
    }

    private fun setupColV16() {
        useCollection(CollectionType.SCHEMA_V_16)
    }

    private fun setupColV250() {
        useCollection(CollectionType.SCHEMA_V_250)
    }

    private fun useCollection(collectionType: CollectionType) {
        // load asset into temp
        val path = ResourceLoader.getTempCollection(targetContext, collectionType.assetFile)
        val p = File(path)
        assertThat(p.isFile, equalTo(true))
        val collectionDirectory = p.parent

        // set collection path
        targetContext.sharedPrefs().edit {
            putString(CollectionHelper.PREF_COLLECTION_PATH, collectionDirectory)
        }

        // ensure collection not loaded yet
        assertThat(
            "collection should not be loaded",
            CollectionManager.isOpenUnsafe(),
            equalTo(false),
        )
    }

    enum class CollectionType(
        val assetFile: String,
        private val deckName: String,
    ) {
        SCHEMA_V_16("schema16.anki2", "ThisIsSchema16"), SCHEMA_V_250(
            "schema250.anki2",
            "ThisIsSchema250",
        ), ;

        fun isCollection(col: com.ichi2.anki.libanki.Collection): Boolean =
            col.decks.byName(deckName) != null
    }
}
