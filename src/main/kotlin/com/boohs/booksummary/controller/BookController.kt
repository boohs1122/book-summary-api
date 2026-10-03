package com.boohs.booksummary.controller

import com.boohs.booksummary.config.FirebaseAuthInterceptor
import com.boohs.booksummary.dto.BookCreateRequest
import com.boohs.booksummary.dto.BookCreateResponse
import com.boohs.booksummary.dto.BookDetailResponse
import com.boohs.booksummary.dto.BookDocumentResponse
import com.boohs.booksummary.dto.BookItemResponse
import com.boohs.booksummary.dto.BookListResponse
import com.boohs.booksummary.service.BookService
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
@RequestMapping("/api/v1/books")
class BookController(
    private val bookService: BookService,
) {
    @GetMapping
    fun listBooks(
        @RequestAttribute(FirebaseAuthInterceptor.AUTH_UID_ATTRIBUTE) uid: String,
    ): BookListResponse =
        BookListResponse(
            items =
                bookService.list(uid).map { overview ->
                    BookItemResponse(
                        bookId = overview.book.id,
                        title = overview.book.title,
                        documentCount = overview.documentCount,
                        totalCharCount = overview.totalCharCount,
                        lastStudiedAt = overview.lastStudiedAt,
                        latestScore = null,
                        createdAt = overview.book.createdAt,
                    )
                },
            nextCursor = null,
        )

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createBook(
        @RequestAttribute(FirebaseAuthInterceptor.AUTH_UID_ATTRIBUTE) uid: String,
        @Valid @RequestBody request: BookCreateRequest,
    ): BookCreateResponse {
        val book = bookService.create(uid, request.title)
        return BookCreateResponse(book.id, book.title, book.createdAt)
    }

    @GetMapping("/{bookId}")
    fun getBook(
        @RequestAttribute(FirebaseAuthInterceptor.AUTH_UID_ATTRIBUTE) uid: String,
        @PathVariable bookId: String,
    ): BookDetailResponse {
        val detail = bookService.detail(uid, bookId)
        return BookDetailResponse(
            bookId = detail.book.id,
            title = detail.book.title,
            documentCount = detail.documentCount,
            totalCharCount = detail.totalCharCount,
            documents =
                detail.documents.map { overview ->
                    BookDocumentResponse(
                        documentId = overview.document.id,
                        sequence = overview.document.sequence,
                        status = overview.document.status.name,
                        title = overview.summaryTitle,
                        preview = overview.preview,
                        charCount = overview.document.charCount,
                        hasQuiz = false,
                        latestScore = null,
                        createdAt = overview.document.createdAt,
                    )
                },
        )
    }

    @DeleteMapping("/{bookId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteBook(
        @RequestAttribute(FirebaseAuthInterceptor.AUTH_UID_ATTRIBUTE) uid: String,
        @PathVariable bookId: String,
    ) = bookService.delete(uid, bookId)
}
