package com.boohs.booksummary.repository

import com.boohs.booksummary.domain.Book
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface BookRepository : JpaRepository<Book, String> {
    fun findAllByOwnerUidOrderByCreatedAtDescIdDesc(ownerUid: String): List<Book>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Book b where b.id = :bookId")
    fun findLockedById(bookId: String): Book?
}
