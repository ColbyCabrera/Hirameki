/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.GET_PERMISSIONS
import android.os.Build
import android.os.Environment
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.ichi2.anki.common.utils.android.isRobolectric
import com.ichi2.compat.CompatHelper.Companion.getPackageInfoCompat
import com.ichi2.compat.PackageInfoFlagsCompat
import com.ichi2.utils.Permissions.MANAGE_EXTERNAL_STORAGE
import com.ichi2.utils.Permissions.arePermissionsDefinedInManifest
import com.ichi2.utils.Permissions.isExternalStorageManager
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber

object Permissions {
    const val MANAGE_EXTERNAL_STORAGE = "android.permission.MANAGE_EXTERNAL_STORAGE"

    /**
     * Runtime permission gating LAN access on Android 17+.
     * Declared in `AndroidManifest.xml`; enforced only for `targetSdk >= 37`.
     * String literal (not `Manifest.permission`) so checks compile/run on older SDKs.
     */
    const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    val tiramisuPhotosAndVideosPermissions =
        listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        )

    /**
     * The name of the "post notification" permission on API where it's defined.
     */
    val postNotification =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            null
        }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    val tiramisuAudioPermission = Manifest.permission.READ_MEDIA_AUDIO

    val legacyStorageAccessPermissions =
        listOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )

    val recordAudioPermission = Manifest.permission.RECORD_AUDIO

    fun canRecordAudio(context: Context): Boolean = hasPermission(context, recordAudioPermission)

    /**
     * Whether the app is granted [permission]
     *
     * Same as [androidx.core.content.ContextCompat.checkSelfPermission] except it corrects a bug related to [MANAGE_EXTERNAL_STORAGE].
     */
    fun hasPermission(
        context: Context,
        permission: String,
    ): Boolean {
        if (permission == MANAGE_EXTERNAL_STORAGE) {
            // checkSelfPermission doesn't return PERMISSION_GRANTED, even if it's granted.
            return isExternalStorageManager()
        }

        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Whether the app is granted all permission of [permissions]
     */
    fun hasAllPermissions(
        context: Context,
        permissions: Collection<String>,
    ): Boolean = permissions.all { hasPermission(context, it) }

    fun isExternalStorageManager(): Boolean {
        // BUG: Environment.isExternalStorageManager() crashes under robolectric
        // https://github.com/robolectric/robolectric/issues/7300
        if (isRobolectric) {
            return false // TODO: handle tests with both 'true' and 'false'
        }
        return Environment.isExternalStorageManager()
    }

    /**
     * On < Android 11, returns false.
     * On >= Android 11, returns [isExternalStorageManager]
     */
    fun isExternalStorageManagerCompat(): Boolean = isExternalStorageManager()

    /**
     * Check if we have write access permission to the external storage
     * @param context
     * @return
     */
    @JvmStatic // unit tests were flaky - maybe remove later
    private fun hasStorageWriteAccessPermission(
        context: Context,
    ): Boolean = hasPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)

    /**
     * Check if we have read access permission to the external storage
     * @param context
     * @return
     */
    @JvmStatic // unit tests were flaky - maybe remove later
    private fun hasStorageReadAccessPermission(
        context: Context,
    ): Boolean = hasPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)

    /**
     * Check if we have read and write access permission to the external storage
     * Note: This can return true >= R on a debug build or if storage is preserved
     *
     * @see com.ichi2.anki.IntentHandler.grantedStoragePermissions
     *
     * @param context
     */
    @JvmStatic // unit tests were flaky - maybe remove later
    fun hasLegacyStorageAccessPermission(context: Context): Boolean =
        hasStorageReadAccessPermission(context) && hasStorageWriteAccessPermission(context)

    /**
     * Detects if permissions are defined via <uses-permission> in the Manifest.
     * This does **not** mean the permission has been granted.
     * Intention is to be used when a permissions may be changed by build flavours
     *
     * Example:

     * * Play => no 'manage external storage'
     *
     * @param permissions One or more permission strings, typically defined in [Manifest.permission]
     * @return `true` if all permissions were granted. `false` otherwise, or if an error occurs.
     */
    fun Context.arePermissionsDefinedInManifest(
        packageName: String,
        vararg permissions: String,
    ): Boolean {
        try {
            val permissionsInManifest = getPermissionsDefinedInManifest(packageName) ?: return false
            return permissions.all { permissionsInManifest.contains(it) }
        } catch (e: Exception) {
            Timber.w(e)
        }
        return false
    }

    private fun Context.getPermissionsDefinedInManifest(packageName: String): Array<out String>? =
        try {
            // requestedPermissions => <uses-permission> in manifest
            val flags = PackageInfoFlagsCompat.of(GET_PERMISSIONS.toLong())
            getPackageInfoCompat(packageName, flags)!!.requestedPermissions
        } catch (e: Exception) {
            Timber.w(e)
            null
        }

    /**
     * @see Context.arePermissionsDefinedInManifest
     */
    fun Context.arePermissionsDefinedInAnkiDroidManifest(vararg permissions: String) =
        this.arePermissionsDefinedInManifest(this.packageName, *permissions)

    /**
     * Whether it would be possible to manage external storage (potentially after requesting permission).
     */
    fun canManageExternalStorage(context: Context): Boolean {
        // TODO: See if we can move this to a testing manifest
        if (isRobolectric) {
            return false
        }
        return context.arePermissionsDefinedInAnkiDroidManifest(MANAGE_EXTERNAL_STORAGE)
    }

    fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /**
     * Whether the OS enforces [ACCESS_LOCAL_NETWORK] (Android 17 / API 37+, i.e. `CINNAMON_BUN`).
     * Below 37, `INTERNET` implicitly grants LAN access.
     */
    fun requiresLocalNetworkPermission(): Boolean = Build.VERSION.SDK_INT >= 37

    fun hasLocalNetworkPermission(context: Context): Boolean =
        !requiresLocalNetworkPermission() || hasPermission(context, ACCESS_LOCAL_NETWORK)

    /**
     * Whether [url] points at a LAN destination that Android 17 gates behind
     * [ACCESS_LOCAL_NETWORK]. Public hosts and loopback return false.
     */
    fun isLocalNetworkUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val host = url.toHttpUrlOrNull()?.host?.lowercase() ?: return false
        if (host == "localhost" || host == "::1") return false
        if (isLoopbackIpv4(host)) return false
        if (
            host.endsWith(".local") ||
            host.endsWith(".lan") ||
            host.endsWith(".home") ||
            host.endsWith(".home.arpa") ||
            host.endsWith(".internal")
        ) {
            return true
        }
        if (!host.contains(".") && !host.contains(":")) return true // single-label e.g. http://nas:8080
        if (isPrivateIpv4(host)) return true
        // IPv6 link-local fe80::/10, ULA fc00::/7, multicast ff00::/8; ::1 already exempted
        if (host.contains(":") && (isIpv6LinkLocal(host) || isIpv6Ula(host) || host.startsWith("ff"))) {
            return true
        }
        return false
    }

    /** 127.0.0.0/8 loopback; hostnames merely starting with "127." (e.g. 127.local) don't count. */
    private fun isLoopbackIpv4(host: String): Boolean {
        val parts = host.split(".")
        if (parts.size != 4 || parts[0] != "127") return false
        return parts.all {
            val octet = it.toIntOrNull()
            octet != null && octet in 0..255
        }
    }

    /** fe80::/10 link-local (fe80..febf first hextet). */
    private fun isIpv6LinkLocal(host: String): Boolean {
        val firstHextet = host.split(":").firstOrNull()?.toIntOrNull(16) ?: return false
        return firstHextet in 0xFE80..0xFEBF
    }

    /** fc00::/7 unique-local addresses. */
    private fun isIpv6Ula(host: String): Boolean {
        val firstHextet = host.split(":").firstOrNull()?.toIntOrNull(16) ?: return false
        return firstHextet in 0xFC00..0xFDFF
    }

    private fun isPrivateIpv4(host: String): Boolean {
        val parts = host.split(".")
        if (parts.size != 4) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false
        val (a, b) = octets
        return when {
            a == 10 -> true
            a == 172 && b in 16..31 -> true
            a == 192 && b == 168 -> true
            a == 169 && b == 254 -> true // link-local
            a == 100 && b in 64..127 -> true // CGNAT
            a in 224..239 -> true // multicast
            host == "255.255.255.255" -> true // broadcast
            else -> false
        }
    }

    /**
     * Whether a sync to [endpoint] would be blocked for lack of LAN permission.
     * Callers should additionally ensure custom sync is enabled so AnkiWeb never prompts.
     */
    fun isLocalNetworkSyncBlocked(
        context: Context,
        endpoint: String?,
    ): Boolean =
        requiresLocalNetworkPermission() &&
            !hasLocalNetworkPermission(context) &&
            isLocalNetworkUrl(endpoint)
}
