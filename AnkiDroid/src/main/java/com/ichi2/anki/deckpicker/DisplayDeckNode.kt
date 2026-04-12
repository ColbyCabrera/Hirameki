/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
 *  Copyright (c) 2025 Gautam Bhetanabhotla <gautamarcturus@gmail.com>
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

package com.ichi2.anki.deckpicker

import android.annotation.SuppressLint
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Stable
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.sched.DeckNode
import com.ichi2.anki.libanki.utils.append
import java.util.Locale

/**
 * A stable, display-oriented view of a [DeckNode]. Created when building
 * the deck list for display. Contains properties needed for rendering
 * (counts, selection state, etc.) plus a reference to the source [DeckNode]
 * for actions that need the full tree context.
 */
@ConsistentCopyVisibility
@Stable
data class DisplayDeckNode private constructor(
    val did: DeckId,
    val fullDeckName: String,
    val lastDeckNameComponent: String,
    val collapsed: Boolean,
    val canCollapse: Boolean,
    val depth: Int,
    val filtered: Boolean,
    val newCount: Int,
    val lrnCount: Int,
    val revCount: Int,
    val isSelected: Boolean,
    val hasBuried: Boolean,
) {
    /** Reference to the original [DeckNode]. Excluded from equals/hashCode by being outside the constructor. */
    lateinit var deckNode: DeckNode
        private set

    fun withUpdatedDeckId(deckId: DeckId): DisplayDeckNode =
        this.copy(isSelected = this.did == deckId).also { it.deckNode = this.deckNode }

    companion object {
        fun from(
            node: DeckNode,
            matchesSearchOrChild: Boolean,
            selectedDeckId: DeckId,
            hasBuried: Boolean,
        ): DisplayDeckNode = DisplayDeckNode(
            did = node.did,
            fullDeckName = node.fullDeckName,
            lastDeckNameComponent = node.lastDeckNameComponent,
            collapsed = node.collapsed,
            canCollapse = node.children.any() && matchesSearchOrChild,
            depth = node.depth,
            filtered = node.filtered,
            newCount = node.newCount,
            lrnCount = node.lrnCount,
            revCount = node.revCount,
            isSelected = node.did == selectedDeckId,
            hasBuried = hasBuried,
        ).apply { deckNode = node }
    }
}

private fun DeckNode.hasBuriedRecursively(decksWithBuried: Set<DeckId>): Boolean {
    if (did in decksWithBuried) return true
    return children.any { it.hasBuriedRecursively(decksWithBuried) }
}

/** Convert the tree into a flat list of [DisplayDeckNode]s, where matching decks and the children/parents
 * are included. Decks inside collapsed decks are not considered. */
fun DeckNode.filterAndFlattenDisplay(
    filter: CharSequence?,
    selectedDeckId: DeckId,
    decksWithBuried: Set<DeckId>,
): List<DisplayDeckNode> {
    val filterPattern = if (filter.isNullOrBlank()) {
        null
    } else {
        filter.toString().lowercase(Locale.getDefault()).trim()
    }
    val list = mutableListOf<DisplayDeckNode>()
    filterAndFlattenDisplayInner(
        filterPattern,
        list,
        parentMatched = false,
        selectedDeckId,
        decksWithBuried
    )
    return list
}

private fun DeckNode.filterAndFlattenDisplayInner(
    filter: CharSequence?,
    list: MutableList<DisplayDeckNode>,
    parentMatched: Boolean,
    selectedDeckId: DeckId,
    decksWithBuried: Set<DeckId>,
) {
    if (!isSyntheticDeck && (nameMatchesFilter((filter)) || parentMatched)) {
        this.addVisibleToList(list, matchesSearchOrChild = true, selectedDeckId, decksWithBuried)
        return
    }

    // When searching, ignore collapsed state and always search children
    val searching = filter != null
    if (collapsed && !searching) {
        return
    }

    if (!isSyntheticDeck) {
        list.append(
            DisplayDeckNode.from(
                this,
                matchesSearchOrChild = false,
                selectedDeckId = selectedDeckId,
                hasBuried = hasBuriedRecursively(decksWithBuried),
            ),
        )
    }
    val startingLen = list.size
    for (child in children) {
        child.filterAndFlattenDisplayInner(
            filter,
            list,
            parentMatched = false,
            selectedDeckId,
            decksWithBuried
        )
    }
    if (!isSyntheticDeck && startingLen == list.size) {
        // we don't include ourselves if no children matched
        list.removeAt(list.lastIndex)
    }
}

private fun DeckNode.addVisibleToList(
    list: MutableList<DisplayDeckNode>,
    matchesSearchOrChild: Boolean,
    selectedDeckId: DeckId,
    decksWithBuried: Set<DeckId>,
) {
    list.append(
        DisplayDeckNode.from(
            this,
            matchesSearchOrChild,
            selectedDeckId,
            hasBuriedRecursively(decksWithBuried)
        )
    )
    if (!collapsed) {
        for (child in children) {
            child.addVisibleToList(list, matchesSearchOrChild, selectedDeckId, decksWithBuried)
        }
    }
}

@VisibleForTesting
fun DeckNode.addVisibleToList(list: MutableList<DeckNode>) {
    list.append(this)
    if (!collapsed) {
        for (child in children) {
            child.addVisibleToList(list)
        }
    }
}

@SuppressLint("LocaleRootUsage")
private fun DeckNode.nameMatchesFilter(filter: CharSequence?): Boolean {
    return if (filter == null) {
        true
    } else {
        node.name.lowercase(Locale.getDefault())
            .contains(filter) || node.name.lowercase(Locale.ROOT).contains(filter)
    }
}
