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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/** Provides the API key stored by AIED-02 secure storage. */
fun interface NoteEditorApiKeyProvider {
    fun getApiKey(): String?
}

/** Rewrite API used by the AI editor panel (AIED-01). */
fun interface NoteEditorRewriter {
    suspend fun rewrite(
        input: String,
        instruction: String,
    ): String
}

data class NoteEditorRewriteTrace(
    val output: String,
    val rawRequestBody: String?,
    val rawResponseBody: String?,
)

interface TraceableNoteEditorRewriter : NoteEditorRewriter {
    suspend fun rewriteWithTrace(
        input: String,
        instruction: String,
    ): NoteEditorRewriteTrace

    override suspend fun rewrite(
        input: String,
        instruction: String,
    ): String = rewriteWithTrace(input, instruction).output
}

sealed class NoteEditorRewriteException(
    message: String,
    cause: Throwable? = null,
    val rawRequestBody: String? = null,
    val rawResponseBody: String? = null,
) : Exception(message, cause)

class MissingApiKeyException : NoteEditorRewriteException("API key is not set")

class OpenRouterApiException(
    val statusCode: Int,
    message: String,
    rawRequestBody: String? = null,
    rawResponseBody: String? = null,
) : NoteEditorRewriteException(message, rawRequestBody = rawRequestBody, rawResponseBody = rawResponseBody)

class InvalidOpenRouterResponseException(
    message: String,
    cause: Throwable? = null,
    rawRequestBody: String? = null,
    rawResponseBody: String? = null,
) : NoteEditorRewriteException(message, cause, rawRequestBody, rawResponseBody)

class OpenRouterNetworkException(
    cause: IOException,
    rawRequestBody: String? = null,
) : NoteEditorRewriteException("Network error while calling OpenRouter", cause, rawRequestBody = rawRequestBody)

class OpenRouterNoteEditorRewriter(
    private val apiKeyProvider: NoteEditorApiKeyProvider,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val model: String = DEFAULT_MODEL,
) : TraceableNoteEditorRewriter {
    override suspend fun rewriteWithTrace(
        input: String,
        instruction: String,
    ): NoteEditorRewriteTrace =
        withContext(Dispatchers.IO) {
            val apiKey = apiKeyProvider.getApiKey()?.trim().orEmpty()
            if (apiKey.isEmpty()) {
                throw MissingApiKeyException()
            }
            val requestBody = buildRequestBody(model = model, input = input, instruction = instruction)
            val request =
                Request
                    .Builder()
                    .url(OPENROUTER_URL)
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")
                    .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
                    .build()

            try {
                httpClient.newCall(request).execute().use { response ->
                    val responseBody = response.body.string()
                    if (!response.isSuccessful) {
                        val errorMessage =
                            parseErrorMessage(responseBody) ?: "OpenRouter request failed with HTTP ${response.code}"
                        throw OpenRouterApiException(
                            statusCode = response.code,
                            message = errorMessage,
                            rawRequestBody = requestBody,
                            rawResponseBody = responseBody,
                        )
                    }
                    return@withContext NoteEditorRewriteTrace(
                        output = parseRewrittenText(responseBody, requestBody),
                        rawRequestBody = requestBody,
                        rawResponseBody = responseBody,
                    )
                }
            } catch (e: IOException) {
                throw OpenRouterNetworkException(cause = e, rawRequestBody = requestBody)
            }
        }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private const val OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"
        private const val DEFAULT_MODEL = "anthropic/claude-sonnet-4"
        private const val SYSTEM_PROMPT =
            "Rewrite the provided note field content according to the user instruction. " +
                "Preserve meaning and formatting unless the instruction says otherwise. " +
                "Return only the rewritten field content."

        internal fun buildRequestBody(
            model: String,
            input: String,
            instruction: String,
        ): String =
            JSONObject()
                .put("model", model)
                .put(
                    "messages",
                    org.json
                        .JSONArray()
                        .put(
                            JSONObject()
                                .put("role", "system")
                                .put("content", SYSTEM_PROMPT),
                        ).put(
                            JSONObject()
                                .put("role", "user")
                                .put(
                                    "content",
                                    "Instruction:\n$instruction\n\nField content:\n$input",
                                ),
                        ),
                ).toString()

        internal fun parseRewrittenText(
            responseBody: String,
            rawRequestBody: String? = null,
        ): String {
            try {
                val root = JSONObject(responseBody)
                val choices =
                    root.optJSONArray("choices")
                        ?: throw InvalidOpenRouterResponseException(
                            message = "OpenRouter response has no choices",
                            rawRequestBody = rawRequestBody,
                            rawResponseBody = responseBody,
                        )
                if (choices.length() == 0) {
                    throw InvalidOpenRouterResponseException(
                        message = "OpenRouter response has empty choices",
                        rawRequestBody = rawRequestBody,
                        rawResponseBody = responseBody,
                    )
                }
                val message =
                    choices.getJSONObject(0).optJSONObject("message")
                        ?: throw InvalidOpenRouterResponseException(
                            message = "OpenRouter response has no message",
                            rawRequestBody = rawRequestBody,
                            rawResponseBody = responseBody,
                        )
                val content = message.optString("content").trim()
                if (content.isEmpty()) {
                    throw InvalidOpenRouterResponseException(
                        message = "OpenRouter response content is empty",
                        rawRequestBody = rawRequestBody,
                        rawResponseBody = responseBody,
                    )
                }
                return content
            } catch (e: JSONException) {
                throw InvalidOpenRouterResponseException(
                    message = "Failed to parse OpenRouter response",
                    cause = e,
                    rawRequestBody = rawRequestBody,
                    rawResponseBody = responseBody,
                )
            }
        }

        internal fun parseErrorMessage(responseBody: String?): String? {
            if (responseBody.isNullOrBlank()) {
                return null
            }
            return try {
                val root = JSONObject(responseBody)
                val error = root.optJSONObject("error") ?: return null
                val message = error.optString("message").trim()
                message.ifEmpty { null }
            } catch (_: JSONException) {
                null
            }
        }
    }
}
