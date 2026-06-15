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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/**
 * A single OpenRouter model and its pricing, as shown in the model picker (AIED-13).
 *
 * [promptUsdPerToken]/[completionUsdPerToken] are USD per token (OpenRouter's native unit); the
 * `*PerMillion` helpers convert to the more readable per-1M-tokens figure for display.
 */
data class OpenRouterModel(
    val id: String,
    val name: String,
    val promptUsdPerToken: Double?,
    val completionUsdPerToken: Double?,
    val contextLength: Int?,
) {
    val promptPerMillion: Double?
        get() = promptUsdPerToken?.let { it * TOKENS_PER_MILLION }

    val completionPerMillion: Double?
        get() = completionUsdPerToken?.let { it * TOKENS_PER_MILLION }

    /** The OpenRouter provider prefix, e.g. `anthropic` for `anthropic/claude-sonnet-4`. */
    val provider: String
        get() = id.substringBefore('/', missingDelimiterValue = id)

    /** Combined per-1M price used to order models cheapest-first; models without pricing sort last. */
    val sortCost: Double
        get() = listOfNotNull(promptPerMillion, completionPerMillion).let { if (it.isEmpty()) Double.POSITIVE_INFINITY else it.sum() }

    companion object {
        private const val TOKENS_PER_MILLION = 1_000_000
    }
}

/** A row in the model picker list: either a provider section header or a selectable model. */
sealed interface ModelCatalogRow {
    data class ProviderHeader(
        val provider: String,
    ) : ModelCatalogRow

    data class ModelEntry(
        val model: OpenRouterModel,
    ) : ModelCatalogRow
}

/**
 * Fetches the OpenRouter model catalogue (`GET /api/v1/models`) with pricing. Mirrors the OkHttp + org.json
 * style of [OpenRouterNoteEditorRewriter] and reuses its exception types. The endpoint works without auth;
 * the API key is sent when available.
 */
class OpenRouterModelCatalog(
    private val apiKeyProvider: NoteEditorApiKeyProvider = NoteEditorApiKeyProvider { null },
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    suspend fun fetchModels(): List<OpenRouterModel> =
        withContext(Dispatchers.IO) {
            val builder =
                Request
                    .Builder()
                    .url(MODELS_URL)
                    .get()
            apiKeyProvider.getApiKey()?.trim()?.takeIf { it.isNotEmpty() }?.let {
                builder.header("Authorization", "Bearer $it")
            }
            val request = builder.build()
            try {
                httpClient.newCall(request).execute().use { response ->
                    val responseBody = response.body.string()
                    if (!response.isSuccessful) {
                        val message =
                            parseErrorMessage(responseBody) ?: "OpenRouter request failed with HTTP ${response.code}"
                        throw OpenRouterApiException(
                            statusCode = response.code,
                            message = message,
                            rawResponseBody = responseBody,
                        )
                    }
                    return@withContext parseModels(responseBody)
                }
            } catch (e: IOException) {
                throw OpenRouterNetworkException(cause = e)
            }
        }

    companion object {
        private const val MODELS_URL = "https://openrouter.ai/api/v1/models"

        /**
         * Groups [models] into picker rows: a provider header per provider (alphabetical, case-insensitive),
         * with that provider's models listed cheapest-first.
         */
        fun toDisplayRows(models: List<OpenRouterModel>): List<ModelCatalogRow> =
            models
                .groupBy { it.provider }
                .toSortedMap(String.CASE_INSENSITIVE_ORDER)
                .flatMap { (provider, providerModels) ->
                    buildList {
                        add(ModelCatalogRow.ProviderHeader(provider))
                        providerModels
                            .sortedWith(compareBy({ it.sortCost }, { it.id }))
                            .forEach { add(ModelCatalogRow.ModelEntry(it)) }
                    }
                }

        internal fun parseModels(responseBody: String): List<OpenRouterModel> {
            try {
                val data =
                    JSONObject(responseBody).optJSONArray("data")
                        ?: throw InvalidOpenRouterResponseException("OpenRouter models response has no data array")
                val models = mutableListOf<OpenRouterModel>()
                for (i in 0 until data.length()) {
                    val obj = data.optJSONObject(i) ?: continue
                    val id = obj.optString("id").trim()
                    if (id.isEmpty()) continue
                    val name = obj.optString("name").trim().ifEmpty { id }
                    val pricing = obj.optJSONObject("pricing")
                    models.add(
                        OpenRouterModel(
                            id = id,
                            name = name,
                            promptUsdPerToken = pricing?.optString("prompt")?.toDoubleOrNull(),
                            completionUsdPerToken = pricing?.optString("completion")?.toDoubleOrNull(),
                            contextLength = obj.optInt("context_length").takeIf { it > 0 },
                        ),
                    )
                }
                return models
            } catch (e: JSONException) {
                throw InvalidOpenRouterResponseException("Failed to parse OpenRouter models response", e)
            }
        }

        internal fun parseErrorMessage(responseBody: String?): String? {
            if (responseBody.isNullOrBlank()) return null
            return try {
                val error = JSONObject(responseBody).optJSONObject("error") ?: return null
                error.optString("message").trim().ifEmpty { null }
            } catch (_: JSONException) {
                null
            }
        }
    }
}
