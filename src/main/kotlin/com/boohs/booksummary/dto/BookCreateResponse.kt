package com.boohs.booksummary.dto

import java.time.Instant

data class BookCreateResponse(
    val bookId: String,
    val title: String,
    val createdAt: Instant,
)
