// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2023 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.bottomsheet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ichi2.anki.R

class ImageOcclusionBottomSheetFragment : BottomSheetDialogFragment() {
    var listener: ImagePickerListener? = null

    interface ImagePickerListener {
        fun onCameraClicked()

        fun onGalleryClicked()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.image_occlusion_options_layout, container, false)

        view.findViewById<LinearLayout>(R.id.occlusion_action_camera).setOnClickListener {
            listener?.onCameraClicked()
            dismiss()
        }
        view.findViewById<LinearLayout>(R.id.occlusion_action_gallery).setOnClickListener {
            listener?.onGalleryClicked()
            dismiss()
        }

        return view
    }

    override fun onStart() {
        super.onStart()
        val behavior = BottomSheetBehavior.from(requireView().parent as View)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }
}
