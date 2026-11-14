// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.previewer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.os.BundleCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.ichi2.anki.R
import com.ichi2.anki.previewer.TemplatePreviewerFragment.Companion.ARGS_KEY
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Container for [TemplatePreviewerFragment] that works as a standalone page
 * by including a toolbar and a TabLayout for changing the current template.
 */
class TemplatePreviewerPage : Fragment(R.layout.template_previewer_container) {
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        view.findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        val arguments = BundleCompat.getParcelable(requireArguments(), ARGS_KEY, TemplatePreviewerArguments::class.java)!!
        val fragment = TemplatePreviewerFragment.newInstance(arguments)
        childFragmentManager.commitNow {
            replace(R.id.fragment_container, fragment)
        }

        val viewModel = fragment.viewModel
        val tabLayout = view.findViewById<TabLayout>(R.id.tab_layout)

        lifecycleScope.launch {
            val cardsWithEmptyFronts = viewModel.cardsWithEmptyFronts?.await()
            for ((index, templateName) in viewModel.getTemplateNames().withIndex()) {
                val tabTitle =
                    if (cardsWithEmptyFronts?.get(index) == true) {
                        getString(R.string.card_previewer_empty_front_indicator, templateName)
                    } else {
                        templateName
                    }
                val newTab = tabLayout.newTab().setText(tabTitle)
                tabLayout.addTab(newTab)
            }
            tabLayout.selectTab(tabLayout.getTabAt(viewModel.getCurrentTabIndex()))
            tabLayout.addOnTabSelectedListener(
                object : OnTabSelectedListener {
                    override fun onTabSelected(tab: TabLayout.Tab) {
                        Timber.v("Selected tab %d", tab.position)
                        viewModel.onTabSelected(tab.position)
                    }

                    override fun onTabUnselected(tab: TabLayout.Tab) {
                        // do nothing
                    }

                    override fun onTabReselected(tab: TabLayout.Tab) {
                        // do nothing
                    }
                },
            )
        }
    }

    companion object {
        fun getIntent(
            context: Context,
            arguments: TemplatePreviewerArguments,
        ): Intent =
            CardViewerActivity.getIntent(
                context,
                TemplatePreviewerPage::class,
                Bundle().apply { putParcelable(ARGS_KEY, arguments) },
            )
    }
}
