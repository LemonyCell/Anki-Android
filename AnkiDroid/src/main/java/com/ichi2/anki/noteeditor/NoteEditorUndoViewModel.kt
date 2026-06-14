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

import androidx.lifecycle.ViewModel

/**
 * Holds per-field undo/redo history for the note editor, keyed by field ordinal ([com.ichi2.anki.FieldEditText.ord]).
 *
 * Living in a [ViewModel] means the history survives configuration changes (e.g. rotation) while the
 * editor fragment is recreated. This is the foundation for AIED-05: today snapshots are captured as the
 * user types (debounced); when the AI panel lands, an AI rewrite simply becomes one more [record] call.
 */
class NoteEditorUndoViewModel : ViewModel() {
    private val histories = mutableMapOf<Int, FieldHistory>()

    /** Initialises history for [ord] with [initial] content. No-op if history already exists. */
    fun seed(
        ord: Int,
        initial: String,
    ) {
        if (!histories.containsKey(ord)) {
            histories[ord] = FieldHistory(initial)
        }
    }

    /** Records [text] (caret at [selection]) as a new snapshot for [ord], seeding the history if needed. */
    fun record(
        ord: Int,
        text: String,
        selection: Int,
    ) {
        histories.getOrPut(ord) { FieldHistory(text, selection) }.push(text, selection)
    }

    /** Returns the previous snapshot for field [ord], or `null` if there is nothing to undo. */
    fun undo(ord: Int): FieldSnapshot? = histories[ord]?.undo()

    /** Returns the next snapshot for field [ord], or `null` if there is nothing to redo. */
    fun redo(ord: Int): FieldSnapshot? = histories[ord]?.redo()

    fun canUndo(ord: Int): Boolean = histories[ord]?.canUndo == true

    fun canRedo(ord: Int): Boolean = histories[ord]?.canRedo == true
}
