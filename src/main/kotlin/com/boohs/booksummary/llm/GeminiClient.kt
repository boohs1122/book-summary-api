package com.boohs.booksummary.llm

import com.boohs.booksummary.common.BusinessException
import com.boohs.booksummary.common.ErrorCode
import com.boohs.booksummary.config.LlmProperties
import com.boohs.booksummary.llm.prompt.SummaryPrompt
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@Component
class GeminiClient(
    private val properties: LlmProperties,
    private val jsonMapper: JsonMapper,
    private val llmHttpClient: HttpClient,
) : LlmClient {
    private val schemaTemplate =
        ClassPathResource("llm/summary-schema.json").inputStream.use { jsonMapper.readTree(it) }

    override fun generateSummary(
        text: String,
        keyPointCount: Int,
    ): String {
        if (properties.apiKey.isBlank() || !MODEL_PATTERN.matches(properties.model)) {
            throw BusinessException(ErrorCode.LLM_FAILED)
        }
        require(keyPointCount in 3..6)
        val schema = schemaTemplate.deepCopy()
        (schema.path("properties").path("keyPoints") as ObjectNode).apply {
            put("minItems", keyPointCount)
            put("maxItems", keyPointCount)
        }
        val body =
            mapOf(
                "systemInstruction" to mapOf("parts" to listOf(mapOf("text" to SummaryPrompt.instruction(keyPointCount)))),
                "contents" to listOf(mapOf("role" to "user", "parts" to listOf(mapOf("text" to text)))),
                "generationConfig" to
                    mapOf(
                        "responseMimeType" to "application/json",
                        "responseJsonSchema" to schema,
                        "maxOutputTokens" to 4096,
                    ),
            )
        val request =
            HttpRequest
                .newBuilder(URI("${properties.baseUrl.toString().trimEnd('/')}/models/${properties.model}:generateContent"))
                .timeout(properties.timeout)
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", properties.apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body)))
                .build()
        val response =
            try {
                llmHttpClient.send(request, HttpResponse.BodyHandlers.ofString())
            } catch (_: IOException) {
                throw BusinessException(ErrorCode.LLM_FAILED)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw BusinessException(ErrorCode.LLM_FAILED)
            }
        if (response.statusCode() == 429) throw BusinessException(ErrorCode.RATE_LIMITED)
        if (response.statusCode() !in 200..299) throw BusinessException(ErrorCode.LLM_FAILED)

        val root =
            try {
                jsonMapper.readTree(response.body())
            } catch (_: JacksonException) {
                throw BusinessException(ErrorCode.LLM_INVALID_RESPONSE)
            }
        val candidate = root.path("candidates").path(0)
        if (candidate.path("finishReason").stringValue("") != "STOP") throw BusinessException(ErrorCode.LLM_INVALID_RESPONSE)
        val parts = candidate.path("content").path("parts")
        if (!parts.isArray) throw BusinessException(ErrorCode.LLM_INVALID_RESPONSE)
        val result =
            parts
                .filter { !it.path("thought").asBoolean(false) && it.path("text").isString }
                .joinToString("") { it.path("text").stringValue() }
        if (result.isBlank()) throw BusinessException(ErrorCode.LLM_INVALID_RESPONSE)
        return result
    }

    companion object {
        private val MODEL_PATTERN = Regex("[a-z0-9][a-z0-9.-]+")
    }
}
