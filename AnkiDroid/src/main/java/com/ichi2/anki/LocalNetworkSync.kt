/*
 Copyright (c) 2026 Colby Cabrera <colby.cabrera@gmail.com>
 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.
 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.
 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
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
