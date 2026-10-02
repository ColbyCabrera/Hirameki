/*
 * Copyright (c) 2026 Colby Cabrera <gdthyispro@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.ankiconnect

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.ichi2.anki.Channel
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.preferences.sharedPrefs
import timber.log.Timber

class AnkiConnectService : Service() {
    private var server: AnkiConnectServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Timber.i("AnkiConnectService: Stop requested")
                try {
                    val prefs = applicationContext.sharedPrefs()
                    prefs.edit {
                        putBoolean(getString(R.string.ankiconnect_enable_key), false)
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to update preference on AnkiConnect stop")
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            else -> {
                startServer()
            }
        }
        return START_STICKY
    }

    private fun startServer() {
        if (server != null) return
        try {
            val port = AnkiConnectServer.DEFAULT_PORT
            val newServer = AnkiConnectServer(port)
            newServer.start()
            server = newServer
            isRunning = true
            Timber.i("AnkiConnect server started on port %d", port)

            val notification = createNotification(port)
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to start AnkiConnect server")
            stopSelf()
        }
    }

    private fun createNotification(port: Int): Notification {
        val openAppIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, DeckPicker::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val stopIntent =
            PendingIntent.getService(
                this,
                1,
                Intent(this, AnkiConnectService::class.java).apply { action = ACTION_STOP },
                PendingIntent.FLAG_IMMUTABLE,
            )

        return NotificationCompat
            .Builder(this, Channel.ANKICONNECT.id)
            .setSmallIcon(R.drawable.ic_star_notify)
            .setContentTitle(getString(R.string.ankiconnect_service_title))
            .setContentText(getString(R.string.ankiconnect_service_running, port))
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .addAction(R.drawable.close_icon, getString(R.string.ankiconnect_stop), stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            server?.stop()
            server = null
            isRunning = false
            Timber.i("AnkiConnect server stopped")
        } catch (e: Exception) {
            Timber.e(e, "Error stopping AnkiConnect server")
        }
    }

    companion object {
        const val NOTIFICATION_ID = 8765
        const val ACTION_START = "com.ichi2.anki.ankiconnect.START"
        const val ACTION_STOP = "com.ichi2.anki.ankiconnect.STOP"

        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent =
                Intent(context, AnkiConnectService::class.java).apply {
                    action = ACTION_START
                }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent =
                Intent(context, AnkiConnectService::class.java).apply {
                    action = ACTION_STOP
                }
            context.startService(intent)
        }
    }
}
