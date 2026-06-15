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

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import kotlinx.coroutines.test.runTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationHistoryNoteEditorRewriterTest : RobolectricTest() {
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var store: PromptPresetStore

    @Before
    override fun setUp() {
        super.setUp()
        val context = ApplicationProvider.getApplicationContext<Context>()
        sharedPreferences =
            context.getSharedPreferences(
                "ConversationHistoryNoteEditorRewriterTest-${System.nanoTime()}",
                Context.MODE_PRIVATE,
            )
        store = PromptPresetStore(sharedPreferences)
    }

    @After
    override fun tearDown() {
        sharedPreferences.edit { clear() }
        super.tearDown()
    }

    @Test
    fun `decorator records trace payloads for traceable rewriter`() =
        runTest {
            val delegate =
                object : TraceableNoteEditorRewriter {
                    override suspend fun rewriteWithTrace(
                        input: String,
                        instruction: String,
                    ): NoteEditorRewriteTrace =
                        NoteEditorRewriteTrace(
                            output = "rewritten",
                            rawRequestBody = """{"request":"body"}""",
                            rawResponseBody = """{"response":"body"}""",
                        )
                }
            val decorated =
                ConversationHistoryNoteEditorRewriter(
                    delegate = delegate,
                    promptPresetStore = store,
                    now = { 100L },
                )

            val output = decorated.rewrite(input = "old", instruction = "improve")

            assertThat(output, equalTo("rewritten"))
            assertThat(
                store.getConversationHistory(),
                equalTo(
                    listOf(
                        AiConversationEntry(
                            createdAt = 100L,
                            instruction = "improve",
                            input = "old",
                            output = "rewritten",
                            rawRequestBody = """{"request":"body"}""",
                            rawResponseBody = """{"response":"body"}""",
                        ),
                    ),
                ),
            )
        }

    @Test
    fun `decorator records failures and rethrows`() =
        runTest {
            val delegate =
                NoteEditorRewriter { _, _ ->
                    throw OpenRouterApiException(
                        statusCode = 400,
                        message = "bad request",
                        rawRequestBody = """{"request":"body"}""",
                        rawResponseBody = """{"error":"bad"}""",
                    )
                }
            val decorated =
                ConversationHistoryNoteEditorRewriter(
                    delegate = delegate,
                    promptPresetStore = store,
                    now = { 200L },
                )

            val thrown =
                runCatching { decorated.rewrite(input = "old", instruction = "improve") }
                    .exceptionOrNull() as OpenRouterApiException

            assertThat(thrown.statusCode, equalTo(400))
            assertThat(
                store.getConversationHistory(),
                equalTo(
                    listOf(
                        AiConversationEntry(
                            createdAt = 200L,
                            instruction = "improve",
                            input = "old",
                            output = null,
                            rawRequestBody = """{"request":"body"}""",
                            rawResponseBody = """{"error":"bad"}""",
                            errorMessage = "bad request",
                        ),
                    ),
                ),
            )
        }
}
