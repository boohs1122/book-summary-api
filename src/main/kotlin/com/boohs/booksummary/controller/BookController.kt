package com.boohs.booksummary.controller

import com.boohs.booksummary.config.FirebaseAuthInterceptor
import com.boohs.booksummary.dto.BookCreateRequest
import com.boohs.booksummary.dto.BookCreateResponse
import com.boohs.booksummary.dto.BookDetailResponse
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
                bookService.list(uid).map { book ->
                    BookItemResponse(
                        bookId = book.id,
                        title = book.title,
                        documentCount = 0,
                        totalCharCount = 0,
                        lastStudiedAt = null,
                        latestScore = null,
                        createdAt = book.createdAt,
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
        val book = bookService.get(uid, bookId)
        return BookDetailResponse(book.id, book.title, 0, 0, emptyList())
    }

    @DeleteMapping("/{bookId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteBook(
        @RequestAttribute(FirebaseAuthInterceptor.AUTH_UID_ATTRIBUTE) uid: String,
        @PathVariable bookId: String,
    ) = bookService.delete(uid, bookId)
}
