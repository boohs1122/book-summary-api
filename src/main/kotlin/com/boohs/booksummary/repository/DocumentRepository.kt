package com.boohs.booksummary.repository

import com.boohs.booksummary.domain.Document
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface DocumentRepository : JpaRepository<Document, String> {
    fun findAllByBookIdOrderBySequenceAsc(bookId: String): List<Document>

    fun findAllByBookIdIn(bookIds: Collection<String>): List<Document>

    @Query("select coalesce(max(d.sequence), 0) + 1 from Document d where d.book.id = :bookId")
    fun nextSequence(bookId: String): Int

    @Query("select d.book.id from Document d where d.id = :documentId")
    fun findBookIdById(documentId: String): String?
}
