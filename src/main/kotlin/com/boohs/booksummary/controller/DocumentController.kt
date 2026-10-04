package com.boohs.booksummary.controller

import com.boohs.booksummary.config.FirebaseAuthInterceptor
import com.boohs.booksummary.domain.ProcessingStatus
import com.boohs.booksummary.dto.BookScoreResponse
import com.boohs.booksummary.dto.DocumentCreateRequest
import com.boohs.booksummary.dto.DocumentDetailResponse
import com.boohs.booksummary.dto.DocumentQuizResponse
import com.boohs.booksummary.dto.JobAcceptedResponse
import com.boohs.booksummary.dto.SummaryKeyPointResponse
import com.boohs.booksummary.dto.SummaryResponse
import com.boohs.booksummary.dto.SummaryTermResponse
import com.boohs.booksummary.service.DocumentService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/documents")
class DocumentController(
    private val documents: DocumentService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun register(
        @RequestAttribute(FirebaseAuthInterceptor.AUTH_UID_ATTRIBUTE) uid: String,
        @Valid @RequestBody request: DocumentCreateRequest,
    ): JobAcceptedResponse = JobAcceptedResponse(documents.register(uid, request.bookId, request.text).id)

    @GetMapping("/{documentId}")
    fun get(
        @RequestAttribute(FirebaseAuthInterceptor.AUTH_UID_ATTRIBUTE) uid: String,
        @PathVariable documentId: String,
    ): DocumentDetailResponse {
        val detail = documents.get(uid, documentId)
        return DocumentDetailResponse(
            documentId = detail.document.id,
            bookId = detail.book.id,
            bookTitle = detail.book.title,
            sequence = detail.document.sequence,
            status = detail.document.status.name,
            charCount = detail.document.charCount,
            extractedText = detail.document.extractedText,
            summary =
                detail.summary?.let { summary ->
                    SummaryResponse(
                        title = summary.title,
                        keyPoints = summary.keyPoints.map { SummaryKeyPointResponse(it.type, it.heading, it.detail) },
                        terms = summary.terms.map { SummaryTermResponse(it.term, it.meaning) },
                    )
                },
            quiz =
                if (detail.document.status == ProcessingStatus.DONE) {
                    DocumentQuizResponse(
                        exists = detail.quizId != null,
                        quizId = detail.quizId,
                        questionCount = detail.quizQuestionCount,
                        latestScore =
                            detail.latestScore?.let { (correct, total) ->
                                BookScoreResponse(correct, total)
                            },
                    )
                } else {
                    null
                },
            createdAt = detail.document.createdAt,
        )
    }

    @PostMapping("/{documentId}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun retry(
        @RequestAttribute(FirebaseAuthInterceptor.AUTH_UID_ATTRIBUTE) uid: String,
        @PathVariable documentId: String,
    ): JobAcceptedResponse = JobAcceptedResponse(documents.retry(uid, documentId).id)

    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @RequestAttribute(FirebaseAuthInterceptor.AUTH_UID_ATTRIBUTE) uid: String,
        @PathVariable documentId: String,
    ) = documents.delete(uid, documentId)
}
