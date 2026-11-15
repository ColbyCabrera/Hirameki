// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Shridhar Goel <shridhar.goel@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2025-2026 Colby Cabrera <colbycabrera.wd@gmail.com>

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
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import kotlinx.parcelize.Parcelize

class SetupCollectionFragment : Fragment() {
    private var acknowledged by mutableStateOf(false)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
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

    private fun setResult(option: CollectionSetupOption) {
        setFragmentResult(FRAGMENT_KEY, Bundle().apply { putParcelable(RESULT_KEY, option) })
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
