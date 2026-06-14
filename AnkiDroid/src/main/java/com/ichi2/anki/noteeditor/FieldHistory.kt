/*
 * Copyright (c) 2026 Viktor Patsula <viktor.patsula@intapp.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.noteeditor

/**
 * A single point in a field's edit history: its [text] and the caret position ([selection]) at the time
 * the snapshot was taken, so undo/redo can restore the cursor to where the change happened.
 */
data class FieldSnapshot(
    val text: String,
    val selection: Int,
)

/**
 * Undo/redo history for a single note-editor field.
 *
 * Holds a linear stack of content snapshots with a cursor pointing at the current state. A new [push]
 * after one or more [undo] calls overwrites the "redo" branch, matching the standard editor behaviour:
 *
 * ```
 * A -> B -> C        (cursor on C)
 * undo  -> cursor on B
 * undo  -> cursor on A
 * push(D) -> A -> B -> D   (C is discarded)
 * ```
 *
 * The stack is seeded with the field's [initial] content so [undo] can always return to where editing
 * started. Depth is capped at [maxDepth]; once exceeded, the oldest states are dropped.
 *
 * This class is pure (no Android dependencies) so it can be unit-tested directly.
 */
class FieldHistory(
    initial: String,
    initialSelection: Int = initial.length,
    private val maxDepth: Int = DEFAULT_MAX_DEPTH,
) {
    private val states = mutableListOf(FieldSnapshot(initial, initialSelection))
    private var cursor = 0

    /** The snapshot the cursor currently points at. */
    val current: FieldSnapshot
        get() = states[cursor]

    val canUndo: Boolean
        get() = cursor > 0

    val canRedo: Boolean
        get() = cursor < states.lastIndex

    /**
     * Records [text] (with caret at [selection]) as a new snapshot. No-op if [text] equals the current
     * state's text (so unchanged content / caret-only moves don't create empty history entries). Discards
     * any redo branch and caps the stack at [maxDepth].
     */
    fun push(
        text: String,
        selection: Int = text.length,
    ) {
        if (text == states[cursor].text) return
        if (cursor < states.lastIndex) {
            states.subList(cursor + 1, states.size).clear()
        }
        states.add(FieldSnapshot(text, selection))
        cursor = states.lastIndex
        if (states.size > maxDepth) {
            val overflow = states.size - maxDepth
            repeat(overflow) { states.removeAt(0) }
            cursor -= overflow
        }
    }

    /** Steps back one state and returns it, or `null` if already at the oldest state. */
    fun undo(): FieldSnapshot? {
        if (!canUndo) return null
        cursor--
        return states[cursor]
    }

    /** Steps forward one state and returns it, or `null` if already at the newest state. */
    fun redo(): FieldSnapshot? {
        if (!canRedo) return null
        cursor++
        return states[cursor]
    }

    companion object {
        const val DEFAULT_MAX_DEPTH = 20
    }
}
