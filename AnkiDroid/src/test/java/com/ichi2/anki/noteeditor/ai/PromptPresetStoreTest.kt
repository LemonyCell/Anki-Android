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
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PromptPresetStoreTest : RobolectricTest() {
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var store: PromptPresetStore

    @Before
    override fun setUp() {
        super.setUp()
        val context = ApplicationProvider.getApplicationContext<Context>()
        sharedPreferences =
            context.getSharedPreferences(
                "PromptPresetStoreTest-${System.nanoTime()}",
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
    fun `submitted prompts are persisted with timestamp`() {
        store.saveSubmittedPrompt("Make this easier to understand", createdAt = 100L)

        val reloadedStore = PromptPresetStore(sharedPreferences)

        assertThat(
            reloadedStore.getPromptHistory(),
            equalTo(listOf(PromptSubmission("Make this easier to understand", 100L))),
        )
        assertThat(
            reloadedStore.getVisiblePresets(),
            equalTo(listOf(PromptPreset("Make this easier to understand", 100L))),
        )
    }

    @Test
    fun `visible presets are deduplicated by normalized prompt text`() {
        store.saveSubmittedPrompt("  Explain   this  ", createdAt = 100L)
        store.saveSubmittedPrompt("Explain this", createdAt = 200L)

        assertThat(
            store.getVisiblePresets(),
            equalTo(listOf(PromptPreset("Explain this", 200L))),
        )
        assertThat(
            store.getPromptHistory(),
            equalTo(
                listOf(
                    PromptSubmission("Explain   this", 100L),
                    PromptSubmission("Explain this", 200L),
                ),
            ),
        )
    }

    @Test
    fun `soft delete hides preset but keeps history and re-submit un-hides it`() {
        store.saveSubmittedPrompt("Shorten this text", createdAt = 100L)

        assertThat(store.softDeletePreset(" Shorten   this text "), equalTo(true))
        assertThat(store.getVisiblePresets(), empty())
        assertThat(
            store.getPromptHistory(),
            equalTo(listOf(PromptSubmission("Shorten this text", 100L))),
        )

        store.saveSubmittedPrompt("Shorten this text", createdAt = 200L)

        assertThat(
            store.getVisiblePresets(),
            equalTo(listOf(PromptPreset("Shorten this text", 200L))),
        )
        assertThat(
            store.getPromptHistory(),
            equalTo(
                listOf(
                    PromptSubmission("Shorten this text", 100L),
                    PromptSubmission("Shorten this text", 200L),
                ),
            ),
        )
    }

    @Test
    fun `request builder appends checked presets to typed prompt`() {
        val effectivePrompt =
            PromptPresetRequestBuilder.buildEffectivePrompt(
                typedPrompt = "Rewrite this politely",
                checkedPresetPrompts =
                    listOf(
                        "Use short sentences",
                        "Use  short   sentences",
                        "",
                        "Add bullet points",
                    ),
            )

        assertThat(
            effectivePrompt,
            equalTo("Rewrite this politely\n\nUse short sentences\n\nAdd bullet points"),
        )
    }

    @Test
    fun `no built-in presets are returned for empty history`() {
        assertThat(store.getVisiblePresets(), empty())
    }

    @Test
    fun `recording preset usage increments count and updates last-used timestamp`() {
        store.saveSubmittedPrompt("  Explain   this  ", createdAt = 100L)
        store.saveSubmittedPrompt("Explain this", createdAt = 200L)

        assertThat(store.recordPresetUsage("Explain this", usedAt = 1000L), equalTo(true))
        assertThat(store.recordPresetUsage(" Explain   this ", usedAt = 2000L), equalTo(true))

        assertThat(
            store.getVisiblePresets(),
            equalTo(
                listOf(
                    PromptPreset(
                        promptText = "Explain this",
                        createdAt = 200L,
                        usageCount = 2,
                        lastUsedAt = 2000L,
                    ),
                ),
            ),
        )

        val reloadedStore = PromptPresetStore(sharedPreferences)
        assertThat(
            reloadedStore.getVisiblePresets(),
            equalTo(
                listOf(
                    PromptPreset(
                        promptText = "Explain this",
                        createdAt = 200L,
                        usageCount = 2,
                        lastUsedAt = 2000L,
                    ),
                ),
            ),
        )
    }

    @Test
    fun `recording preset usage rejects blank or unknown prompts`() {
        store.saveSubmittedPrompt("Shorten this", createdAt = 100L)

        assertThat(store.recordPresetUsage("   "), equalTo(false))
        assertThat(store.recordPresetUsage("Unknown prompt"), equalTo(false))
        assertThat(
            store.getVisiblePresets(),
            equalTo(listOf(PromptPreset(promptText = "Shorten this", createdAt = 100L))),
        )
    }

    @Test
    fun `conversation entries are persisted`() {
        val entry =
            AiConversationEntry(
                createdAt = 42L,
                instruction = "Shorten this",
                input = "Original text",
                output = "Short text",
                rawRequestBody = """{"model":"anthropic/claude-sonnet-4"}""",
                rawResponseBody = """{"choices":[{"message":{"content":"Short text"}}]}""",
            )
        store.saveConversationEntry(entry)

        val reloadedStore = PromptPresetStore(sharedPreferences)

        assertThat(reloadedStore.getConversationHistory(), equalTo(listOf(entry)))
    }

    @Test
    fun `conversation history is bounded`() {
        repeat(PromptPresetStore.MAX_CONVERSATION_HISTORY_ENTRIES + 5) { index ->
            store.saveConversationEntry(
                AiConversationEntry(
                    createdAt = index.toLong(),
                    instruction = "Instruction $index",
                    input = "Input $index",
                    output = "Output $index",
                ),
            )
        }

        val history = store.getConversationHistory()

        assertThat(history.size, equalTo(PromptPresetStore.MAX_CONVERSATION_HISTORY_ENTRIES))
        assertThat(history.first().createdAt, equalTo(5L))
        assertThat(history.last().createdAt, equalTo((PromptPresetStore.MAX_CONVERSATION_HISTORY_ENTRIES + 4).toLong()))
    }
}
