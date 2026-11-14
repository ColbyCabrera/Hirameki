// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.previewer

import android.os.Bundle
import android.view.View
import androidx.core.os.BundleCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.ichi2.anki.R
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.anki.utils.ext.sharedPrefs
import com.ichi2.anki.workarounds.SafeWebViewLayout
import com.ichi2.utils.BundleUtils.getNullableInt
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class TemplatePreviewerFragment :
    CardViewerFragment(R.layout.template_previewer),
    BaseSnackbarBuilderProvider {
    override val viewModel: TemplatePreviewerViewModel by viewModels {
        val arguments = BundleCompat.getParcelable(requireArguments(), ARGS_KEY, TemplatePreviewerArguments::class.java)!!
        TemplatePreviewerViewModel.factory(arguments)
    }
    override val webView: SafeWebViewLayout
        get() = requireView().findViewById(R.id.webview)

    override val baseSnackbarBuilder: SnackbarBuilder
        get() = { anchorView = this@TemplatePreviewerFragment.view?.findViewById(R.id.show_answer) }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        val showAnswerButton =
            view.findViewById<MaterialButton>(R.id.show_answer).apply {
                setOnClickListener { viewModel.toggleShowAnswer() }
            }
        viewModel.showingAnswer
            .onEach { showingAnswer ->
                showAnswerButton.text =
                    if (showingAnswer) {
                        getString(R.string.hide_answer)
                    } else {
                        getString(R.string.show_answer)
                    }
            }.launchIn(lifecycleScope)

        if (sharedPrefs().getBoolean("safeDisplay", false)) {
            view.findViewById<MaterialCardView>(R.id.webview_container).elevation = 0F
        }

        arguments?.getNullableInt(ARG_BACKGROUND_OVERRIDE_COLOR)?.let { color ->
            view.setBackgroundColor(color)
        }
    }

    companion object {
        const val ARGS_KEY = "templatePreviewerArgs"
        private const val ARG_BACKGROUND_OVERRIDE_COLOR = "arg_background_override_color"

        /**
         * @param backgroundOverrideColor optional color to be used as background on the root view
         * of this fragment
         */
        fun newInstance(
            arguments: TemplatePreviewerArguments,
            backgroundOverrideColor: Int? = null,
        ): TemplatePreviewerFragment =
            TemplatePreviewerFragment().apply {
                val args = Bundle().apply { putParcelable(ARGS_KEY, arguments) }
                backgroundOverrideColor?.let { args.putInt(ARG_BACKGROUND_OVERRIDE_COLOR, backgroundOverrideColor) }
                this.arguments = args
            }
    }
}
