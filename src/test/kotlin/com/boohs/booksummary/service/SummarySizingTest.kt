package com.boohs.booksummary.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SummarySizingTest {
    @Test
    fun `분량 구간 경계에서 목표 핵심 항목 수를 결정한다`() {
        mapOf(100 to 3, 1500 to 3, 1501 to 4, 4000 to 4, 4001 to 5, 7000 to 5, 7001 to 6, 10000 to 6)
            .forEach { (charCount, expected) -> assertEquals(expected, SummarySizing.keyPointCount(charCount)) }
    }
}
