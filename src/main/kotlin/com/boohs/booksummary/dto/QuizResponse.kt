package com.boohs.booksummary.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import java.time.Instant

data class QuizRequestResponse(
    val jobId: String,
    val status: String = "PROCESSING",
)

data class QuizExistsResponse(
    val quizId: String,
    val status: String = "DONE",
)

data class QuizDetailResponse(
    val quizId: String,
    val documentId: String,
    val questions: List<QuizQuestionResponse>,
    val createdAt: Instant,
)

data class QuizQuestionResponse(
    val index: Int,
    val question: String,
    val options: List<String>,
)

data class QuizResultRequest(
    @field:Valid val answers: List<QuizAnswerRequest>,
)

data class QuizAnswerRequest(
    @field:Min(0) @field:Max(2) val index: Int,
    @field:Min(0) @field:Max(3) val selected: Int?,
)

data class QuizResultResponse(
    val resultId: String,
    val score: BookScoreResponse,
    val items: List<QuizResultItemResponse>,
    val solvedAt: Instant,
)

data class QuizResultItemResponse(
    val index: Int,
    val question: String,
    val options: List<String>,
    val selected: Int?,
    val answerIndex: Int,
    val correct: Boolean,
    val explanation: String,
)
