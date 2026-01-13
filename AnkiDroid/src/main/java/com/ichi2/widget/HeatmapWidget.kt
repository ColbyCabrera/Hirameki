/*
* Copyright (c) 2025 Colby Cabrera <colbycabrera.wd@gmail.com>
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
* this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.ichi2.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProviders
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.IntentHandler
import com.ichi2.anki.NoteEditorActivity
import com.ichi2.anki.NoteEditorFragment
import com.ichi2.anki.R
import com.ichi2.anki.noteeditor.NoteEditorCaller
import timber.log.Timber
import java.util.Calendar

class HeatmapWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        // Fetch data before providing content to ensure it's fresh on every update
        val heatmapData = fetchHeatmapData()
        provideContent {
            GlanceTheme {
                HeatmapContent(heatmapData, context)
            }
        }
    }

    override suspend fun providePreview(
        context: Context,
        widgetCategory: Int,
    ) {
        val heatmapData = getDummyHeatmapData()
        provideContent {
            GlanceTheme {
                HeatmapContent(heatmapData, context)
            }
        }
    }

    @Composable
    internal fun HeatmapContent(
        data: Map<Long, Int>,
        context: Context,
    ) {
        val size = LocalSize.current

        // Approximate layout calculations
        // Left Labels: ~25dp
        // Right Panel: ~120dp
        // Padding: 16dp (8 start + 8 end)
        // Gap between sections: 16dp
        // Total reserved width: 25 + 120 + 16 + 16 = ~177dp
        val availableWidth = size.width - RESERVED_WIDTH_FOR_LABELS_AND_PANEL

        // Cell width 10.dp + 2.dp gap = 12.dp
        val numWeeks = (availableWidth.value / WEEK_COLUMN_WIDTH).toInt().coerceAtLeast(8)

        // Widgets run outside the main app context and don't have collection access,
        // so direct time APIs are appropriate here rather than collection.getTime()
        @Suppress("DirectSystemCurrentTimeMillisUsage") val today = System.currentTimeMillis()
        val dayMillis = DAY_IN_MILLIS
        val currentDayIndex = today / dayMillis

        // Calculate ISO Day of Week (0 = Mon, 6 = Sun)
        @Suppress("DirectCalendarInstanceUsage") val calendar = Calendar.getInstance()
        calendar.timeInMillis = today
        // Calendar.DAY_OF_WEEK: Sun=1, Mon=2, ... Sat=7
        // Convert to Mon=0, ... Sun=6
        val dow = calendar.get(Calendar.DAY_OF_WEEK)
        val todayDoW = (dow + 5) % 7

        val todayCount = data[currentDayIndex] ?: 0

        Row(
            modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.background)
                .padding(16.dp).clickable(actionStartActivity<IntentHandler>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // --- Left Section: Heatmap ---
            Column(
                modifier = GlanceModifier.fillMaxHeight(),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = context.getString(R.string.history),
                    style = TextStyle(
                        color = GlanceTheme.colors.onBackground,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )

                Spacer(GlanceModifier.height(8.dp))

                Row(
                    modifier = GlanceModifier.defaultWeight(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Day Labels
                    // Note: Using wrapper Boxes instead of Spacers to reduce child count.
                    // Glance/RemoteViews has a per-container child limit (~10), so we embed
                    // spacing in the Box height (12.dp = 10.dp content + 2.dp gap).
                    Column(
                        modifier = GlanceModifier.padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Get localized short weekday names in Mon-Sun order
                        // DateFormatSymbols.shortWeekdays is 1-indexed: [0]="", [1]=Sun, [2]=Mon, ..., [7]=Sat
                        val dateSymbols = java.text.DateFormatSymbols.getInstance()
                        val shortWeekdays = dateSymbols.shortWeekdays
                        val days = listOf(
                            shortWeekdays[Calendar.MONDAY],
                            shortWeekdays[Calendar.TUESDAY],
                            shortWeekdays[Calendar.WEDNESDAY],
                            shortWeekdays[Calendar.THURSDAY],
                            shortWeekdays[Calendar.FRIDAY],
                            shortWeekdays[Calendar.SATURDAY],
                            shortWeekdays[Calendar.SUNDAY],
                        )
                        days.forEachIndexed { _, day ->
                            Box(
                                modifier = GlanceModifier.height(16.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(
                                    text = day,
                                    style = TextStyle(
                                        color = GlanceTheme.colors.onSurfaceVariant,
                                        fontSize = 10.sp,
                                    ),
                                )
                            }
                        }
                    }

                    // Grid
                    // Note: Same wrapper Box approach as day labels to reduce child count
                    Row {
                        for (w in (numWeeks - 1) downTo 0) {
                            Column(
                                modifier = GlanceModifier.padding(end = 2.dp),
                            ) {
                                for (d in 0..6) {
                                    val dayOffset = (w * 7) + (todayDoW - d)
                                    val checkDayIndex = currentDayIndex - dayOffset
                                    val count = data[checkDayIndex] ?: 0
                                    val (colorProvider, alpha) = getColorForCount(
                                        count,
                                        GlanceTheme.colors,
                                    )

                                    // Wrapper Box with built-in spacing (12.dp = 10.dp cell + 2.dp gap)
                                    Box(
                                        modifier = GlanceModifier.height(if (d < 6) 16.dp else 14.dp),
                                        contentAlignment = Alignment.TopCenter,
                                    ) {
                                        Box(
                                            modifier = GlanceModifier.size(14.dp).background(
                                                colorProvider.getColor(context).copy(alpha = alpha),
                                            ).cornerRadius(2.dp),
                                        ) {}
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(GlanceModifier.defaultWeight())

            // --- Right Section: Info & Action ---
            Column(
                horizontalAlignment = Alignment.End,
                modifier = GlanceModifier.fillMaxHeight(),
            ) {
                Column {
                    Text(
                        text = context.resources.getQuantityString(
                            R.plurals.heatmap_widget_reviewed_count,
                            todayCount,
                            todayCount,
                        ),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        ),
                    )
                }

                Spacer(GlanceModifier.defaultWeight())

                // Add Card Button
                Box(
                    modifier = GlanceModifier.size(56.dp).background(GlanceTheme.colors.tertiary)
                        .cornerRadius(200.dp).clickable(
                            actionStartActivity(
                                NoteEditorActivity::class.java,
                                actionParametersOf(
                                    ActionParameters.Key<Int>(NoteEditorFragment.EXTRA_CALLER) to NoteEditorCaller.DECKPICKER.value,
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.add_24px),
                        contentDescription = context.getString(R.string.widget_add_note_button),
                        modifier = GlanceModifier.size(24.dp),
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.onTertiary),
                    )
                }
            }
        }
    }

    companion object {
        private val RESERVED_WIDTH_FOR_LABELS_AND_PANEL = 180.dp
        private const val WEEK_COLUMN_WIDTH = 16
        const val DAY_IN_MILLIS = 86400000L

        private const val HEATMAP_LEVEL_1_MAX_COUNT = 5
        private const val HEATMAP_LEVEL_2_MAX_COUNT = 20
        private const val HEATMAP_LEVEL_3_MAX_COUNT = 40

        /**
         * Maximum days of history to query for the heatmap.
         * This limits the revlog scan to a reasonable window for performance.
         * The widget typically displays ~20 weeks max, so 365 days provides
         * ample buffer while avoiding full table scans on large collections.
         */
        private const val MAX_HEATMAP_DAYS = 365

        /**
         * Returns the base color and alpha for the heatmap cell.
         */
        fun getColorForCount(
            count: Int,
            colors: ColorProviders,
        ): Pair<ColorProvider, Float> = when {
            count == 0 -> colors.surfaceVariant to 0.5f
            count <= HEATMAP_LEVEL_1_MAX_COUNT -> colors.primary to 0.25f
            count <= HEATMAP_LEVEL_2_MAX_COUNT -> colors.primary to 0.5f
            count <= HEATMAP_LEVEL_3_MAX_COUNT -> colors.primary to 0.8f
            else -> colors.primary to 1f
        }

        suspend fun fetchHeatmapData(): Map<Long, Int> = try {
            CollectionManager.withCol {
                val data = mutableMapOf<Long, Int>()
                // Limit query to recent history for performance.
                // revlog.id is the primary key (timestamp in ms), so the WHERE clause
                // enables an efficient index range scan instead of a full table scan.
                // Widgets run outside the main app context and may not have collection access,
                // so direct time APIs are appropriate here
                @Suppress("DirectSystemCurrentTimeMillisUsage") val cutoffMillis =
                    System.currentTimeMillis() - (MAX_HEATMAP_DAYS * DAY_IN_MILLIS)
                val query =
                    "SELECT CAST(id/$DAY_IN_MILLIS AS INTEGER) as day, count() FROM revlog WHERE id >= $cutoffMillis GROUP BY day"

                val cursor = this.db.query(query)
                cursor.use { c ->
                    while (c.moveToNext()) {
                        val day = c.getLong(0)
                        val count = c.getInt(1)
                        data[day] = count
                    }
                }
                data
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch heatmap data")
            emptyMap()
        }

        fun getDummyHeatmapData(): Map<Long, Int> {
            @Suppress("DirectSystemCurrentTimeMillisUsage") val today =
                System.currentTimeMillis() / DAY_IN_MILLIS
            val dummyData = mutableMapOf<Long, Int>()
            // Fill some days
            for (i in 0..100) {
                // Use when to explicitly define precedence (first matching condition wins)
                when {
                    i % 11 == 0 -> dummyData[today - i] = 21
                    i % 5 == 0 -> dummyData[today - i] = 11
                    i % 13 == 0 -> dummyData[today - i] = 6
                    i % 2 == 0 -> dummyData[today - i] = 1
                    i % 3 == 0 -> dummyData[today - i] = 0
                }
            }
            dummyData[today] = 294
            return dummyData
        }

        suspend fun updateHeatmapWidgetPreview(context: Context) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    val manager = GlanceAppWidgetManager(context)
                    @Suppress("UNUSED_VARIABLE")
                    val ignored = manager.setWidgetPreviews(
                        HeatmapWidgetReceiver::class,
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to update heatmap widget preview")
            }
        }
    }
}

@Suppress("unused")
@Preview(widthDp = 300, heightDp = 180)
@OptIn(ExperimentalGlancePreviewApi::class)
@Composable
fun HeatmapWidgetPreview() {
    val context = androidx.glance.LocalContext.current
    val dummyData = HeatmapWidget.getDummyHeatmapData()

    androidx.compose.runtime.CompositionLocalProvider(
        LocalSize provides androidx.compose.ui.unit.DpSize(300.dp, 400.dp),
    ) {
        HeatmapWidget().HeatmapContent(dummyData, context)
    }
}
