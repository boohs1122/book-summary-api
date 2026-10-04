package com.boohs.booksummary.llm.validator

import com.boohs.booksummary.common.BusinessException
import com.boohs.booksummary.common.ErrorCode
import com.boohs.booksummary.domain.QuizContent
import com.boohs.booksummary.domain.QuizQuestion
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

@Component
class QuizValidator(
    private val mapper: JsonMapper,
) {
    fun parse(json: String): QuizContent {
        val root =
            try {
                mapper.readTree(json)
            } catch (_: JacksonException) {
                invalid()
            }
        val nodes = root.path("questions")
        if (!root.isObject || !nodes.isArray || nodes.size() != QUESTION_COUNT) invalid()
        return QuizContent(
            nodes.toList().map { node ->
                val options = node.path("options")
                val answer = node.path("answerIndex")
                if (!options.isArray || options.size() != OPTION_COUNT || !answer.isInt || answer.intValue() !in 0..3) invalid()
                QuizQuestion(
                    text(node, "question"),
                    options.toList().map { option ->
                        if (!option.isString || option.stringValue().isBlank()) invalid()
                        option.stringValue().trim()
                    },
                    answer.intValue(),
                    text(node, "explanation"),
                )
            },
        )
    }

    fun serialize(content: QuizContent): String = mapper.writeValueAsString(content)

    fun parseStored(json: String): QuizContent = parse(json)

    private fun text(
        node: JsonNode,
        field: String,
    ): String {
        val value = node.path(field)
        if (!value.isString || value.stringValue().isBlank()) invalid()
        return value.stringValue().trim()
    }

    private fun invalid(): Nothing = throw BusinessException(ErrorCode.LLM_INVALID_RESPONSE)

    companion object {
        const val QUESTION_COUNT = 3
        const val OPTION_COUNT = 4
    }
}
