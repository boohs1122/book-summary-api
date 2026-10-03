package com.boohs.booksummary.service

import com.boohs.booksummary.common.BusinessException
import com.boohs.booksummary.common.ErrorCode
import com.boohs.booksummary.domain.Document
import com.boohs.booksummary.domain.DocumentDetails
import com.boohs.booksummary.domain.Job
import com.boohs.booksummary.domain.ProcessingStatus
import com.boohs.booksummary.llm.validator.SummaryValidator
import com.boohs.booksummary.repository.DocumentRepository
import com.boohs.booksummary.repository.JobRepository
import com.boohs.booksummary.repository.SummaryRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional(readOnly = true)
class DocumentService(
    private val bookService: BookService,
    private val documentRepository: DocumentRepository,
    private val jobRepository: JobRepository,
    private val summaryRepository: SummaryRepository,
    private val summaryValidator: SummaryValidator,
    private val events: ApplicationEventPublisher,
) {
    @Transactional
    fun register(
        uid: String,
        bookId: String,
        text: String,
    ): Job {
        if (text.length > MAX_TEXT_LENGTH) throw BusinessException(ErrorCode.INVALID_REQUEST)
        val normalized = TextNormalizer.normalize(text)
        if (normalized.length < MIN_TEXT_LENGTH) throw BusinessException(ErrorCode.TEXT_TOO_SHORT)
        val book = bookService.getForUpdate(uid, bookId)
        val document =
            documentRepository.save(
                Document(
                    id = "doc_${Ulid.generate()}",
                    book = book,
                    sequence = documentRepository.nextSequence(bookId),
                    extractedText = normalized,
                    createdAt = Instant.now(),
                ),
            )
        return requestSummary(uid, document)
    }

    fun get(
        uid: String,
        documentId: String,
    ): DocumentDetails {
        val document = documentRepository.findById(documentId).orElseThrow { BusinessException(ErrorCode.NOT_FOUND) }
        val book = bookService.get(uid, document.book.id)
        val summary =
            if (document.status == ProcessingStatus.DONE) {
                summaryRepository.findByDocumentId(documentId)?.let { summaryValidator.parse(it.contentJson) }
            } else {
                null
            }
        return DocumentDetails(document, book, summary)
    }

    @Transactional
    fun retry(
        uid: String,
        documentId: String,
    ): Job {
        val document = ownedLockedDocument(uid, documentId)
        when (document.status) {
            ProcessingStatus.PROCESSING -> throw BusinessException(ErrorCode.JOB_IN_PROGRESS)
            ProcessingStatus.DONE -> throw BusinessException(ErrorCode.INVALID_REQUEST)
            ProcessingStatus.FAILED -> document.startRetry(Instant.now())
        }
        return requestSummary(uid, document)
    }

    @Transactional
    fun delete(
        uid: String,
        documentId: String,
    ) {
        documentRepository.delete(ownedLockedDocument(uid, documentId))
    }

    private fun ownedLockedDocument(
        uid: String,
        documentId: String,
    ): Document {
        val bookId = documentRepository.findBookIdById(documentId) ?: throw BusinessException(ErrorCode.NOT_FOUND)
        bookService.getForUpdate(uid, bookId)
        return documentRepository.findById(documentId).orElseThrow { BusinessException(ErrorCode.NOT_FOUND) }
    }

    private fun requestSummary(
        uid: String,
        document: Document,
    ): Job {
        val job = jobRepository.save(Job("job_${Ulid.generate()}", document, uid, Instant.now()))
        events.publishEvent(SummaryRequested(job.id))
        return job
    }

    companion object {
        const val MIN_TEXT_LENGTH = 100
        const val MAX_TEXT_LENGTH = 10000
    }
}

data class SummaryRequested(
    val jobId: String,
)
