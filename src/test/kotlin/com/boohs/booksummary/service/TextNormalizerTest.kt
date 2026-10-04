package com.boohs.booksummary.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TextNormalizerTest {
    @Test
    fun `줄바꿈 종류와 연속 공백을 정리하고 문단은 남긴다`() {
        val input = "  프로세스\t  상태\r\n  실행  \r\n\r\n\r\n 대기\r 종료  "

        assertEquals("프로세스 상태\n실행\n\n대기\n종료", TextNormalizer.normalize(input))
    }

    @Test
    fun `영문 줄끝 분철만 결합하고 일반 하이픈은 유지한다`() {
        val input = "oper- \n ating system\nstate-of-the-art\n항목-\n설명\n2-\n3"

        assertEquals("operating system\nstate-of-the-art\n항목-\n설명\n2-\n3", TextNormalizer.normalize(input))
    }
}
