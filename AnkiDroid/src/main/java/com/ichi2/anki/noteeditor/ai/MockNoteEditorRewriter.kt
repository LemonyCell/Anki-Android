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

/**
 * A no-network stand-in for [NoteEditorRewriter] used by AIED-01 to build and test the AI panel UI before
 * the real provider (AIED-12) is wired in. It echoes the input wrapped in guillemets so the effect is
 * visible in the field (and undoable), confirming the apply pipeline works end to end.
 */
class MockNoteEditorRewriter : NoteEditorRewriter {
    override suspend fun rewrite(
        input: String,
        instruction: String,
    ): String = "«$input»"
}
