package com.boohs.booksummary.llm

object SummaryFixtures {
    val validJson =
        """
        {
          "title": "프로세스 상태 전이와 PCB",
          "keyPoints": [
            {"type":"definition","heading":"프로세스","detail":"실행 중인 프로그램이다."},
            {"type":"mechanism","heading":"상태 전이","detail":"실행과 대기 상태를 오간다."},
            {"type":"caution","heading":"PCB","detail":"상태 정보를 저장한다."}
          ],
          "terms": [{"term":"PCB","meaning":"프로세스 제어 블록"}]
        }
        """.trimIndent()
}
