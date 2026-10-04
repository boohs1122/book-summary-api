package com.boohs.booksummary.service

import com.boohs.booksummary.common.BusinessException
import com.boohs.booksummary.common.ErrorCode
import com.boohs.booksummary.domain.Book
import com.boohs.booksummary.domain.BookDetails
import com.boohs.booksummary.domain.BookOverview
import com.boohs.booksummary.domain.DocumentOverview
import com.boohs.booksummary.llm.validator.SummaryValidator
import com.boohs.booksummary.repository.BookRepository
import com.boohs.booksummary.repository.DocumentRepository
import com.boohs.booksummary.repository.QuizRepository
import com.boohs.booksummary.repository.QuizResultRepository
import com.boohs.booksummary.repository.SummaryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional(readOnly = true)
class BookService(
    private val bookRepository: BookRepository,
    private val documentRepository: DocumentRepository,
    private val summaryRepository: SummaryRepository,
    private val summaryValidator: SummaryValidator,
    private val quizRepository: QuizRepository,
    private val quizResultRepository: QuizResultRepository,
) {
    fun list(uid: String): List<BookOverview> {
        val books = bookRepository.findAllByOwnerUidOrderByCreatedAtDescIdDesc(uid)
        if (books.isEmpty()) return emptyList()
        val documentsByBook = documentRepository.findAllByBookIdIn(books.map { it.id }).groupBy { it.book.id }
        return books
            .map { book ->
                val documents = documentsByBook[book.id].orEmpty()
                BookOverview(
                    book,
                    documents.size,
                    documents.sumOf { it.charCount },
                    documents.maxOfOrNull { it.lastActivityAt },
                    quizResultRepository.findFirstByQuizDocumentBookIdOrderBySolvedAtDescIdDesc(book.id)?.let {
                        it.correct to
                            it.total
                    },
                )
            }.sortedWith(
                compareByDescending<BookOverview> { it.lastStudiedAt }
                    .thenByDescending { it.book.createdAt }
                    .thenByDescending { it.book.id },
            )
    }

    fun detail(
        uid: String,
        bookId: String,
    ): BookDetails {
        val book = get(uid, bookId)
        val documents = documentRepository.findAllByBookIdOrderBySequenceAsc(bookId)
        val titles =
            if (documents.isEmpty()) {
                emptyMap()
            } else {
                summaryRepository
                    .findAllByDocumentIdIn(documents.map { it.id })
                    .associate { it.document.id to summaryValidator.parse(it.contentJson).title }
            }
        val quizByDocument =
            if (documents.isEmpty()) {
                emptyMap()
            } else {
                quizRepository
                    .findAllByDocumentIdIn(
                        documents.map {
                            it.id
                        },
                    ).associateBy { it.document.id }
            }
        return BookDetails(
            book,
            documents.map { document ->
                val quiz = quizByDocument[document.id]
                val score =
                    quiz?.let {
                        quizResultRepository.findFirstByQuizIdOrderBySolvedAtDescIdDesc(it.id)?.let { result ->
                            result.correct to
                                result.total
                        }
                    }
                DocumentOverview(document, titles[document.id], quiz?.id, score)
            },
        )
    }

    @Transactional
    fun create(
        uid: String,
        title: String,
    ): Book =
        bookRepository.save(
            Book(
                id = "bok_${Ulid.generate()}",
                ownerUid = uid,
                title = title.trim(),
                createdAt = Instant.now(),
            ),
        )

    fun get(
        uid: String,
        bookId: String,
    ): Book {
        val book = bookRepository.findById(bookId).orElseThrow { BusinessException(ErrorCode.NOT_FOUND) }
        if (book.ownerUid != uid) throw BusinessException(ErrorCode.FORBIDDEN)
        return book
    }

    @Transactional
    fun updateTitle(
        uid: String,
        bookId: String,
        title: String,
    ): Book {
        val normalizedTitle = title.trim()
        if (normalizedTitle.length !in 1..100) throw BusinessException(ErrorCode.INVALID_REQUEST)
        val book = getForUpdate(uid, bookId)
        book.rename(normalizedTitle)
        return book
    }

    @Transactional
    fun delete(
        uid: String,
        bookId: String,
    ) {
        bookRepository.delete(getForUpdate(uid, bookId))
    }

    @Transactional
    fun getForUpdate(
        uid: String,
        bookId: String,
    ): Book {
        val book = bookRepository.findLockedById(bookId) ?: throw BusinessException(ErrorCode.NOT_FOUND)
        if (book.ownerUid != uid) throw BusinessException(ErrorCode.FORBIDDEN)
        return book
    }
}
