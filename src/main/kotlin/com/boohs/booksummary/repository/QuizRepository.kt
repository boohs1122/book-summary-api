package com.boohs.booksummary.repository

import com.boohs.booksummary.domain.Quiz
import com.boohs.booksummary.domain.QuizResult
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface QuizRepository : JpaRepository<Quiz, String> {
    @Query("select q.document.book.id from Quiz q where q.id = :quizId")
    fun findBookIdById(quizId: String): String?

    fun findByDocumentId(documentId: String): Quiz?

    fun findAllByDocumentIdIn(documentIds: Collection<String>): List<Quiz>
}

interface QuizResultRepository : JpaRepository<QuizResult, String> {
    fun findFirstByQuizIdOrderBySolvedAtDescIdDesc(quizId: String): QuizResult?

    fun findFirstByQuizDocumentBookIdOrderBySolvedAtDescIdDesc(bookId: String): QuizResult?
}
