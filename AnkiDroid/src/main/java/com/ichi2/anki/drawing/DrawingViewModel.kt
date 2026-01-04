/*
 * Copyright (c) 2026 Cabrera Family
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
package com.ichi2.anki.drawing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import androidx.annotation.CheckResult
import androidx.compose.material.MaterialTheme
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Represents a single drawing stroke on the canvas.
 */
data class DrawingPath(
    val path: Path,
    val color: Int,
    val strokeWidth: Float,
)

/**
 * ViewModel for the Drawing screen used to create images for notes.
 * Manages drawing paths, brush settings, and save functionality.
 */
class DrawingViewModel : ViewModel() {
    // Drawing history
    private val _paths = MutableStateFlow<List<DrawingPath>>(emptyList())
    val paths: StateFlow<List<DrawingPath>> = _paths

    // Undo stack
    private val undoStack = mutableListOf<DrawingPath>()

    // Brush settings
    private val _brushColor = MutableStateFlow(Color.TRANSPARENT)
    val brushColor: StateFlow<Int> = _brushColor

    private val _strokeWidth = MutableStateFlow(8f)
    val strokeWidth: StateFlow<Float> = _strokeWidth

    // UI state
    val canUndo = _paths.map { it.isNotEmpty() }

    /**
     * Adds a completed path to the drawing history.
     */
    fun addPath(path: Path) {
        val drawingPath = DrawingPath(
            path = path,
            color = _brushColor.value,
            strokeWidth = _strokeWidth.value,
        )
        _paths.value = _paths.value + drawingPath
        // Clear redo stack when new path is added
        undoStack.clear()
    }

    /**
     * Undoes the last stroke.
     */
    fun undo() {
        val currentPaths = _paths.value
        if (currentPaths.isNotEmpty()) {
            val lastPath = currentPaths.last()
            undoStack.add(lastPath)
            _paths.value = currentPaths.dropLast(1)
        }
    }

    /**
     * Sets the brush color.
     */
    fun setBrushColor(color: Int) {
        _brushColor.value = color
    }

    /**
     * Sets the stroke width.
     */
    fun setStrokeWidth(width: Float) {
        _strokeWidth.value = width
    }

    /**
     * Checks if there are any paths drawn.
     */
    fun hasContent(): Boolean = _paths.value.isNotEmpty()

    /**
     * Saves the current drawing to a file and returns its URI.
     * @param context Context for file operations
     * @param width Canvas width
     * @param height Canvas height
     * @return URI of the saved file, or null if save failed
     */
    @CheckResult
    suspend fun saveDrawing(
        context: Context,
        width: Int,
        height: Int,
    ): Uri? {
        val currentPaths = _paths.value
        if (currentPaths.isEmpty()) {
            Timber.d("No paths to save")
            return null
        }

        return withContext(Dispatchers.IO) {
            try {
                if (width <= 0 || height <= 0) {
                    Timber.w("Invalid dimensions for bitmap: %d x %d", width, height)
                    return@withContext null
                }

                // Create bitmap with white background (like original DrawingActivity)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                
                // Use white background for light brush colors, black for dark
                val avgBrightness = currentPaths.map { 
                    val r = Color.red(it.color)
                    val g = Color.green(it.color)
                    val b = Color.blue(it.color)
                    (r + g + b) / 3
                }.average()
                
                if (avgBrightness > 128) {
                    canvas.drawColor(Color.BLACK)
                } else {
                    canvas.drawColor(Color.WHITE)
                }

                // Draw all paths
                val paint = Paint().apply {
                    isAntiAlias = true
                    isDither = true
                    style = Paint.Style.STROKE
                    strokeJoin = Paint.Join.ROUND
                    strokeCap = Paint.Cap.ROUND
                }

                for (drawingPath in currentPaths) {
                    paint.color = drawingPath.color
                    paint.strokeWidth = drawingPath.strokeWidth
                    canvas.drawPath(drawingPath.path, paint)
                }

                // Save to file
                val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
                val saveDirectory = File(baseDir, "Whiteboard")
                if (!saveDirectory.exists()) {
                    saveDirectory.mkdirs()
                }

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val fileName = "Drawing_$timestamp.jpg"
                val file = File(saveDirectory, fileName)

                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }

                bitmap.recycle()

                Timber.d("Drawing saved to: %s", file.absolutePath)
                
                // Return content URI via FileProvider
                FileProvider.getUriForFile(
                    context,
                    context.applicationContext.packageName + ".apkgfileprovider",
                    file,
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to save drawing")
                null
            }
        }
    }

    companion object {
        // Predefined colors matching reviewer_whiteboard_editor.xml
        val PRESET_COLORS = listOf(
            Color.WHITE,
            Color.BLACK,
            Color.parseColor("#F44336"), // Red
            Color.parseColor("#4CAF50"), // Green
            Color.parseColor("#2196F3"), // Blue
            Color.parseColor("#FFEB3B"), // Yellow
        )
    }
}
