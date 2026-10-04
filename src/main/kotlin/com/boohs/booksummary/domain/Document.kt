package com.boohs.booksummary.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.time.Instant

@Entity
@Table(name = "documents", uniqueConstraints = [UniqueConstraint(columnNames = ["book_id", "sequence"])])
class Document(
    @Id
    @Column(length = 30)
    val id: String,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    val book: Book,
    @Column(nullable = false)
    val sequence: Int,
    @Column(nullable = false, length = 10000)
    val extractedText: String,
    @Column(nullable = false)
    val createdAt: Instant,
) {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: ProcessingStatus = ProcessingStatus.PROCESSING
        protected set

    @Column(nullable = false)
    var lastActivityAt: Instant = createdAt
        protected set

    val charCount: Int
        get() = extractedText.length

    fun startRetry(now: Instant) {
        check(status == ProcessingStatus.FAILED)
        status = ProcessingStatus.PROCESSING
        lastActivityAt = now
    }

    fun complete(now: Instant) {
        check(status == ProcessingStatus.PROCESSING)
        status = ProcessingStatus.DONE
        lastActivityAt = now
    }

    fun fail(now: Instant) {
        check(status == ProcessingStatus.PROCESSING)
        status = ProcessingStatus.FAILED
        lastActivityAt = now
    }

    fun touch(now: Instant) {
        lastActivityAt = now
    }
}
