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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterModelCatalogTest {
    private val sampleJson =
        """
        {"data":[
          {"id":"anthropic/claude-sonnet-4","name":"Anthropic: Claude Sonnet 4","context_length":200000,
           "pricing":{"prompt":"0.000003","completion":"0.000015"}},
          {"id":"openai/gpt-4o","name":"OpenAI: GPT-4o",
           "pricing":{"prompt":"0.0000025","completion":"0.00001"}},
          {"id":"","name":"ignored, no id"}
        ]}
        """.trimIndent()

    @Test
    fun parsesModelsWithPricingAndSkipsEntriesWithoutId() {
        val models = OpenRouterModelCatalog.parseModels(sampleJson)
        assertEquals(2, models.size)

        val sonnet = models[0]
        assertEquals("anthropic/claude-sonnet-4", sonnet.id)
        assertEquals("Anthropic: Claude Sonnet 4", sonnet.name)
        assertEquals(200000, sonnet.contextLength)
        // 0.000003 USD/token -> 3.0 USD per 1M tokens
        assertEquals(3.0, sonnet.promptPerMillion!!, 1e-9)
        assertEquals(15.0, sonnet.completionPerMillion!!, 1e-9)
    }

    @Test
    fun missingPricingYieldsNullPrices() {
        val models = OpenRouterModelCatalog.parseModels("""{"data":[{"id":"x/y","name":"Y"}]}""")
        assertEquals(1, models.size)
        assertNull(models[0].promptUsdPerToken)
        assertNull(models[0].promptPerMillion)
    }

    @Test
    fun invalidJsonThrows() {
        assertThrows(InvalidOpenRouterResponseException::class.java) {
            OpenRouterModelCatalog.parseModels("not json")
        }
    }

    @Test
    fun missingDataArrayThrows() {
        assertThrows(InvalidOpenRouterResponseException::class.java) {
            OpenRouterModelCatalog.parseModels("""{"object":"list"}""")
        }
    }

    @Test
    fun fetchModelsReturnsParsedListOnSuccess() =
        runTest {
            val client = clientReturning(code = 200, body = sampleJson)
            val catalog = OpenRouterModelCatalog(httpClient = client)
            val models = catalog.fetchModels()
            assertEquals(2, models.size)
            assertEquals("openai/gpt-4o", models[1].id)
        }

    @Test
    fun fetchModelsThrowsOnHttpError() =
        runTest {
            val client = clientReturning(code = 500, body = """{"error":{"message":"boom"}}""")
            val catalog = OpenRouterModelCatalog(httpClient = client)
            val thrown =
                assertThrows(OpenRouterApiException::class.java) {
                    kotlinx.coroutines.runBlocking { catalog.fetchModels() }
                }
            assertEquals(500, thrown.statusCode)
            assertTrue(thrown.message!!.contains("boom"))
        }

    private fun clientReturning(
        code: Int,
        body: String,
    ): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(
                Interceptor { chain ->
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(code)
                        .message(if (code == 200) "OK" else "Error")
                        .body(body.toResponseBody())
                        .build()
                },
            ).build()
}
