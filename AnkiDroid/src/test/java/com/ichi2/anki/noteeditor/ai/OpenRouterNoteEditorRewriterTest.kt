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

import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterNoteEditorRewriterTest {
    @Test
    fun rewriteThrowsWhenApiKeyMissing() =
        runTest {
            val rewriter = OpenRouterNoteEditorRewriter(apiKeyProvider = NoteEditorApiKeyProvider { null })

            val thrown =
                runCatching { rewriter.rewrite("input", "instruction") }
                    .exceptionOrNull()

            assertTrue(thrown is MissingApiKeyException)
        }

    @Test
    fun rewriteSendsAuthorizationAndParsesResponse() =
        runTest {
            var capturedAuthorization: String? = null
            var capturedBody: String? = null
            val client =
                OkHttpClient
                    .Builder()
                    .addInterceptor(
                        Interceptor { chain ->
                            val request = chain.request()
                            capturedAuthorization = request.header("Authorization")
                            val buffer = Buffer()
                            request.body!!.writeTo(buffer)
                            capturedBody = buffer.readUtf8()
                            Response
                                .Builder()
                                .request(request)
                                .protocol(Protocol.HTTP_1_1)
                                .code(200)
                                .message("OK")
                                .body("""{"choices":[{"message":{"content":"rewritten text"}}]}""".toResponseBody())
                                .build()
                        },
                    ).build()

            val rewriter =
                OpenRouterNoteEditorRewriter(
                    apiKeyProvider = NoteEditorApiKeyProvider { "or-key-123" },
                    httpClient = client,
                )

            val rewritten = rewriter.rewrite("old text", "improve clarity")
            val body = requireNotNull(capturedBody)

            assertEquals("rewritten text", rewritten)
            assertEquals("Bearer or-key-123", capturedAuthorization)
            assertTrue(body.contains("\"model\":\"anthropic/claude-sonnet-4\""))
            assertTrue(body.contains("Field content:\\nold text"))
            assertTrue(body.contains("Instruction:\\nimprove clarity"))
        }

    @Test
    fun rewriteThrowsReadableApiError() =
        runTest {
            val client =
                OkHttpClient
                    .Builder()
                    .addInterceptor(
                        Interceptor { chain ->
                            Response
                                .Builder()
                                .request(chain.request())
                                .protocol(Protocol.HTTP_1_1)
                                .code(401)
                                .message("Unauthorized")
                                .body("""{"error":{"message":"Invalid API key"}}""".toResponseBody())
                                .build()
                        },
                    ).build()

            val rewriter =
                OpenRouterNoteEditorRewriter(
                    apiKeyProvider = NoteEditorApiKeyProvider { "bad-key" },
                    httpClient = client,
                )

            val thrown =
                runCatching { rewriter.rewrite("old", "new") }
                    .exceptionOrNull()

            assertTrue(thrown is OpenRouterApiException)
            thrown as OpenRouterApiException
            assertEquals(401, thrown.statusCode)
            assertEquals("Invalid API key", thrown.message)
        }
}
