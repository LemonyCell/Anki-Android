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

package com.ichi2.anki.noteeditor.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteEditorRewriteApplierTest {
    @Test
    fun replacesSelectedRangeAndPlacesCaretAfterReplacement() {
        val result = NoteEditorRewriteApplier.compose("hello world", 6, 11, "THERE")
        assertEquals("hello THERE", result.text)
        assertEquals(11, result.caret) // 6 + "THERE".length
    }

    @Test
    fun replacesWholeFieldWhenRangeIsEntireText() {
        val result = NoteEditorRewriteApplier.compose("hello", 0, 5, "«hello»")
        assertEquals("«hello»", result.text)
        assertEquals(7, result.caret)
    }

    @Test
    fun normalisesReversedSelection() {
        val forward = NoteEditorRewriteApplier.compose("abcdef", 1, 4, "X")
        val reversed = NoteEditorRewriteApplier.compose("abcdef", 4, 1, "X")
        assertEquals("aXef", forward.text)
        assertEquals(forward.text, reversed.text)
        assertEquals(forward.caret, reversed.caret)
    }

    @Test
    fun clampsOutOfBoundsIndices() {
        val result = NoteEditorRewriteApplier.compose("abc", -5, 99, "Z")
        assertEquals("Z", result.text)
        assertEquals(1, result.caret)
    }

    @Test
    fun insertsAtCaretWhenRangeIsEmpty() {
        val result = NoteEditorRewriteApplier.compose("abcd", 2, 2, "XY")
        assertEquals("abXYcd", result.text)
        assertEquals(4, result.caret)
    }
}
