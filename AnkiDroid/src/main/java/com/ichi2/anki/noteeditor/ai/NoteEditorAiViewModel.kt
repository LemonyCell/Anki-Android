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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * Shared (activity-scoped) bridge between [AiRewriteBottomSheet] and the note editor (AIED-01). The sheet
 * emits the user's instruction on Apply; the editor collects it and runs the rewrite. Modelled on
 * `MultimediaViewModel`.
 */
class NoteEditorAiViewModel : ViewModel() {
    /** Emits the user's instruction when Apply is tapped in the AI panel. */
    val applyRequested = MutableSharedFlow<String>()

    fun requestApply(instruction: String) {
        viewModelScope.launch {
            applyRequested.emit(instruction)
        }
    }
}
