/*
 *  Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.preferences

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.MetaDB
import com.ichi2.anki.R
import com.ichi2.anki.ai.OpenRouterApiKeyStore
import com.ichi2.anki.ai.OpenRouterModelStore
import com.ichi2.anki.compat.CompatHelper
import com.ichi2.anki.exception.StorageAccessException
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.noteeditor.ai.ModelCatalogRow
import com.ichi2.anki.noteeditor.ai.NoteEditorRewriteException
import com.ichi2.anki.noteeditor.ai.OpenRouterModel
import com.ichi2.anki.noteeditor.ai.OpenRouterModelCatalog
import com.ichi2.anki.noteeditor.ai.OpenRouterNoteEditorRewriter
import com.ichi2.anki.noteeditor.ai.PromptPresetStore
import com.ichi2.anki.noteeditor.ai.SystemPromptVersion
import com.ichi2.anki.provider.CardContentProvider
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.openUrl
import com.ichi2.anki.withProgress
import com.ichi2.utils.show
import timber.log.Timber
import java.io.File

class AdvancedSettingsFragment : SettingsFragment() {
    override val preferenceResource: Int
        get() = R.xml.preferences_advanced
    override val analyticsScreenNameConstant: String
        get() = "prefs.advanced"

    override fun initSubscreen() {
        removeUnnecessaryAdvancedPrefs()

        // Check that input is valid before committing change in the collection path
        requirePreference<EditTextPreference>(CollectionHelper.PREF_COLLECTION_PATH).apply {
            setOnPreferenceChangeListener { _, newValue: Any? ->
                val newPath = newValue as String
                try {
                    CollectionHelper.initializeAnkiDroidDirectory(File(newPath))
                    launchCatchingTask {
                        CollectionManager.discardBackend()
                        val deckPicker = Intent(requireContext(), DeckPicker::class.java)
                        deckPicker.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(deckPicker)
                    }
                    true
                } catch (e: StorageAccessException) {
                    // TODO: Request MANAGE_EXTERNAL_STORAGE
                    Timber.e(e, "Could not initialize directory: %s", newPath)
                    AlertDialog.Builder(requireContext()).show {
                        setTitle(R.string.dialog_collection_path_not_dir)
                        setPositiveButton(R.string.dialog_ok) { _, _ -> }
                        setNegativeButton(R.string.reset_custom_buttons) { _, _ ->
                            text = CollectionHelper.getDefaultAnkiDroidDirectory(requireContext()).absolutePath
                        }
                    }
                    false
                }
            }
        }

        val ttsPref = requirePreference<SwitchPreferenceCompat>(R.string.tts_key)
        ttsPref.setOnPreferenceChangeListener { _, isChecked ->
            if (!(isChecked as Boolean)) return@setOnPreferenceChangeListener true
            AlertDialog.Builder(requireContext()).show {
                setIcon(R.drawable.ic_warning)
                setMessage(R.string.readtext_deprecation_warn)
                setNegativeButton(R.string.dialog_cancel) { _, _ -> ttsPref.isChecked = false }
                setNeutralButton(R.string.scoped_storage_learn_more) { _, _ ->
                    ttsPref.isChecked = false
                    requireContext().openUrl(R.string.link_tts)
                }
                setPositiveButton(R.string.dialog_ok) { _, _ -> }
                setOnCancelListener { ttsPref.isChecked = false }
            }
            return@setOnPreferenceChangeListener true
        }

        // Configure "Reset languages" preference
        requirePreference<Preference>(R.string.pref_reset_languages_key).setOnPreferenceClickListener {
            AlertDialog.Builder(requireContext()).show {
                setTitle(R.string.reset_languages)
                setIcon(R.drawable.ic_warning)
                setMessage(R.string.reset_languages_question)
                setPositiveButton(R.string.dialog_ok) { _, _ ->
                    if (MetaDB.resetLanguages(requireContext())) {
                        showSnackbar(R.string.reset_confirmation)
                    }
                }
                setNegativeButton(R.string.dialog_cancel) { _, _ -> }
            }
            false
        }

        /*
         * Plugins section
         */

        // Third party apps
        requirePreference<Preference>(R.string.thirdparty_apps_key).setOnPreferenceClickListener {
            requireContext().openUrl(R.string.link_third_party_api_apps)
            false
        }

        // Enable API
        requirePreference<SwitchPreferenceCompat>(R.string.enable_api_key).setOnPreferenceChangeListener { newValue ->
            val providerName = ComponentName(requireContext(), CardContentProvider::class.java.name)
            val state =
                if (newValue) {
                    Timber.i("AnkiDroid ContentProvider enabled by user")
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    Timber.i("AnkiDroid ContentProvider disabled by user")
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
            requireActivity().packageManager.setComponentEnabledSetting(providerName, state, PackageManager.DONT_KILL_APP)
        }

        setupOpenRouterApiKeySetting()
        setupOpenRouterModelSetting()
        setupSystemPromptSetting()
        setupNewStudyScreenSettings()
    }

    private fun setupOpenRouterApiKeySetting() {
        val keyStore = OpenRouterApiKeyStore(requireContext())
        val apiKeyPreference = requirePreference<Preference>(R.string.open_router_api_key_preference_key)
        apiKeyPreference.summary = apiKeySummary(keyStore)
        apiKeyPreference.setOnPreferenceClickListener {
            showOpenRouterApiKeyDialog(keyStore, apiKeyPreference)
            true
        }
    }

    private fun showOpenRouterApiKeyDialog(
        keyStore: OpenRouterApiKeyStore,
        preference: Preference,
    ) {
        val input =
            EditText(requireContext()).apply {
                inputType =
                    InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                    InputType.TYPE_TEXT_VARIATION_PASSWORD
                transformationMethod = PasswordTransformationMethod.getInstance()
                setSingleLine(true)
            }

        AlertDialog.Builder(requireContext()).show {
            setTitle(R.string.open_router_api_key_title)
            setMessage(R.string.open_router_api_key_dialog_message)
            setView(input)
            setPositiveButton(R.string.save) { _, _ ->
                val updatedApiKey = input.text.toString()
                val wasSet = keyStore.hasApiKey()
                keyStore.setApiKey(updatedApiKey)
                preference.summary = apiKeySummary(keyStore)
                if (keyStore.hasApiKey()) {
                    showSnackbar(R.string.open_router_api_key_saved)
                } else if (wasSet) {
                    showSnackbar(R.string.open_router_api_key_cleared)
                }
            }
            setNeutralButton(R.string.open_router_api_key_clear) { _, _ ->
                keyStore.clearApiKey()
                preference.summary = apiKeySummary(keyStore)
                showSnackbar(R.string.open_router_api_key_cleared)
            }
            setNegativeButton(R.string.dialog_cancel, null)
        }
    }

    private fun apiKeySummary(keyStore: OpenRouterApiKeyStore): String =
        if (keyStore.hasApiKey()) {
            getString(R.string.open_router_api_key_configured)
        } else {
            getString(R.string.open_router_api_key_not_set)
        }

    private fun setupOpenRouterModelSetting() {
        val modelStore = OpenRouterModelStore(requireContext().sharedPrefs())
        val modelPreference = requirePreference<Preference>(R.string.open_router_model_preference_key)
        modelPreference.summary = modelSummary(modelStore)
        modelPreference.setOnPreferenceClickListener {
            showModelPicker(modelStore, modelPreference)
            true
        }
    }

    private fun modelSummary(modelStore: OpenRouterModelStore): String =
        modelStore.getSelectedModel() ?: OpenRouterNoteEditorRewriter.DEFAULT_MODEL

    /** Fetches the OpenRouter catalogue and shows a single-choice list with pricing (AIED-13). */
    private fun showModelPicker(
        modelStore: OpenRouterModelStore,
        preference: Preference,
    ) {
        launchCatchingTask {
            val models =
                try {
                    val keyStore = OpenRouterApiKeyStore(requireContext())
                    withProgress(getString(R.string.ai_model_loading)) {
                        OpenRouterModelCatalog(apiKeyProvider = { keyStore.getApiKey() }).fetchModels()
                    }
                } catch (e: NoteEditorRewriteException) {
                    Timber.w(e, "Failed to fetch OpenRouter models")
                    showSnackbar(R.string.ai_model_load_failed)
                    showManualModelEntry(modelStore, preference)
                    return@launchCatchingTask
                }
            if (models.isEmpty()) {
                showSnackbar(R.string.ai_model_load_failed)
                showManualModelEntry(modelStore, preference)
                return@launchCatchingTask
            }
            val rows = OpenRouterModelCatalog.toDisplayRows(models)
            val adapter = ModelRowAdapter(rows, currentModelId = modelSummary(modelStore))
            AlertDialog.Builder(requireContext()).show {
                setTitle(R.string.ai_model_title)
                setAdapter(adapter) { dialog, which ->
                    val row = rows[which]
                    if (row is ModelCatalogRow.ModelEntry) {
                        modelStore.setSelectedModel(row.model.id)
                        preference.summary = modelSummary(modelStore)
                        showSnackbar(getString(R.string.ai_model_selected, row.model.id))
                    }
                    dialog.dismiss()
                }
                setNeutralButton(R.string.ai_model_enter_manually) { _, _ ->
                    showManualModelEntry(modelStore, preference)
                }
                setNegativeButton(R.string.dialog_cancel, null)
            }
        }
    }

    /** List adapter for the model picker: non-selectable provider headers + two-line model rows with pricing. */
    private inner class ModelRowAdapter(
        private val rows: List<ModelCatalogRow>,
        private val currentModelId: String,
    ) : ArrayAdapter<ModelCatalogRow>(requireContext(), 0, rows) {
        override fun getViewTypeCount(): Int = 2

        override fun getItemViewType(position: Int): Int = if (rows[position] is ModelCatalogRow.ProviderHeader) 0 else 1

        override fun areAllItemsEnabled(): Boolean = false

        override fun isEnabled(position: Int): Boolean = rows[position] is ModelCatalogRow.ModelEntry

        override fun getView(
            position: Int,
            convertView: View?,
            parent: ViewGroup,
        ): View =
            when (val row = rows[position]) {
                is ModelCatalogRow.ProviderHeader -> {
                    val view =
                        convertView ?: layoutInflater.inflate(android.R.layout.simple_list_item_1, parent, false)
                    view.findViewById<TextView>(android.R.id.text1).apply {
                        text = row.provider
                        setTypeface(typeface, Typeface.BOLD)
                        isEnabled = false
                    }
                    view
                }
                is ModelCatalogRow.ModelEntry -> {
                    val view =
                        convertView ?: layoutInflater.inflate(android.R.layout.simple_list_item_2, parent, false)
                    val current = row.model.id == currentModelId
                    view.findViewById<TextView>(android.R.id.text1).text =
                        if (current) "${row.model.name}  ✓" else row.model.name
                    view.findViewById<TextView>(android.R.id.text2).text = modelPrice(row.model)
                    view
                }
            }
    }

    private fun showManualModelEntry(
        modelStore: OpenRouterModelStore,
        preference: Preference,
    ) {
        val input =
            EditText(requireContext()).apply {
                setSingleLine(true)
                setText(modelSummary(modelStore))
            }
        AlertDialog.Builder(requireContext()).show {
            setTitle(R.string.ai_model_title)
            setMessage(R.string.ai_model_manual_message)
            setView(input)
            setPositiveButton(R.string.save) { _, _ ->
                modelStore.setSelectedModel(input.text.toString())
                preference.summary = modelSummary(modelStore)
            }
            setNegativeButton(R.string.dialog_cancel, null)
        }
    }

    private fun modelPrice(model: OpenRouterModel): String {
        val prompt = model.promptPerMillion
        val completion = model.completionPerMillion
        return if (prompt != null && completion != null) {
            getString(R.string.ai_model_price_format, prompt, completion)
        } else {
            getString(R.string.ai_model_price_unknown)
        }
    }

    // --- Versioned system prompts (AIED-14) ---

    private fun setupSystemPromptSetting() {
        val store = PromptPresetStore(requireContext().sharedPrefs())
        val preference = requirePreference<Preference>(R.string.ai_system_prompt_preference_key)
        preference.summary = systemPromptSummary(store)
        preference.setOnPreferenceClickListener {
            showSystemPromptVersions(store, preference)
            true
        }
    }

    private fun systemPromptSummary(store: PromptPresetStore): String {
        val activeId = store.getActiveSystemPromptVersionId()
        val active = store.getSystemPromptVersions().firstOrNull { it.id == activeId }
        return active?.name ?: getString(R.string.ai_system_prompt_default)
    }

    private fun showSystemPromptVersions(
        store: PromptPresetStore,
        preference: Preference,
    ) {
        val versions = store.getSystemPromptVersions()
        val activeId = store.getActiveSystemPromptVersionId()
        // Row 0 is the synthetic, read-only Default (null id); the rest are stored versions.
        val rowIds = listOf<String?>(null) + versions.map { it.id }
        val rowNames = listOf(getString(R.string.ai_system_prompt_default)) + versions.map { it.name }
        val labels = rowIds.mapIndexed { index, id -> if (id == activeId) "${rowNames[index]}  ✓" else rowNames[index] }.toTypedArray()
        AlertDialog.Builder(requireContext()).show {
            setTitle(R.string.ai_system_prompt_title)
            setItems(labels) { _, which ->
                val id = rowIds[which]
                if (id == null) {
                    showDefaultActions(store, preference)
                } else {
                    showVersionActions(store, preference, versions.first { it.id == id })
                }
            }
            setNeutralButton(R.string.ai_system_prompt_new) { _, _ ->
                createNewSystemPromptVersion(store, preference)
            }
            setNegativeButton(R.string.dialog_cancel, null)
        }
    }

    private fun showDefaultActions(
        store: PromptPresetStore,
        preference: Preference,
    ) {
        val actions =
            arrayOf(
                getString(R.string.ai_system_prompt_activate),
                getString(R.string.ai_system_prompt_duplicate),
            )
        AlertDialog.Builder(requireContext()).show {
            setTitle(R.string.ai_system_prompt_default)
            setItems(actions) { _, which ->
                when (which) {
                    0 -> {
                        store.setActiveSystemPromptVersion(null)
                        preference.summary = systemPromptSummary(store)
                        showSnackbar(getString(R.string.ai_system_prompt_activated, getString(R.string.ai_system_prompt_default)))
                    }
                    1 -> {
                        val id = store.addSystemPromptVersion(nextVersionName(store), OpenRouterNoteEditorRewriter.DEFAULT_SYSTEM_PROMPT)
                        preference.summary = systemPromptSummary(store)
                        store.getSystemPromptVersions().firstOrNull { it.id == id }?.let {
                            editSystemPromptVersion(store, preference, it)
                        }
                    }
                }
            }
            setNegativeButton(R.string.dialog_cancel, null)
        }
    }

    private fun showVersionActions(
        store: PromptPresetStore,
        preference: Preference,
        version: SystemPromptVersion,
    ) {
        val actions =
            arrayOf(
                getString(R.string.ai_system_prompt_activate),
                getString(R.string.ai_system_prompt_edit),
                getString(R.string.ai_system_prompt_duplicate),
                getString(R.string.ai_system_prompt_delete),
            )
        AlertDialog.Builder(requireContext()).show {
            setTitle(version.name)
            setItems(actions) { _, which ->
                when (which) {
                    0 -> {
                        store.setActiveSystemPromptVersion(version.id)
                        preference.summary = systemPromptSummary(store)
                        showSnackbar(getString(R.string.ai_system_prompt_activated, version.name))
                    }
                    1 -> editSystemPromptVersion(store, preference, version)
                    2 -> {
                        val newId = store.duplicateSystemPromptVersion(version.id, nextVersionName(store)) ?: return@setItems
                        preference.summary = systemPromptSummary(store)
                        store.getSystemPromptVersions().firstOrNull { it.id == newId }?.let {
                            editSystemPromptVersion(store, preference, it)
                        }
                    }
                    3 -> {
                        store.deleteSystemPromptVersion(version.id)
                        preference.summary = systemPromptSummary(store)
                    }
                }
            }
            setNegativeButton(R.string.dialog_cancel, null)
        }
    }

    private fun createNewSystemPromptVersion(
        store: PromptPresetStore,
        preference: Preference,
    ) {
        val id = store.addSystemPromptVersion(nextVersionName(store), "")
        preference.summary = systemPromptSummary(store)
        store.getSystemPromptVersions().firstOrNull { it.id == id }?.let {
            editSystemPromptVersion(store, preference, it)
        }
    }

    private fun editSystemPromptVersion(
        store: PromptPresetStore,
        preference: Preference,
        version: SystemPromptVersion,
    ) {
        val input =
            EditText(requireContext()).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                minLines = 4
                setText(version.text)
            }
        AlertDialog.Builder(requireContext()).show {
            setTitle(version.name)
            setMessage(R.string.ai_system_prompt_edit_message)
            setView(input)
            setPositiveButton(R.string.save) { _, _ ->
                store.updateSystemPromptVersion(version.id, version.name, input.text.toString())
                preference.summary = systemPromptSummary(store)
            }
            setNegativeButton(R.string.dialog_cancel, null)
        }
    }

    private fun nextVersionName(store: PromptPresetStore): String =
        getString(
            R.string.ai_system_prompt_version_name,
            store.getSystemPromptVersions().size + 1,
        )

    private fun removeUnnecessaryAdvancedPrefs() {
        /* These preferences should be searchable or not based
         * on this same condition at [HeaderFragment.configureSearchBar] */
        // Disable the double scroll preference if no scrolling keys
        if (!CompatHelper.hasScrollKeys()) {
            val doubleScrolling = findPreference<SwitchPreferenceCompat>("double_scrolling")
            if (doubleScrolling != null) {
                preferenceScreen.removePreference(doubleScrolling)
            }
        }
    }

    private fun setupNewStudyScreenSettings() {
        if (!Prefs.isNewStudyScreenEnabled) return
        for (key in legacyStudyScreenSettings) {
            val keyString = getString(key)
            findPreference<Preference>(keyString)?.isVisible = false
        }
    }

    companion object {
        val legacyStudyScreenSettings =
            listOf(
                R.string.pref_reset_languages_key,
                R.string.double_scrolling_gap_key,
                R.string.tts_key,
            )
    }
}
