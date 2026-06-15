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
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import com.ichi2.anki.common.time.TimeManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.UUID

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
        val usageCounts = state.presetUsageCountByNormalizedPrompt
        val lastUsedAt = state.presetLastUsedAtByNormalizedPrompt
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
                    usageCount = usageCounts[normalizedPrompt] ?: 0,
                    lastUsedAt = lastUsedAt[normalizedPrompt],
                )
        }

        return latestByNormalizedPrompt.values.toList()
    }

    /**
     * Increments usage metrics for one preset prompt (frequency + last-used timestamp).
     *
     * @return `false` if [promptText] is blank or no matching preset exists in prompt history.
     */
    fun recordPresetUsage(
        promptText: String,
        usedAt: Long = TimeManager.time.intTimeMS(),
    ): Boolean {
        val normalizedPrompt = normalizePrompt(promptText)
        if (normalizedPrompt.isEmpty()) {
            Timber.d("Skipping usage record for blank AI preset")
            return false
        }

        val previousState = readState()
        val existsInHistory = previousState.submissions.any { normalizePrompt(it.promptText) == normalizedPrompt }
        if (!existsInHistory) {
            Timber.d("Skipping usage record for unknown AI preset")
            return false
        }

        val updatedCounts = previousState.presetUsageCountByNormalizedPrompt.toMutableMap()
        val updatedLastUsed = previousState.presetLastUsedAtByNormalizedPrompt.toMutableMap()
        updatedCounts[normalizedPrompt] = (updatedCounts[normalizedPrompt] ?: 0) + 1
        updatedLastUsed[normalizedPrompt] = usedAt

        writeState(
            previousState.copy(
                presetUsageCountByNormalizedPrompt = updatedCounts,
                presetLastUsedAtByNormalizedPrompt = updatedLastUsed,
            ),
        )
        return true
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

    /**
     * Returns persisted AI conversation history in chronological order.
     */
    fun getConversationHistory(): List<AiConversationEntry> = readState().conversationHistory

    /**
     * Appends one AI interaction entry and prunes older entries to [MAX_CONVERSATION_HISTORY_ENTRIES].
     */
    fun saveConversationEntry(entry: AiConversationEntry) {
        val previousState = readState()
        val updatedHistory = (previousState.conversationHistory + entry).takeLast(MAX_CONVERSATION_HISTORY_ENTRIES)
        writeState(
            previousState.copy(
                conversationHistory = updatedHistory,
            ),
        )
    }

    // --- System prompt versions (AIED-14) ---

    /** All saved system-prompt versions in creation order. The built-in Default is not included. */
    fun getSystemPromptVersions(): List<SystemPromptVersion> = readState().systemPromptVersions

    /** The active version id, or `null` when the built-in Default is active. */
    fun getActiveSystemPromptVersionId(): String? = readState().activeSystemPromptVersionId

    /** The active version's non-blank text, or [default] when Default is active / the text is blank. */
    fun activeSystemPromptText(default: String): String {
        val state = readState()
        val active = state.systemPromptVersions.firstOrNull { it.id == state.activeSystemPromptVersionId }
        return active?.text?.trim()?.takeIf { it.isNotEmpty() } ?: default
    }

    /** Creates a new version, makes it active, and returns its id. */
    fun addSystemPromptVersion(
        name: String,
        text: String,
        createdAt: Long = TimeManager.time.intTimeMS(),
    ): String {
        val id = UUID.randomUUID().toString()
        val previousState = readState()
        writeState(
            previousState.copy(
                systemPromptVersions = previousState.systemPromptVersions + SystemPromptVersion(id, name, text, createdAt),
                activeSystemPromptVersionId = id,
            ),
        )
        return id
    }

    /** Updates an existing version's name/text. Returns `false` if [id] is unknown. */
    fun updateSystemPromptVersion(
        id: String,
        name: String,
        text: String,
    ): Boolean {
        val previousState = readState()
        if (previousState.systemPromptVersions.none { it.id == id }) return false
        writeState(
            previousState.copy(
                systemPromptVersions =
                    previousState.systemPromptVersions.map {
                        if (it.id == id) it.copy(name = name, text = text) else it
                    },
            ),
        )
        return true
    }

    /** Creates a new active version copying [fromId]'s text. Returns the new id, or `null` if [fromId] is unknown. */
    fun duplicateSystemPromptVersion(
        fromId: String,
        name: String,
        createdAt: Long = TimeManager.time.intTimeMS(),
    ): String? {
        val source = readState().systemPromptVersions.firstOrNull { it.id == fromId } ?: return null
        return addSystemPromptVersion(name, source.text, createdAt)
    }

    /** Deletes a version; if it was active, falls back to Default. Returns `false` if [id] is unknown. */
    fun deleteSystemPromptVersion(id: String): Boolean {
        val previousState = readState()
        if (previousState.systemPromptVersions.none { it.id == id }) return false
        writeState(
            previousState.copy(
                systemPromptVersions = previousState.systemPromptVersions.filterNot { it.id == id },
                activeSystemPromptVersionId =
                    previousState.activeSystemPromptVersionId.takeIf { it != id },
            ),
        )
        return true
    }

    /** Sets the active version ([id] = `null` activates the built-in Default). Returns `false` if [id] is unknown. */
    fun setActiveSystemPromptVersion(id: String?): Boolean {
        val previousState = readState()
        if (id != null && previousState.systemPromptVersions.none { it.id == id }) return false
        writeState(previousState.copy(activeSystemPromptVersionId = id))
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
            if (state.submissions.isEmpty() &&
                state.softDeletedNormalizedPrompts.isEmpty() &&
                state.conversationHistory.isEmpty() &&
                state.presetUsageCountByNormalizedPrompt.isEmpty() &&
                state.presetLastUsedAtByNormalizedPrompt.isEmpty() &&
                state.systemPromptVersions.isEmpty() &&
                state.activeSystemPromptVersionId == null
            ) {
                remove(STORAGE_KEY)
            } else {
                putString(STORAGE_KEY, json.encodeToString(state))
            }
        }
    }

    companion object {
        private const val PREFERENCES_FILE_NAME = "ai-prompt-preset-store"
        private const val STORAGE_KEY = "ai_prompt_preset_store"
        const val MAX_CONVERSATION_HISTORY_ENTRIES = 200
        private val whitespaceRegex = Regex("\\s+")
        private val json = Json { ignoreUnknownKeys = true }

        fun fromContext(context: Context): PromptPresetStore =
            PromptPresetStore(
                context.getSharedPreferences(PREFERENCES_FILE_NAME, Context.MODE_PRIVATE),
            )

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
    val usageCount: Int = 0,
    val lastUsedAt: Long? = null,
)

@Serializable
data class AiConversationEntry(
    val createdAt: Long,
    val instruction: String,
    val input: String,
    val output: String? = null,
    val rawRequestBody: String? = null,
    val rawResponseBody: String? = null,
    val errorMessage: String? = null,
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

/**
 * A saved system-prompt version (AIED-14). The built-in "Default" prompt is not stored here — a `null`
 * [StoredPromptPresetState.activeSystemPromptVersionId] means the default is active.
 */
@Serializable
data class SystemPromptVersion(
    val id: String,
    val name: String,
    val text: String,
    val createdAt: Long,
)

@Serializable
private data class StoredPromptPresetState(
    val submissions: List<PromptSubmission> = emptyList(),
    val softDeletedNormalizedPrompts: Set<String> = emptySet(),
    val conversationHistory: List<AiConversationEntry> = emptyList(),
    val presetUsageCountByNormalizedPrompt: Map<String, Int> = emptyMap(),
    val presetLastUsedAtByNormalizedPrompt: Map<String, Long> = emptyMap(),
    val systemPromptVersions: List<SystemPromptVersion> = emptyList(),
    val activeSystemPromptVersionId: String? = null,
)
