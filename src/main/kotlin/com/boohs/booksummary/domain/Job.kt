package com.boohs.booksummary.domain

import com.boohs.booksummary.common.ErrorCode
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.time.Instant

@Entity
@Table(name = "jobs")
class Job(
    @Id
    @Column(length = 30)
    val id: String,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    val document: Document,
    @Column(nullable = false, length = 128)
    val ownerUid: String,
    @Column(nullable = false)
    val createdAt: Instant,
    type: JobType = JobType.SUMMARY,
) {
    @Column(length = 30)
    var quizId: String? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    val type: JobType = type

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: ProcessingStatus = ProcessingStatus.PROCESSING
        protected set

    var startedAt: Instant? = null
        protected set

    var completedAt: Instant? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    var errorCode: ErrorCode? = null
        protected set

    fun start(now: Instant) {
        check(status == ProcessingStatus.PROCESSING && startedAt == null)
        startedAt = now
    }

    fun complete(
        now: Instant,
        quizId: String? = null,
    ) {
        check(status == ProcessingStatus.PROCESSING)
        status = ProcessingStatus.DONE
        completedAt = now
        this.quizId = quizId
    }

    fun fail(
        code: ErrorCode,
        now: Instant,
    ) {
        check(status == ProcessingStatus.PROCESSING)
        status = ProcessingStatus.FAILED
        errorCode = code
        completedAt = now
    }
}

enum class JobType {
    SUMMARY,
    QUIZ,
}
