package com.boohs.booksummary.controller

import com.boohs.booksummary.config.FirebaseAuthInterceptor
import com.boohs.booksummary.domain.QuizAnswer
import com.boohs.booksummary.dto.BookScoreResponse
import com.boohs.booksummary.dto.QuizDetailResponse
import com.boohs.booksummary.dto.QuizExistsResponse
import com.boohs.booksummary.dto.QuizQuestionResponse
import com.boohs.booksummary.dto.QuizRequestResponse
import com.boohs.booksummary.dto.QuizResultItemResponse
import com.boohs.booksummary.dto.QuizResultRequest
import com.boohs.booksummary.dto.QuizResultResponse
import com.boohs.booksummary.service.QuizRequestResult
import com.boohs.booksummary.service.QuizService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class QuizController(
    private val quizzes: QuizService,
) {
    @PostMapping("/api/v1/documents/{documentId}/quiz")
    fun request(
        @RequestAttribute(FirebaseAuthInterceptor.AUTH_UID_ATTRIBUTE) uid: String,
        @PathVariable documentId: String,
    ): ResponseEntity<Any> =
        when (val result = quizzes.request(uid, documentId)) {
            is QuizRequestResult.Accepted -> ResponseEntity.status(HttpStatus.ACCEPTED).body(QuizRequestResponse(result.job.id))
            is QuizRequestResult.Existing -> ResponseEntity.ok(QuizExistsResponse(result.quizId))
        }

    @GetMapping("/api/v1/documents/{documentId}/quiz")
    fun get(
        @RequestAttribute(FirebaseAuthInterceptor.AUTH_UID_ATTRIBUTE) uid: String,
        @PathVariable documentId: String,
    ): QuizDetailResponse {
        val quiz = quizzes.get(uid, documentId)
        return QuizDetailResponse(
            quiz.id,
            documentId,
            quizzes.parseContent(quiz).questions.mapIndexed {
                index,
                question,
                ->
                QuizQuestionResponse(index, question.question, question.options)
            },
            quiz.createdAt,
        )
    }

    @PostMapping("/api/v1/quizzes/{quizId}/results")
    @ResponseStatus(HttpStatus.CREATED)
    fun submit(
        @RequestAttribute(FirebaseAuthInterceptor.AUTH_UID_ATTRIBUTE) uid: String,
        @PathVariable quizId: String,
        @Valid @RequestBody request: QuizResultRequest,
    ): QuizResultResponse {
        val submission = quizzes.submit(uid, quizId, request.answers.map { QuizAnswer(it.index, it.selected) })
        val items =
            submission.questions.questions.mapIndexed { index, question ->
                val selected = submission.answers[index].selected
                QuizResultItemResponse(
                    index,
                    question.question,
                    question.options,
                    selected,
                    question.answerIndex,
                    selected == question.answerIndex,
                    question.explanation,
                )
            }
        return QuizResultResponse(
            submission.result.id,
            BookScoreResponse(submission.result.correct, submission.result.total),
            items,
            submission.result.solvedAt,
        )
    }
}
