/***************************************************************************************
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference

/**
 * The status of the widget.
 */
object WidgetStatus {
    private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val updateJobRef = AtomicReference<Job?>(null)

    fun updateInBackground(context: Context) {
        val currentJob = updateJobRef.get()
        if (currentJob != null && currentJob.isActive) {
            Timber.d("WidgetStatus.update(): already running")
            return
        }

        // Create the new job
        val newJob = launchUpdateJob(context)

        // Atomically try to install it; only succeeds if still null/completed
        if (updateJobRef.compareAndSet(currentJob, newJob)) {
            Timber.d("WidgetStatus.update(): updating")
        } else {
            // Another thread won the race; cancel our job and log
            newJob.cancel()
            Timber.d("WidgetStatus.update(): lost race, cancelled duplicate job")
        }
    }

    private fun launchUpdateJob(context: Context): Job =
        widgetScope.launch {
            try {
                // Update Heatmap Widget
                if (GlanceAppWidgetManager(context)
                        .getGlanceIds(HeatmapWidget::class.java)
                        .isNotEmpty()
                ) {
                    HeatmapWidget().updateAll(context)
                }

                Timber.v("launchUpdateJob completed")
            } catch (exc: Exception) {
                Timber.w(exc, "failure in widget update")
            }
        }
}
