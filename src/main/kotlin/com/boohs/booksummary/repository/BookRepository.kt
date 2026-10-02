package com.boohs.booksummary.repository

import com.boohs.booksummary.domain.Book
import org.springframework.data.jpa.repository.JpaRepository

interface BookRepository : JpaRepository<Book, String> {
    fun findAllByOwnerUidOrderByCreatedAtDescIdDesc(ownerUid: String): List<Book>
}
