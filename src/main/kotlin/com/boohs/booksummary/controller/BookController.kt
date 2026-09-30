package com.boohs.booksummary.controller

import com.boohs.booksummary.config.FirebaseAuthInterceptor
import com.boohs.booksummary.dto.BookListResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/books")
class BookController {
    @GetMapping
    fun listBooks(
        @RequestAttribute(FirebaseAuthInterceptor.AUTH_UID_ATTRIBUTE) uid: String,
    ): BookListResponse = BookListResponse(items = emptyList(), nextCursor = null)
}
