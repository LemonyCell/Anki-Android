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

/** Result of applying a rewrite to a field: the new full text and where to place the caret. */
data class RewriteResult(
    val text: String,
    val caret: Int,
)

/**
 * Pure string math for applying an AI rewrite to a field's content, kept out of the fragment so it can be
 * unit-tested. Replaces the range [[start], [end]) of [fullText] with [replacement] and returns the new
 * full text plus the caret position (just after the inserted replacement).
 *
 * To rewrite the whole field, pass `start = 0` and `end = fullText.length`.
 */
object NoteEditorRewriteApplier {
    fun compose(
        fullText: String,
        start: Int,
        end: Int,
        replacement: String,
    ): RewriteResult {
        // Normalise: clamp to bounds and order the endpoints (selections can be reversed).
        val lo = start.coerceIn(0, fullText.length)
        val hi = end.coerceIn(0, fullText.length)
        val from = minOf(lo, hi)
        val to = maxOf(lo, hi)
        val newText = fullText.substring(0, from) + replacement + fullText.substring(to)
        return RewriteResult(newText, from + replacement.length)
    }
}
