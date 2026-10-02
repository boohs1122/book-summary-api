package com.boohs.booksummary.llm.validator

import com.boohs.booksummary.common.BusinessException
import com.boohs.booksummary.common.ErrorCode
import com.boohs.booksummary.llm.SummaryFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class SummaryValidatorTest {
    private val mapper = JsonMapper.builder().build()
    private val validator = SummaryValidator(mapper)

    @Test
    fun `요약을 검증하고 모르는 핵심 항목 유형은 definition으로 치환한다`() {
        val summary = validator.parse(SummaryFixtures.validJson.replace("mechanism", "unknown"))

        assertEquals("프로세스 상태 전이와 PCB", summary.title)
        assertEquals(3, summary.keyPoints.size)
        assertEquals("definition", summary.keyPoints[1].type)
        assertEquals("PCB", summary.terms.single().term)
        assertEquals(summary, validator.parse(validator.serialize(summary)))
    }

    @Test
    fun `파싱 실패와 누락 및 잘못된 타입과 빈 필드를 거부한다`() {
        val invalidResponses =
            listOf(
                "{",
                "null",
                "[]",
                SummaryFixtures.validJson.replace("\"title\":", "\"otherTitle\":"),
                SummaryFixtures.validJson.replace("\"heading\":\"프로세스\"", "\"heading\":\" \""),
                SummaryFixtures.validJson.replace("\"detail\":\"실행 중인 프로그램이다.\"", "\"detail\":42"),
                SummaryFixtures.validJson.replace("\"terms\":", "\"otherTerms\":"),
                SummaryFixtures.validJson.replace("\"meaning\":\"프로세스 제어 블록\"", "\"meaning\":null"),
            )

        invalidResponses.forEach { response ->
            val exception = assertThrows(BusinessException::class.java) { validator.parse(response) }
            assertEquals(ErrorCode.LLM_INVALID_RESPONSE, exception.errorCode)
        }
    }

    @Test
    fun `핵심 항목 수가 범위를 벗어나거나 요청한 개수와 다르면 거부한다`() {
        listOf(2, 4, 7).forEach { count ->
            val points = (1..count).map { mapOf("type" to "definition", "heading" to "항목", "detail" to "설명") }
            val response = mapper.writeValueAsString(mapOf("title" to "제목", "keyPoints" to points, "terms" to emptyList<Any>()))

            val exception = assertThrows(BusinessException::class.java) { validator.parse(response, expectedKeyPointCount = 3) }
            assertEquals(ErrorCode.LLM_INVALID_RESPONSE, exception.errorCode)
        }
    }
}
