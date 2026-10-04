package com.boohs.booksummary.llm.validator

import com.boohs.booksummary.common.BusinessException
import com.boohs.booksummary.common.ErrorCode
import com.boohs.booksummary.domain.SummaryContent
import com.boohs.booksummary.domain.SummaryKeyPoint
import com.boohs.booksummary.domain.SummaryTerm
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

@Component
class SummaryValidator(
    private val jsonMapper: JsonMapper,
) {
    fun parse(
        json: String,
        expectedKeyPointCount: Int? = null,
    ): SummaryContent {
        val root =
            try {
                jsonMapper.readTree(json)
            } catch (_: JacksonException) {
                invalid()
            }
        if (!root.isObject) invalid()
        val points = root.path("keyPoints")
        val terms = root.path("terms")
        if (!points.isArray || points.size() !in 3..6 || !terms.isArray) invalid()
        if (expectedKeyPointCount != null && points.size() != expectedKeyPointCount) invalid()

        return SummaryContent(
            title = requiredText(root, "title"),
            keyPoints =
                points.toList().map { point ->
                    val type = requiredText(point, "type")
                    SummaryKeyPoint(
                        type = type.takeIf { it in TYPES } ?: "definition",
                        heading = requiredText(point, "heading"),
                        detail = requiredText(point, "detail"),
                    )
                },
            terms = terms.toList().map { term -> SummaryTerm(requiredText(term, "term"), requiredText(term, "meaning")) },
        )
    }

    fun serialize(summary: SummaryContent): String = jsonMapper.writeValueAsString(summary)

    private fun requiredText(
        node: JsonNode,
        field: String,
    ): String {
        val value = node.path(field)
        if (!value.isString || value.stringValue().isBlank()) invalid()
        return value.stringValue().trim()
    }

    private fun invalid(): Nothing = throw BusinessException(ErrorCode.LLM_INVALID_RESPONSE)

    companion object {
        private val TYPES = setOf("definition", "mechanism", "cause", "comparison", "caution", "example")
    }
}
