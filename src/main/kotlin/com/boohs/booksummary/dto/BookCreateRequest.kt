package com.boohs.booksummary.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class BookCreateRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val title: String,
)
