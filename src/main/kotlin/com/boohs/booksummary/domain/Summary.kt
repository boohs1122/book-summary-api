package com.boohs.booksummary.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Lob
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction

@Entity
@Table(name = "summaries")
class Summary(
    @Id
    @Column(length = 30)
    val id: String,
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    val document: Document,
    @Lob
    @Column(nullable = false)
    val contentJson: String,
)

data class SummaryContent(
    val title: String,
    val keyPoints: List<SummaryKeyPoint>,
    val terms: List<SummaryTerm>,
)

data class SummaryKeyPoint(
    val type: String,
    val heading: String,
    val detail: String,
)

data class SummaryTerm(
    val term: String,
    val meaning: String,
)
