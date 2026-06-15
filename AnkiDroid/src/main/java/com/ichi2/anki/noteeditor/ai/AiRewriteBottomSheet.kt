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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.ichi2.anki.R
import com.ichi2.anki.databinding.FragmentBottomsheetAiRewriteBinding
import dev.androidbroadcast.vbpd.viewBinding

/**
 * AI editing panel (AIED-01 + AIED-03): collects a typed instruction and optional checked presets,
 * builds the effective prompt, and emits it via [NoteEditorAiViewModel] when Apply is tapped. The note
 * editor performs the actual rewrite on the field it captured before opening this sheet. The input scope
 * (selection vs. whole field) is shown as a label, passed via args.
 */
class AiRewriteBottomSheet : BottomSheetDialogFragment(R.layout.fragment_bottomsheet_ai_rewrite) {
    private val viewModel: NoteEditorAiViewModel by activityViewModels()
    private val binding by viewBinding(FragmentBottomsheetAiRewriteBinding::bind)
    private val promptPresetStore by lazy { PromptPresetStore.fromContext(requireContext()) }
    private val checkedPresetKeys = mutableSetOf<String>()
    private var visiblePresets: List<PromptPreset> = emptyList()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        val editingSelection = requireArguments().getBoolean(ARG_EDITING_SELECTION)
        binding.aiRewriteTargetLabel.setText(
            if (editingSelection) R.string.ai_rewrite_editing_selection else R.string.ai_rewrite_editing_field,
        )

        reloadPresets()
        binding.aiRewritePrompt.doAfterTextChanged { text ->
            binding.aiRewriteApply.isEnabled = effectivePrompt(text?.toString() ?: "").isNotBlank()
        }

        binding.aiRewriteApply.setOnClickListener {
            val typedPrompt = (binding.aiRewritePrompt.text ?: "").toString()
            val checkedPresetPrompts = getCheckedPresetPrompts()
            val instruction = PromptPresetRequestBuilder.buildEffectivePrompt(typedPrompt, checkedPresetPrompts)
            if (instruction.isBlank()) return@setOnClickListener

            promptPresetStore.saveSubmittedPrompt(typedPrompt)
            checkedPresetPrompts.forEach(promptPresetStore::recordPresetUsage)
            viewModel.requestApply(instruction)
            dismiss()
        }

        binding.aiRewriteApply.isEnabled = effectivePrompt((binding.aiRewritePrompt.text ?: "").toString()).isNotBlank()
        binding.aiRewritePrompt.requestFocus()
    }

    private fun reloadPresets() {
        visiblePresets = promptPresetStore.getVisiblePresets()
        binding.aiRewritePresetsContainer.removeAllViews()
        binding.aiRewritePresetsEmpty.visibility = if (visiblePresets.isEmpty()) View.VISIBLE else View.GONE
        binding.aiRewritePresetsScroll.visibility = if (visiblePresets.isEmpty()) View.GONE else View.VISIBLE

        visiblePresets.forEach { preset ->
            val normalizedPrompt = PromptPresetStore.normalizePrompt(preset.promptText)
            val row =
                LayoutInflater.from(requireContext()).inflate(
                    R.layout.item_ai_rewrite_preset,
                    binding.aiRewritePresetsContainer,
                    false,
                )
            val checkBox = row.findViewById<MaterialCheckBox>(R.id.ai_rewrite_preset_checkbox)
            val removeButton = row.findViewById<MaterialButton>(R.id.ai_rewrite_preset_remove)
            checkBox.text = preset.promptText
            checkBox.isChecked = normalizedPrompt in checkedPresetKeys
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    checkedPresetKeys.add(normalizedPrompt)
                } else {
                    checkedPresetKeys.remove(normalizedPrompt)
                }
                binding.aiRewriteApply.isEnabled =
                    effectivePrompt((binding.aiRewritePrompt.text ?: "").toString()).isNotBlank()
            }
            removeButton.setOnClickListener {
                if (promptPresetStore.softDeletePreset(preset.promptText)) {
                    checkedPresetKeys.remove(normalizedPrompt)
                    reloadPresets()
                    binding.aiRewriteApply.isEnabled =
                        effectivePrompt((binding.aiRewritePrompt.text ?: "").toString()).isNotBlank()
                }
            }
            binding.aiRewritePresetsContainer.addView(row)
        }
    }

    private fun getCheckedPresetPrompts(): List<String> =
        visiblePresets
            .filter { PromptPresetStore.normalizePrompt(it.promptText) in checkedPresetKeys }
            .map { it.promptText }

    private fun effectivePrompt(typedPrompt: String): String =
        PromptPresetRequestBuilder.buildEffectivePrompt(
            typedPrompt = typedPrompt,
            checkedPresetPrompts = getCheckedPresetPrompts(),
        )

    companion object {
        const val TAG = "AiRewriteBottomSheet"
        private const val ARG_EDITING_SELECTION = "editingSelection"

        fun newInstance(editingSelection: Boolean): AiRewriteBottomSheet =
            AiRewriteBottomSheet().apply {
                arguments = bundleOf(ARG_EDITING_SELECTION to editingSelection)
            }
    }
}
