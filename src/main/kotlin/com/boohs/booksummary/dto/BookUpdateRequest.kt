package com.boohs.booksummary.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class BookUpdateRequest(
    val title: String,
) {
    @get:NotBlank
    @get:Size(min = 1, max = 100)
    val normalizedTitle: String
        get() = title.trim()
}

data class BookUpdateResponse(
    val bookId: String,
    val title: String,
)
