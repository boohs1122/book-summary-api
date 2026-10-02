package com.boohs.booksummary.service

import com.boohs.booksummary.common.BusinessException
import com.boohs.booksummary.common.ErrorCode
import com.boohs.booksummary.domain.Book
import com.boohs.booksummary.repository.BookRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional(readOnly = true)
class BookService(
    private val bookRepository: BookRepository,
) {
    fun list(uid: String): List<Book> = bookRepository.findAllByOwnerUidOrderByCreatedAtDescIdDesc(uid)

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
    fun delete(
        uid: String,
        bookId: String,
    ) {
        bookRepository.delete(get(uid, bookId))
    }
}
