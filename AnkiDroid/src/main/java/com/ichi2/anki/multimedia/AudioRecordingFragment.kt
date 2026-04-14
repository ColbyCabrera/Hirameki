/*
 * Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.multimedia

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.R
import com.ichi2.anki.multimedia.MultimediaActivity.Companion.MULTIMEDIA_RESULT
import com.ichi2.anki.multimedia.MultimediaActivity.Companion.MULTIMEDIA_RESULT_FIELD_INDEX
import com.ichi2.anki.multimedia.audio.AudioRecorderViewModel
import com.ichi2.anki.multimedia.audio.ui.compose.AudioRecorderScreen
import com.ichi2.anki.ui.compose.theme.AnkiDroidTheme
import com.ichi2.utils.FileUtil
import com.ichi2.utils.Permissions
import kotlinx.coroutines.launch
import timber.log.Timber

class AudioRecordingFragment : MultimediaFragment(R.layout.fragment_audio_recording) {
    override val title: String
        get() = resources.getString(R.string.multimedia_editor_field_editing_audio)

    private val audioRecorderViewModel: AudioRecorderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ankiCacheDirectory = FileUtil.getAnkiCacheDirectory(requireContext(), "temp-media")
        if (ankiCacheDirectory == null) {
            Timber.e("createUI() failed to get cache directory")
            showErrorDialog(errorMessage = resources.getString(R.string.multimedia_editor_failed))
            return
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            Timber.d("Audio permission granted")
            initializeComposeUI()
        } else {
            Timber.d("Audio permission denied")
            showErrorDialog(resources.getString(R.string.multimedia_editor_audio_permission_refused))
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        if (!hasMicPermission()) {
            return
        }

        (requireActivity() as? MultimediaActivity)?.setToolbarVisible(false)

        initializeComposeUI()
        setupDoneAction()
        setupCloseAction()
    }

    private fun hasMicPermission(): Boolean {
        if (!Permissions.canRecordAudio(requireContext())) {
            Timber.i("Requesting Audio Permissions")
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return false
        }
        return true
    }

    override fun onStop() {
        super.onStop()
        audioRecorderViewModel.stopAllAndRelease()
    }

    private fun setupDoneAction() {
        lifecycleScope.launch {
            audioRecorderViewModel.uiState.collect { state ->
                val savedFile = state.savedFile
                if (savedFile != null && state.state == AudioRecorderViewModel.RecordingState.PlaybackReady) {
                    viewModel.updateCurrentMultimediaPath(savedFile)
                    viewModel.updateMediaFileLength(savedFile.length())
                    onDone()
                } else if (state.state == AudioRecorderViewModel.RecordingState.Idle && savedFile == null) {
                    // Handled discard if needed
                }
            }
        }
    }

    private fun setupCloseAction() {
        lifecycleScope.launch {
            audioRecorderViewModel.uiState.collect { state ->
                if (state.shouldClose) {
                    requireActivity().finish()
                }
            }
        }
    }

    private fun onDone() {
        Timber.d("AudioRecordingFragment:: Done action triggered")
        if (viewModel.selectedMediaFileSize == 0L) {
            Timber.d("Audio length not valid")
            return
        }

        field.mediaFile = viewModel.currentMultimediaPath.value
        field.hasTemporaryMedia = true

        val resultData = Intent().apply {
            putExtra(MULTIMEDIA_RESULT, field)
            putExtra(MULTIMEDIA_RESULT_FIELD_INDEX, indexValue)
        }
        requireActivity().setResult(AppCompatActivity.RESULT_OK, resultData)
        requireActivity().finish()
    }

    private fun initializeComposeUI() {
        try {
            val composeView = view?.findViewById<ComposeView>(R.id.compose_view)
            composeView?.apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    AnkiDroidTheme {
                        AudioRecorderScreen(
                            title = title,
                            viewModel = audioRecorderViewModel,
                            onBackClick = {
                                if (audioRecorderViewModel.uiState.value.state != AudioRecorderViewModel.RecordingState.Idle) {
                                    audioRecorderViewModel.processIntent(AudioRecorderViewModel.Intent.DiscardRecording)
                                }
                                audioRecorderViewModel.processIntent(AudioRecorderViewModel.Intent.CloseRecorder)
                            },
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "unable to add the audio recorder to fragment")
            CrashReportService.sendExceptionReport(e, "Unable to create recorder compose view")
            showErrorDialog()
        }
    }

    companion object {
        fun getIntent(
            context: Context,
            multimediaExtra: MultimediaActivityExtra,
        ): Intent = MultimediaActivity.getIntent(
            context,
            AudioRecordingFragment::class,
            multimediaExtra,
        )
    }
}
