// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Eric Li <ericli3690@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>

package com.ichi2.anki.reviewreminders

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.R
import com.ichi2.anki.ui.compose.components.AnkiToggleView

class ScheduleRemindersAdapter(
    private val setDeckNameFromScopeForView: (ReviewReminderScope, TextView) -> Unit,
    private val toggleReminderEnabled: (ReviewReminderId, ReviewReminderScope) -> Unit,
    private val editReminder: (ReviewReminder) -> Unit,
) : ListAdapter<ReviewReminder, ScheduleRemindersAdapter.ViewHolder>(diffCallback) {
    inner class ViewHolder(
        holder: View,
    ) : RecyclerView.ViewHolder(holder) {
        var reminder: ReviewReminder? = null
        val context: Context = holder.context
        val deckTextView: TextView = holder.findViewById(R.id.reminders_list_deck_text)
        val timeTextView: TextView = holder.findViewById(R.id.reminders_list_time_text)
        val switchView: AnkiToggleView = holder.findViewById(R.id.reminders_list_switch)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val view =
            LayoutInflater
                .from(parent.context)
                .inflate(R.layout.schedule_reminders_list_item, parent, false)
        return ViewHolder(view)
    }

    @SuppressLint("DefaultLocale")
    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        val reminder = getItem(position)
        holder.reminder = reminder

        setDeckNameFromScopeForView(reminder.scope, holder.deckTextView)
        holder.timeTextView.text = reminder.time.toFormattedString(holder.context)

        holder.itemView.setOnClickListener { editReminder(reminder) }

        holder.switchView.isChecked = reminder.enabled
        holder.switchView.setOnCheckedChangeListener { _, _ -> toggleReminderEnabled(reminder.id, reminder.scope) }
    }

    companion object {
        private val diffCallback =
            object : DiffUtil.ItemCallback<ReviewReminder>() {
                override fun areItemsTheSame(
                    oldItem: ReviewReminder,
                    newItem: ReviewReminder,
                ): Boolean = oldItem.id == newItem.id

                override fun areContentsTheSame(
                    oldItem: ReviewReminder,
                    newItem: ReviewReminder,
                ): Boolean = oldItem == newItem
            }
    }
}
