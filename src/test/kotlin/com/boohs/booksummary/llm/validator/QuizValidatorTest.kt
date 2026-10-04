package com.boohs.booksummary.llm.validator

import com.boohs.booksummary.common.BusinessException
import com.boohs.booksummary.common.ErrorCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class QuizValidatorTest {
    private val mapper = JsonMapper.builder().build()
    private val validator = QuizValidator(mapper)
    private val question =
        mapOf(
            "question" to "문항",
            "options" to listOf("가", "나", "다", "라"),
            "answerIndex" to 2,
            "explanation" to "설명",
        )

    @Test
    fun `정확히 세 문항과 네 선택지 및 정답 범위를 확인하고 직렬화 왕복한다`() {
        val content = validator.parse(mapper.writeValueAsString(mapOf("questions" to List(3) { question })))

        assertEquals(3, content.questions.size)
        assertEquals(content, validator.parseStored(validator.serialize(content)))
    }

    @Test
    fun `문항 수 선택지 수 정답 범위와 필수 텍스트 오류를 거부한다`() {
        val badQuestions =
            listOf(
                emptyList(),
                List(2) { question },
                List(3) { question + ("options" to listOf("하나", "둘", "셋")) },
                List(3) { question + ("answerIndex" to 4) },
                List(3) { question + ("explanation" to " ") },
            )

        badQuestions.forEach { questions ->
            val error =
                assertThrows(BusinessException::class.java) {
                    validator.parse(mapper.writeValueAsString(mapOf("questions" to questions)))
                }
            assertEquals(ErrorCode.LLM_INVALID_RESPONSE, error.errorCode)
        }
    }
}
