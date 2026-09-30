package com.boohs.booksummary.dto

import java.time.Instant

data class BookListResponse(
    val items: List<BookItemResponse>,
    val nextCursor: String?,
)

data class BookItemResponse(
    val bookId: String,
    val title: String,
    val documentCount: Int,
    val totalCharCount: Int,
    val lastStudiedAt: Instant?,
    val latestScore: BookScoreResponse?,
    val createdAt: Instant,
)

data class BookScoreResponse(
    val correct: Int,
    val total: Int,
)
