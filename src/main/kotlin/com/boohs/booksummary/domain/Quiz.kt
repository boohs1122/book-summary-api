package com.boohs.booksummary.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Lob
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.time.Instant

@Entity
@Table(name = "quizzes", uniqueConstraints = [UniqueConstraint(columnNames = ["document_id"])])
class Quiz(
    @Id
    @Column(length = 30)
    val id: String,
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    val document: Document,
    @Lob
    @Column(nullable = false)
    val questionsJson: String,
    @Column(nullable = false)
    val createdAt: Instant,
)

@Entity
@Table(name = "quiz_results")
class QuizResult(
    @Id
    @Column(length = 30)
    val id: String,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    val quiz: Quiz,
    @Lob
    @Column(nullable = false)
    val answersJson: String,
    @Column(nullable = false)
    val correct: Int,
    @Column(nullable = false)
    val total: Int,
    @Column(nullable = false)
    val solvedAt: Instant,
)

data class QuizContent(
    val questions: List<QuizQuestion>,
)

data class QuizQuestion(
    val question: String,
    val options: List<String>,
    val answerIndex: Int,
    val explanation: String,
)

data class QuizAnswer(
    val index: Int,
    val selected: Int?,
)
