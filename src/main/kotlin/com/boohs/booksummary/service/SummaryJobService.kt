package com.boohs.booksummary.service

import com.boohs.booksummary.common.BusinessException
import com.boohs.booksummary.common.ErrorCode
import com.boohs.booksummary.domain.Job
import com.boohs.booksummary.domain.ProcessingStatus
import com.boohs.booksummary.domain.Summary
import com.boohs.booksummary.domain.SummaryContent
import com.boohs.booksummary.llm.validator.SummaryValidator
import com.boohs.booksummary.repository.BookRepository
import com.boohs.booksummary.repository.JobRepository
import com.boohs.booksummary.repository.SummaryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@Service
@Transactional(readOnly = true)
class SummaryJobService(
    private val jobRepository: JobRepository,
    private val bookRepository: BookRepository,
    private val summaryRepository: SummaryRepository,
    private val validator: SummaryValidator,
) {
    fun get(
        uid: String,
        jobId: String,
    ): Job {
        val job = jobRepository.findById(jobId).orElseThrow { BusinessException(ErrorCode.NOT_FOUND) }
        if (job.ownerUid != uid) throw BusinessException(ErrorCode.FORBIDDEN)
        if (job.completedAt?.isBefore(Instant.now().minus(Duration.ofHours(24))) == true) {
            throw BusinessException(ErrorCode.NOT_FOUND)
        }
        return job
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun start(jobId: String): SummaryWork? {
        val job = lockedJob(jobId) ?: return null
        if (job.status != ProcessingStatus.PROCESSING || job.startedAt != null) return null
        job.start(Instant.now())
        return SummaryWork(job.id, job.document.extractedText)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun complete(
        jobId: String,
        summary: SummaryContent,
    ) {
        val job = lockedJob(jobId) ?: return
        if (job.status != ProcessingStatus.PROCESSING) return
        val now = Instant.now()
        summaryRepository.save(Summary(job.document.id, job.document, validator.serialize(summary)))
        job.document.complete(now)
        job.complete(now)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun fail(
        jobId: String,
        code: ErrorCode,
    ) {
        val job = lockedJob(jobId) ?: return
        if (job.status != ProcessingStatus.PROCESSING) return
        markFailed(job, code)
    }

    @Transactional
    fun recoverInterruptedJobs(startedBefore: Instant): Int {
        val ids =
            jobRepository
                .findAllByStatus(ProcessingStatus.PROCESSING)
                .filter { it.createdAt < startedBefore }
                .map { it.id }
        var recovered = 0
        for (id in ids) {
            val job = lockedJob(id) ?: continue
            if (job.status == ProcessingStatus.PROCESSING) {
                markFailed(job, ErrorCode.LLM_FAILED)
                recovered++
            }
        }
        return recovered
    }

    @Transactional
    fun deleteExpiredJobs(now: Instant): Long = jobRepository.deleteByCompletedAtBefore(now.minus(Duration.ofHours(24)))

    private fun lockedJob(jobId: String): Job? {
        val bookId = jobRepository.findBookIdById(jobId) ?: return null
        bookRepository.findLockedById(bookId) ?: return null
        return jobRepository.findById(jobId).orElse(null)
    }

    private fun markFailed(
        job: Job,
        code: ErrorCode,
    ) {
        val now = Instant.now()
        job.document.fail(now)
        job.fail(code, now)
    }
}

data class SummaryWork(
    val jobId: String,
    val text: String,
)
