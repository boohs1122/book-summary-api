package com.boohs.booksummary.llm

import com.boohs.booksummary.common.BusinessException
import com.boohs.booksummary.common.ErrorCode
import com.boohs.booksummary.config.LlmProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import tools.jackson.databind.json.JsonMapper
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException

class GeminiClientTest {
    private val mapper = JsonMapper.builder().build()
    private val httpClient = mock(HttpClient::class.java)

    @Suppress("UNCHECKED_CAST")
    private val response = mock(HttpResponse::class.java) as HttpResponse<String>

    private val client = GeminiClient(LlmProperties(apiKey = "test-key", model = "test-model"), mapper, httpClient)

    @Test
    fun `응답에서 사고 과정은 제외하고 완료된 JSON 텍스트만 반환한다`() {
        stubResponse(
            200,
            mapper.writeValueAsString(
                mapOf(
                    "candidates" to
                        listOf(
                            mapOf(
                                "finishReason" to "STOP",
                                "content" to
                                    mapOf(
                                        "parts" to
                                            listOf(
                                                mapOf("thought" to true, "text" to "추론 텍스트"),
                                                mapOf("text" to SummaryFixtures.validJson),
                                            ),
                                    ),
                            ),
                        ),
                ),
            ),
        )

        assertEquals(SummaryFixtures.validJson, client.generateSummary("원문", 3))
        val request = ArgumentCaptor.forClass(HttpRequest::class.java)
        verify(httpClient).send(request.capture(), any<HttpResponse.BodyHandler<String>>())
        assertEquals(
            "test-key",
            request.value
                .headers()
                .firstValue("x-goog-api-key")
                .orElseThrow(),
        )
        assertEquals("/v1beta/models/test-model:generateContent", request.value.uri().path)
        assertEquals(null, request.value.uri().query)
    }

    @Test
    fun `한도 초과는 RATE_LIMITED이고 HTTP 실패는 LLM_FAILED다`() {
        listOf(429 to ErrorCode.RATE_LIMITED, 500 to ErrorCode.LLM_FAILED).forEach { (status, expected) ->
            stubResponse(status, "외부 오류 본문")

            val exception = assertThrows(BusinessException::class.java) { client.generateSummary("원문", 3) }
            assertEquals(expected, exception.errorCode)
            assertEquals(expected.defaultMessage, exception.message)
        }
    }

    @Test
    fun `타임아웃은 사용자에게 노출할 수 있는 LLM_FAILED로 변환한다`() {
        `when`(httpClient.send(any(HttpRequest::class.java), any<HttpResponse.BodyHandler<String>>()))
            .thenThrow(HttpTimeoutException("provider timeout"))

        val exception = assertThrows(BusinessException::class.java) { client.generateSummary("원문", 3) }
        assertEquals(ErrorCode.LLM_FAILED, exception.errorCode)
    }

    @Test
    fun `불완전하거나 잘못된 응답은 검증 재요청 대상으로 분류한다`() {
        listOf("{", "{}", "null", """{"candidates":[{"finishReason":"MAX_TOKENS"}]}""").forEach { body ->
            stubResponse(200, body)

            val exception = assertThrows(BusinessException::class.java) { client.generateSummary("원문", 3) }
            assertEquals(ErrorCode.LLM_INVALID_RESPONSE, exception.errorCode)
        }
    }

    private fun stubResponse(
        status: Int,
        body: String,
    ) {
        `when`(response.statusCode()).thenReturn(status)
        `when`(response.body()).thenReturn(body)
        `when`(httpClient.send(any(HttpRequest::class.java), any<HttpResponse.BodyHandler<String>>())).thenReturn(response)
    }
}
