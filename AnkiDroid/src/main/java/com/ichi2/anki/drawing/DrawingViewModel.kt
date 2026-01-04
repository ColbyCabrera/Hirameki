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
import com.ichi2.anki.ui.windows.reviewer.whiteboard.BrushInfo
import com.ichi2.anki.ui.windows.reviewer.whiteboard.EraserMode
import com.ichi2.anki.ui.windows.reviewer.whiteboard.ToolbarAlignment
import com.ichi2.anki.ui.windows.reviewer.whiteboard.WhiteboardRepository
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
    val isEraser: Boolean = false,
)

/**
 * ViewModel for the Drawing screen used to create images for notes.
 * Manages drawing paths, brush settings, and save functionality.
 */
class DrawingViewModel : ViewModel() {
    // Drawing history
    private val _paths = MutableStateFlow<List<DrawingPath>>(emptyList())
    val paths: StateFlow<List<DrawingPath>> = _paths

    // Undo/Redo stack
    private val undoStack = mutableListOf<DrawingPath>()
    private val redoStack = mutableListOf<DrawingPath>()
    
    val canUndo = _paths.map { it.isNotEmpty() }
    val canRedo = MutableStateFlow(false)

    // Brush settings (Active State)
    private val _brushColor = MutableStateFlow(Color.TRANSPARENT)
    val brushColor: StateFlow<Int> = _brushColor

    private val _strokeWidth = MutableStateFlow(8f)
    val strokeWidth: StateFlow<Float> = _strokeWidth

    // Toolbar State
    val brushes = MutableStateFlow<List<BrushInfo>>(emptyList())
    val activeBrushIndex = MutableStateFlow(0)
    val isEraserActive = MutableStateFlow(false)
    val eraserMode = MutableStateFlow(EraserMode.INK)
    val isStylusOnlyMode = MutableStateFlow(false)
    val toolbarAlignment = MutableStateFlow(ToolbarAlignment.BOTTOM)
    
    init {
        // Initialize brushes with presets
        val initialBrushes = PRESET_COLORS.map { 
            BrushInfo(it, 8f) // Default stroke width
        }
        brushes.value = initialBrushes
    }
    
    fun initializeWithDefaultColor(color: Int) {
        if (_brushColor.value == Color.TRANSPARENT) {
             // Add primary color as a new brush and select it
             val newBrush = BrushInfo(color, 8f)
             // Insert at index 1 (after White) or just add
             val currentBrushes = brushes.value.toMutableList()
             currentBrushes.add(1, newBrush)
             brushes.value = currentBrushes
             setActiveBrush(1)
        }
    }

    /**
     * Adds a completed path to the drawing history.
     */
    fun addPath(path: Path) {
        val drawingPath = DrawingPath(
            path = path,
            color = _brushColor.value,
            strokeWidth = _strokeWidth.value,
            isEraser = isEraserActive.value
        )
        _paths.value = _paths.value + drawingPath
        undoStack.add(drawingPath)
        
        // Clear redo stack
        redoStack.clear()
        canRedo.value = false
    }

    /**
     * Undoes the last stroke.
     */
    fun undo() {
        val currentPaths = _paths.value
        if (currentPaths.isNotEmpty()) {
            val lastPath = currentPaths.last()
            
            // Allow redoing this path
            redoStack.add(lastPath)
            canRedo.value = true
            
            // Remove from paths and undo stack
            undoStack.removeLastOrNull()
            _paths.value = currentPaths.dropLast(1)
        }
    }

    /**
     * Redoes the last undone stroke.
     */
    fun redo() {
        if (redoStack.isNotEmpty()) {
            val path = redoStack.removeLast()
            undoStack.add(path)
            _paths.value = _paths.value + path
            canRedo.value = redoStack.isNotEmpty()
        }
    }

    // Brush Management
    
    fun setBrushColor(color: Int) {
        _brushColor.value = color
        // Update active brush in list if needed, relying on UI to call setActiveBrush
    }

    fun setActiveBrush(index: Int) {
        val brush = brushes.value.getOrNull(index) ?: return
        
        isEraserActive.value = false
        activeBrushIndex.value = index
        _brushColor.value = brush.color
        _strokeWidth.value = brush.width
    }

    fun toggleEraser() {
        if (isEraserActive.value) {
           // Switch back to brush
           isEraserActive.value = false
        } else {
            isEraserActive.value = true
            // Eraser width
             _strokeWidth.value = WhiteboardRepository.DEFAULT_ERASER_WIDTH
        }
    }
    
    fun toggleStylusOnlyMode() {
        isStylusOnlyMode.value = !isStylusOnlyMode.value
    }

    fun setToolbarAlignment(alignment: ToolbarAlignment) {
        toolbarAlignment.value = alignment
    }
    
    fun addBrush(color: Int = Color.BLACK) {
        // Add current or default brush
        val newBrush = BrushInfo(color, 8f)
        brushes.value = brushes.value + newBrush
        setActiveBrush(brushes.value.lastIndex)
    }

    /**
     * Sets the stroke width.
     */
    fun setStrokeWidth(width: Float) {
        _strokeWidth.value = width
        if (!isEraserActive.value) {
            // Update active brush width
             val index = activeBrushIndex.value
             val list = brushes.value.toMutableList()
             if (index in list.indices) {
                 list[index] = list[index].copy(width = width)
                 brushes.value = list
             }
        }
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
                    paint.strokeWidth = drawingPath.strokeWidth
                    if (drawingPath.isEraser) {
                         paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
                    } else {
                         paint.xfermode = null
                         paint.color = drawingPath.color
                    }
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
