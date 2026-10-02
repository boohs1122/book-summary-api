package com.boohs.booksummary.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class DocumentCreateRequest(
    @field:NotBlank
    val bookId: String,
    @field:Size(max = 10000)
    val text: String,
)
