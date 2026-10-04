package com.boohs.booksummary.dto

import java.time.Instant

data class DocumentDetailResponse(
    val documentId: String,
    val bookId: String,
    val bookTitle: String,
    val sequence: Int,
    val status: String,
    val charCount: Int,
    val extractedText: String,
    val summary: SummaryResponse?,
    val quiz: DocumentQuizResponse?,
    val createdAt: Instant,
)

data class SummaryResponse(
    val title: String,
    val keyPoints: List<SummaryKeyPointResponse>,
    val terms: List<SummaryTermResponse>,
)

data class SummaryKeyPointResponse(
    val type: String,
    val heading: String,
    val detail: String,
)

data class SummaryTermResponse(
    val term: String,
    val meaning: String,
)

data class DocumentQuizResponse(
    val exists: Boolean,
    val quizId: String? = null,
    val questionCount: Int? = null,
    val latestScore: BookScoreResponse? = null,
)
