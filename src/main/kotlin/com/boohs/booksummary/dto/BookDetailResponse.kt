package com.boohs.booksummary.dto

import java.time.Instant

data class BookDetailResponse(
    val bookId: String,
    val title: String,
    val documentCount: Int,
    val totalCharCount: Int,
    val documents: List<BookDocumentResponse>,
)

data class BookDocumentResponse(
    val documentId: String,
    val sequence: Int,
    val status: String,
    val title: String?,
    val preview: String?,
    val charCount: Int,
    val hasQuiz: Boolean,
    val latestScore: BookScoreResponse?,
    val createdAt: Instant,
)
