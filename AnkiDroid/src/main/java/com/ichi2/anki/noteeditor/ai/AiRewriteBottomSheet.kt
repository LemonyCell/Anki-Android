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
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ichi2.anki.R
import com.ichi2.anki.databinding.FragmentBottomsheetAiRewriteBinding
import dev.androidbroadcast.vbpd.viewBinding

/**
 * AI editing panel (AIED-01): collects a free-text instruction and emits it via [NoteEditorAiViewModel]
 * when Apply is tapped. The note editor performs the actual rewrite on the field it captured before
 * opening this sheet. The input scope (selection vs. whole field) is shown as a label, passed via args.
 */
class AiRewriteBottomSheet : BottomSheetDialogFragment(R.layout.fragment_bottomsheet_ai_rewrite) {
    private val viewModel: NoteEditorAiViewModel by activityViewModels()
    private val binding by viewBinding(FragmentBottomsheetAiRewriteBinding::bind)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        val editingSelection = requireArguments().getBoolean(ARG_EDITING_SELECTION)
        binding.aiRewriteTargetLabel.setText(
            if (editingSelection) R.string.ai_rewrite_editing_selection else R.string.ai_rewrite_editing_field,
        )

        // Apply is only enabled once a non-blank instruction has been entered.
        binding.aiRewriteApply.isEnabled = false
        binding.aiRewritePrompt.doAfterTextChanged { text ->
            binding.aiRewriteApply.isEnabled = !text.isNullOrBlank()
        }

        binding.aiRewriteApply.setOnClickListener {
            val instruction = (binding.aiRewritePrompt.text ?: "").toString().trim()
            if (instruction.isEmpty()) return@setOnClickListener
            viewModel.requestApply(instruction)
            dismiss()
        }

        binding.aiRewritePrompt.requestFocus()
    }

    companion object {
        const val TAG = "AiRewriteBottomSheet"
        private const val ARG_EDITING_SELECTION = "editingSelection"

        fun newInstance(editingSelection: Boolean): AiRewriteBottomSheet =
            AiRewriteBottomSheet().apply {
                arguments = bundleOf(ARG_EDITING_SELECTION to editingSelection)
            }
    }
}
