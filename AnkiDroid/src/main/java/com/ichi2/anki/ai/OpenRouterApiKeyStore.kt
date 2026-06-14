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
@file:Suppress("DEPRECATION")

package com.ichi2.anki.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ichi2.anki.common.utils.isRunningAsUnitTest

/**
 * Encrypted storage for OpenRouter API key.
 */
class OpenRouterApiKeyStore(
    context: Context,
) {
    private val appContext = context.applicationContext

    private val encryptedPrefs: SharedPreferences by lazy {
        if (isRunningAsUnitTest) {
            return@lazy appContext.getSharedPreferences(PREFERENCES_FILE_NAME, Context.MODE_PRIVATE)
        }

        val masterKey =
            MasterKey
                .Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

        EncryptedSharedPreferences.create(
            appContext,
            PREFERENCES_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun getApiKey(): String? {
        val key =
            encryptedPrefs.getString(OPEN_ROUTER_API_KEY, null)
                ?: encryptedPrefs.getString(LEGACY_ANTHROPIC_API_KEY, null)
        return key?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun setApiKey(apiKey: String) {
        val normalizedKey = apiKey.trim()
        if (normalizedKey.isEmpty()) {
            clearApiKey()
            return
        }
        encryptedPrefs.edit {
            putString(OPEN_ROUTER_API_KEY, normalizedKey)
            remove(LEGACY_ANTHROPIC_API_KEY)
        }
    }

    fun clearApiKey() {
        encryptedPrefs.edit {
            remove(OPEN_ROUTER_API_KEY)
            remove(LEGACY_ANTHROPIC_API_KEY)
        }
    }

    fun hasApiKey(): Boolean = getApiKey() != null

    companion object {
        private const val PREFERENCES_FILE_NAME = "ai-secure-prefs"
        private const val OPEN_ROUTER_API_KEY = "open_router_api_key"
        private const val LEGACY_ANTHROPIC_API_KEY = "anthropic_api_key"
    }
}
