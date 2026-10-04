package com.boohs.booksummary.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "books")
class Book(
    @Id
    @Column(length = 30)
    val id: String,
    @Column(nullable = false, length = 128)
    val ownerUid: String,
    title: String,
    @Column(nullable = false)
    val createdAt: Instant,
) {
    @Column(nullable = false, length = 100)
    var title: String = title
        protected set

    fun rename(title: String) {
        require(title.length in 1..100 && title == title.trim())
        this.title = title
    }
}
