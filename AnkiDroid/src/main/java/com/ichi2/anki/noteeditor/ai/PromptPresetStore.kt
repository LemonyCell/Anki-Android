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

import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import com.ichi2.anki.common.time.TimeManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Stores AI prompt submissions locally and exposes a deduplicated preset list.
 *
 * This is the functional storage layer only. It does not perform any UI/network integration.
 */
class PromptPresetStore(
    private val sharedPreferences: SharedPreferences,
) {
    /**
     * Returns all submitted prompts in the order they were submitted.
     */
    fun getPromptHistory(): List<PromptSubmission> = readState().submissions

    /**
     * Saves one submitted prompt and un-hides it if it was previously soft-deleted.
     *
     * @return `false` if [promptText] is blank, `true` otherwise.
     */
    fun saveSubmittedPrompt(
        promptText: String,
        createdAt: Long = TimeManager.time.intTimeMS(),
    ): Boolean {
        val normalizedPrompt = normalizePrompt(promptText)
        if (normalizedPrompt.isEmpty()) {
            Timber.d("Skipping blank AI prompt submission")
            return false
        }

        val trimmedPrompt = promptText.trim()
        val previousState = readState()
        writeState(
            previousState.copy(
                submissions = previousState.submissions + PromptSubmission(trimmedPrompt, createdAt),
                softDeletedNormalizedPrompts = previousState.softDeletedNormalizedPrompts - normalizedPrompt,
            ),
        )
        return true
    }

    /**
     * Returns a deduplicated, non-soft-deleted prompt list in most-recent-first order.
     */
    fun getVisiblePresets(): List<PromptPreset> {
        val state = readState()
        val hidden = state.softDeletedNormalizedPrompts
        val latestByNormalizedPrompt = LinkedHashMap<String, PromptPreset>()

        state.submissions.asReversed().forEach { submission ->
            val normalizedPrompt = normalizePrompt(submission.promptText)
            if (normalizedPrompt.isEmpty() ||
                normalizedPrompt in hidden ||
                latestByNormalizedPrompt.contains(normalizedPrompt)
            ) {
                return@forEach
            }
            latestByNormalizedPrompt[normalizedPrompt] =
                PromptPreset(
                    promptText = submission.promptText,
                    createdAt = submission.createdAt,
                )
        }

        return latestByNormalizedPrompt.values.toList()
    }

    /**
     * Soft-deletes the preset from the visible list without deleting submission history.
     */
    fun softDeletePreset(promptText: String): Boolean {
        val normalizedPrompt = normalizePrompt(promptText)
        if (normalizedPrompt.isEmpty()) {
            Timber.d("Skipping soft-delete for blank AI prompt")
            return false
        }

        val previousState = readState()
        val existsInHistory = previousState.submissions.any { normalizePrompt(it.promptText) == normalizedPrompt }
        if (!existsInHistory || normalizedPrompt in previousState.softDeletedNormalizedPrompts) {
            return false
        }

        writeState(
            previousState.copy(
                softDeletedNormalizedPrompts = previousState.softDeletedNormalizedPrompts + normalizedPrompt,
            ),
        )
        return true
    }

    @VisibleForTesting
    fun clear() {
        sharedPreferences.edit { remove(STORAGE_KEY) }
    }

    private fun readState(): StoredPromptPresetState {
        val rawState = sharedPreferences.getString(STORAGE_KEY, null) ?: return StoredPromptPresetState()
        return runCatching {
            json.decodeFromString<StoredPromptPresetState>(rawState)
        }.getOrElse { error ->
            Timber.e(error, "Failed to decode AI prompt preset store state")
            StoredPromptPresetState()
        }
    }

    private fun writeState(state: StoredPromptPresetState) {
        sharedPreferences.edit {
            if (state.submissions.isEmpty() && state.softDeletedNormalizedPrompts.isEmpty()) {
                remove(STORAGE_KEY)
            } else {
                putString(STORAGE_KEY, json.encodeToString(state))
            }
        }
    }

    companion object {
        private const val STORAGE_KEY = "ai_prompt_preset_store"
        private val whitespaceRegex = Regex("\\s+")
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Normalizes prompt text for deduplication:
         * trim + collapse whitespace.
         */
        fun normalizePrompt(promptText: String): String = promptText.trim().replace(whitespaceRegex, " ")
    }
}

@Serializable
data class PromptSubmission(
    val promptText: String,
    val createdAt: Long,
)

data class PromptPreset(
    val promptText: String,
    val createdAt: Long,
)

/**
 * Builds the prompt string sent to AI from the typed prompt and checked presets.
 */
object PromptPresetRequestBuilder {
    fun buildEffectivePrompt(
        typedPrompt: String,
        checkedPresetPrompts: Collection<String>,
    ): String {
        val sections = mutableListOf<String>()
        typedPrompt.trim().takeIf { it.isNotEmpty() }?.let(sections::add)

        val seenNormalizedPrompts = mutableSetOf<String>()
        checkedPresetPrompts.forEach { preset ->
            val trimmedPreset = preset.trim()
            if (trimmedPreset.isEmpty()) {
                return@forEach
            }
            val normalizedPreset = PromptPresetStore.normalizePrompt(trimmedPreset)
            if (seenNormalizedPrompts.add(normalizedPreset)) {
                sections.add(trimmedPreset)
            }
        }

        return sections.joinToString(separator = "\n\n")
    }
}

@Serializable
private data class StoredPromptPresetState(
    val submissions: List<PromptSubmission> = emptyList(),
    val softDeletedNormalizedPrompts: Set<String> = emptySet(),
)
