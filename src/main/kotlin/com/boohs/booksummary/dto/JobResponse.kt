package com.boohs.booksummary.dto

import com.boohs.booksummary.common.ErrorResponse
import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant

data class JobAcceptedResponse(
    val jobId: String,
    val status: String = "PROCESSING",
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class JobResponse(
    val jobId: String,
    val type: String,
    val status: String,
    val createdAt: Instant,
    val documentId: String?,
    val completedAt: Instant?,
    val error: ErrorResponse.ErrorBody?,
)
