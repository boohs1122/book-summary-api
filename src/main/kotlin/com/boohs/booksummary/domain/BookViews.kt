package com.boohs.booksummary.domain

import java.time.Instant

data class BookOverview(
    val book: Book,
    val documentCount: Int,
    val totalCharCount: Int,
    val lastStudiedAt: Instant?,
    val latestScore: Pair<Int, Int>?,
)

data class BookDetails(
    val book: Book,
    val documents: List<DocumentOverview>,
) {
    val documentCount: Int
        get() = documents.size

    val totalCharCount: Int
        get() = documents.sumOf { it.document.charCount }
}

data class DocumentOverview(
    val document: Document,
    val summaryTitle: String?,
    val quizId: String?,
    val latestScore: Pair<Int, Int>?,
) {
    val preview: String?
        get() = if (summaryTitle == null) document.extractedText.take(40) else null
}

data class DocumentDetails(
    val document: Document,
    val book: Book,
    val summary: SummaryContent?,
    val quizId: String?,
    val quizQuestionCount: Int?,
    val latestScore: Pair<Int, Int>?,
)
