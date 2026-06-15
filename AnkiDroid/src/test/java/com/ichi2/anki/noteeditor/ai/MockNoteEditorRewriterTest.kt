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

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MockNoteEditorRewriterTest {
    @Test
    fun wrapsInputInGuillemetsRegardlessOfInstruction() =
        runBlocking {
            val rewriter = MockNoteEditorRewriter()
            assertEquals("«hello»", rewriter.rewrite("hello", "make it shorter"))
            assertEquals("«»", rewriter.rewrite("", "anything"))
        }
}
