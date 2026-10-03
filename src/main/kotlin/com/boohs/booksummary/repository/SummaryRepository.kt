package com.boohs.booksummary.repository

import com.boohs.booksummary.domain.Summary
import org.springframework.data.jpa.repository.JpaRepository

interface SummaryRepository : JpaRepository<Summary, String> {
    fun findByDocumentId(documentId: String): Summary?

    fun findAllByDocumentIdIn(documentIds: Collection<String>): List<Summary>
}
