// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Colby Cabrera <colbycabrera.wd@gmail.com>

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
