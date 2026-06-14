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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class FieldHistoryTest {
    @Test
    fun seededHistoryCannotUndoOrRedo() {
        val history = FieldHistory("A")
        assertEquals("A", history.current.text)
        assertFalse("fresh history cannot undo", history.canUndo)
        assertFalse("fresh history cannot redo", history.canRedo)
        assertNull(history.undo())
        assertNull(history.redo())
    }

    @Test
    fun undoAndRedoWalkTheStack() {
        val history = FieldHistory("A")
        history.push("B")
        history.push("C")

        assertEquals("B", history.undo()?.text)
        assertEquals("A", history.undo()?.text)
        assertFalse(history.canUndo)
        assertNull(history.undo())

        assertEquals("B", history.redo()?.text)
        assertEquals("C", history.redo()?.text)
        assertFalse(history.canRedo)
        assertNull(history.redo())
    }

    @Test
    fun pushAfterUndoOverwritesRedoBranch() {
        val history = FieldHistory("A")
        history.push("B")
        history.push("C")

        history.undo() // -> B (C is now the redo branch)
        history.push("D") // pushing from B discards C: stack becomes A -> B -> D

        assertEquals("D", history.current.text)
        assertFalse("redo branch (C) discarded", history.canRedo)
        assertEquals("B", history.undo()?.text)
        assertEquals("A", history.undo()?.text)
        assertFalse(history.canUndo)
    }

    @Test
    fun pushAfterUndoingToOldestDiscardsAllFutureStates() {
        val history = FieldHistory("A")
        history.push("B")
        history.push("C")

        history.undo() // -> B
        history.undo() // -> A (both B and C are the redo branch)
        history.push("D") // pushing from A discards B and C: stack becomes A -> D

        assertEquals("D", history.current.text)
        assertFalse(history.canRedo)
        assertEquals("A", history.undo()?.text)
        assertFalse(history.canUndo)
    }

    @Test
    fun pushingUnchangedTextIsNoOp() {
        val history = FieldHistory("A")
        history.push("A")
        assertFalse(history.canUndo)

        history.push("B")
        history.push("B")
        assertEquals("A", history.undo()?.text)
        assertFalse("only one real state was recorded", history.canUndo)
    }

    @Test
    fun snapshotPreservesCaretPosition() {
        val history = FieldHistory("hello", initialSelection = 5)
        history.push("hello world", selection = 3)

        val undone = history.undo()
        assertEquals("hello", undone?.text)
        assertEquals(5, undone?.selection)

        val redone = history.redo()
        assertEquals("hello world", redone?.text)
        assertEquals(3, redone?.selection)
    }

    @Test
    fun depthIsCappedDroppingOldestStates() {
        val history = FieldHistory("0", maxDepth = 3)
        // states would be 0,1,2,3,4 but capped to the newest 3: 2,3,4
        for (i in 1..4) {
            history.push(i.toString())
        }
        assertEquals("4", history.current.text)
        assertEquals("3", history.undo()?.text)
        assertEquals("2", history.undo()?.text)
        assertFalse("oldest states (0,1) were dropped by the cap", history.canUndo)
    }
}
