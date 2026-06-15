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

package com.ichi2.anki.ai

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Stores the OpenRouter model id chosen in the model picker (AIED-13). The id is not secret, so plain
 * [SharedPreferences] is used (unlike the encrypted [OpenRouterApiKeyStore]).
 */
class OpenRouterModelStore(
    private val sharedPreferences: SharedPreferences,
) {
    fun getSelectedModel(): String? = sharedPreferences.getString(SELECTED_MODEL_KEY, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun setSelectedModel(modelId: String) {
        val normalized = modelId.trim()
        sharedPreferences.edit {
            if (normalized.isEmpty()) remove(SELECTED_MODEL_KEY) else putString(SELECTED_MODEL_KEY, normalized)
        }
    }

    companion object {
        private const val SELECTED_MODEL_KEY = "open_router_selected_model"
    }
}
