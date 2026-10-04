package com.boohs.booksummary.llm

interface LlmClient {
    fun generateSummary(
        text: String,
        keyPointCount: Int,
    ): String
}
