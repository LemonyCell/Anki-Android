/*
 *  Copyright (c) 2026
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

package com.ichi2.anki.noteeditor.ai

import com.ichi2.anki.common.time.TimeManager
import kotlinx.coroutines.CancellationException

/**
 * Decorates [NoteEditorRewriter] and persists local AI conversation history for every rewrite call.
 */
class ConversationHistoryNoteEditorRewriter(
    private val delegate: NoteEditorRewriter,
    private val promptPresetStore: PromptPresetStore,
    private val now: () -> Long = { TimeManager.time.intTimeMS() },
) : NoteEditorRewriter {
    override suspend fun rewrite(
        input: String,
        instruction: String,
    ): String {
        val createdAt = now()
        return try {
            if (delegate is TraceableNoteEditorRewriter) {
                val trace = delegate.rewriteWithTrace(input = input, instruction = instruction)
                promptPresetStore.saveConversationEntry(
                    AiConversationEntry(
                        createdAt = createdAt,
                        instruction = instruction,
                        input = input,
                        output = trace.output,
                        rawRequestBody = trace.rawRequestBody,
                        rawResponseBody = trace.rawResponseBody,
                    ),
                )
                trace.output
            } else {
                val output = delegate.rewrite(input = input, instruction = instruction)
                promptPresetStore.saveConversationEntry(
                    AiConversationEntry(
                        createdAt = createdAt,
                        instruction = instruction,
                        input = input,
                        output = output,
                    ),
                )
                output
            }
        } catch (e: Exception) {
            if (e is CancellationException) {
                throw e
            }

            val rewriteException = e as? NoteEditorRewriteException
            promptPresetStore.saveConversationEntry(
                AiConversationEntry(
                    createdAt = createdAt,
                    instruction = instruction,
                    input = input,
                    output = null,
                    rawRequestBody = rewriteException?.rawRequestBody,
                    rawResponseBody = rewriteException?.rawResponseBody,
                    errorMessage = e.message,
                ),
            )
            throw e
        }
    }
}
