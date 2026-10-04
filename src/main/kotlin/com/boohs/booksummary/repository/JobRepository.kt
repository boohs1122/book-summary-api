package com.boohs.booksummary.repository

import com.boohs.booksummary.domain.Job
import com.boohs.booksummary.domain.JobType
import com.boohs.booksummary.domain.ProcessingStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface JobRepository : JpaRepository<Job, String> {
    fun findAllByStatus(status: ProcessingStatus): List<Job>

    fun deleteByCompletedAtBefore(cutoff: Instant): Long

    fun existsByDocumentIdAndTypeAndStatus(
        documentId: String,
        type: JobType,
        status: ProcessingStatus,
    ): Boolean

    @Query("select j.document.book.id from Job j where j.id = :jobId")
    fun findBookIdById(jobId: String): String?
}
