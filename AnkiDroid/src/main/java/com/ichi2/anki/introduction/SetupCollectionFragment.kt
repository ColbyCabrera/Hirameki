/*
 *  Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
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

/****************************************************************************************
 *                                                                                      *
 * Copyright (c) 2021 Shridhar Goel <shridhar.goel@gmail.com>                           *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki.introduction

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import kotlinx.parcelize.Parcelize

class SetupCollectionFragment : Fragment() {
    private var acknowledged by mutableStateOf(false)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                AnkiDroidTheme {
                    IntroductionScreen(
                        acknowledged = acknowledged,
                        onAcknowledgedChange = { acknowledged = it },
                        onGetStarted = { setResult(CollectionSetupOption.DeckPickerWithNewCollection) },
                        onSync = { setResult(CollectionSetupOption.SyncFromExistingAccount) },
                    )
                }
            }
        }
    }

    private fun setResult(option: CollectionSetupOption) {
        setFragmentResult(FRAGMENT_KEY, bundleOf(RESULT_KEY to option))
    }

    @Parcelize
    enum class CollectionSetupOption : Parcelable {
        /** Continues to the DeckPicker with a new collection */
        DeckPickerWithNewCollection,

        /** Syncs an existing profile from AnkiWeb */
        SyncFromExistingAccount,
    }

    companion object {
        const val FRAGMENT_KEY = "collectionSetup"
        const val RESULT_KEY = "result"
    }
}
