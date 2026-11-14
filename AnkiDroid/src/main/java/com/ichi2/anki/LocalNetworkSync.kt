// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki

import android.content.Context
import android.os.Build
import androidx.annotation.VisibleForTesting
import com.ichi2.utils.NetworkAddressClassifier
import com.ichi2.utils.Permissions

/**
 * Whether a custom-sync connection to [endpoint] would be blocked for lack of
 * [Permissions.ACCESS_LOCAL_NETWORK].
 *
 * @param isCustomSyncEnabled AnkiWeb sync must never prompt, so callers pass it explicitly
 * rather than relying on convention.
 */
fun isLocalNetworkSyncBlocked(
    context: Context,
    endpoint: String?,
    isCustomSyncEnabled: Boolean,
): Boolean =
    shouldBlockLocalNetworkSync(
        sdkInt = Build.VERSION.SDK_INT,
        hasGrant = Permissions.hasLocalNetworkPermission(context),
        isCustomSyncEnabled = isCustomSyncEnabled,
        endpoint = endpoint,
    )

/** Pure decision core of [isLocalNetworkSyncBlocked]; JVM-testable without a Context. */
@VisibleForTesting
fun shouldBlockLocalNetworkSync(
    sdkInt: Int,
    hasGrant: Boolean,
    isCustomSyncEnabled: Boolean,
    endpoint: String?,
): Boolean =
    isCustomSyncEnabled &&
        sdkInt >= Build.VERSION_CODES.CINNAMON_BUN &&
        !hasGrant &&
        NetworkAddressClassifier.isLocalNetworkUrl(endpoint)
