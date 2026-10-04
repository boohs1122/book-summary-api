package com.boohs.booksummary.service

import com.boohs.booksummary.common.BusinessException
import com.boohs.booksummary.common.ErrorCode
import com.boohs.booksummary.domain.Job
import com.boohs.booksummary.domain.JobType
import com.boohs.booksummary.domain.ProcessingStatus
import com.boohs.booksummary.domain.Quiz
import com.boohs.booksummary.domain.QuizAnswer
import com.boohs.booksummary.domain.QuizContent
import com.boohs.booksummary.domain.QuizResult
import com.boohs.booksummary.llm.validator.QuizValidator
import com.boohs.booksummary.repository.DocumentRepository
import com.boohs.booksummary.repository.JobRepository
import com.boohs.booksummary.repository.QuizRepository
import com.boohs.booksummary.repository.QuizResultRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.time.Instant

@Service
@Transactional(readOnly = true)
class QuizService(
    private val documents: DocumentRepository,
    private val jobs: JobRepository,
    private val quizzes: QuizRepository,
    private val results: QuizResultRepository,
    private val books: BookService,
    private val validator: QuizValidator,
    private val mapper: JsonMapper,
    private val events: org.springframework.context.ApplicationEventPublisher,
) {
    @Transactional
    fun request(
        uid: String,
        documentId: String,
    ): QuizRequestResult {
        val bookId = documents.findBookIdById(documentId) ?: throw BusinessException(ErrorCode.NOT_FOUND)
        books.getForUpdate(uid, bookId)
        val document = documents.findById(documentId).orElseThrow { BusinessException(ErrorCode.NOT_FOUND) }
        quizzes.findByDocumentId(documentId)?.let { return QuizRequestResult.Existing(it.id) }
        if (jobs.existsByDocumentIdAndTypeAndStatus(documentId, JobType.QUIZ, ProcessingStatus.PROCESSING)) {
            throw BusinessException(ErrorCode.JOB_IN_PROGRESS)
        }
        if (jobs.existsByDocumentIdAndTypeAndStatus(documentId, JobType.SUMMARY, ProcessingStatus.PROCESSING)) {
            throw BusinessException(ErrorCode.JOB_IN_PROGRESS)
        }
        if (document.status != ProcessingStatus.DONE) throw BusinessException(ErrorCode.INVALID_REQUEST)
        val job = jobs.save(Job("job_${Ulid.generate()}", document, uid, Instant.now(), JobType.QUIZ))
        events.publishEvent(QuizRequested(job.id))
        return QuizRequestResult.Accepted(job)
    }

    fun get(
        uid: String,
        documentId: String,
    ): Quiz {
        val document = documents.findById(documentId).orElseThrow { BusinessException(ErrorCode.NOT_FOUND) }
        books.get(uid, document.book.id)
        return quizzes.findByDocumentId(documentId) ?: throw BusinessException(ErrorCode.NOT_FOUND)
    }

    @Transactional
    fun submit(
        uid: String,
        quizId: String,
        submitted: List<QuizAnswer>,
    ): QuizSubmission {
        val bookId = quizzes.findBookIdById(quizId) ?: throw BusinessException(ErrorCode.NOT_FOUND)
        books.getForUpdate(uid, bookId)
        val quiz = quizzes.findById(quizId).orElseThrow { BusinessException(ErrorCode.NOT_FOUND) }
        val questions = validator.parseStored(quiz.questionsJson).questions
        if (submitted.size != questions.size || submitted.map { it.index }.toSet().size != questions.size ||
            submitted.map { it.index }.sorted() != questions.indices.toList()
        ) {
            throw BusinessException(ErrorCode.INVALID_REQUEST)
        }
        if (submitted.any { it.selected != null && it.selected !in 0..3 }) throw BusinessException(ErrorCode.INVALID_REQUEST)
        val answers = submitted.sortedBy { it.index }
        val correct = questions.indices.count { index -> answers[index].selected == questions[index].answerIndex }
        val now = Instant.now()
        val result =
            results.save(
                QuizResult("res_${Ulid.generate()}", quiz, mapper.writeValueAsString(answers), correct, questions.size, now),
            )
        quiz.document.touch(now)
        return QuizSubmission(result, QuizContent(questions), answers)
    }

    fun parseContent(quiz: Quiz): QuizContent = validator.parseStored(quiz.questionsJson)
}

sealed interface QuizRequestResult {
    data class Accepted(
        val job: Job,
    ) : QuizRequestResult

    data class Existing(
        val quizId: String,
    ) : QuizRequestResult
}

data class QuizSubmission(
    val result: QuizResult,
    val questions: QuizContent,
    val answers: List<QuizAnswer>,
)

data class QuizRequested(
    val jobId: String,
)
