// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2023 Brayan Oliveira <brayandso.dev@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2025 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.ui.windows.permissions

import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import com.ichi2.anki.R
import com.ichi2.utils.Permissions
import com.ichi2.utils.Permissions.canManageExternalStorage

/**
 * Permissions screen for requesting permissions at and above API 30,
 * if the user [canManageExternalStorage], which isn't possible in the play store.
 *
 * Requested permissions:
 * 1. All files access: [Permissions.MANAGE_EXTERNAL_STORAGE].
 *   Used for saving the collection in a public directory
 *   which isn't deleted when the app is uninstalled
 */
class PermissionsStartingAt30Fragment : PermissionsFragment(R.layout.permissions_starting_at_30) {
    private val accessAllFilesLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) {
            if (hasAllPermissions()) {
                requireActivity().finish()
            }
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        view
            .findViewById<PermissionsItem>(R.id.all_files_permission)
            .requestExternalStorageOnClick(accessAllFilesLauncher)
    }
}
