package com.boohs.booksummary.controller

import com.boohs.booksummary.common.BusinessException
import com.boohs.booksummary.common.ErrorCode
import com.boohs.booksummary.config.FirebaseTokenVerifier
import com.boohs.booksummary.domain.Job
import com.boohs.booksummary.domain.ProcessingStatus
import com.boohs.booksummary.llm.LlmClient
import com.boohs.booksummary.llm.SummaryFixtures
import com.boohs.booksummary.repository.BookRepository
import com.boohs.booksummary.repository.DocumentRepository
import com.boohs.booksummary.repository.JobRepository
import com.boohs.booksummary.repository.SummaryRepository
import com.boohs.booksummary.service.BookService
import com.boohs.booksummary.service.DocumentService
import com.boohs.booksummary.service.SummaryJobService
import com.jayway.jsonpath.JsonPath
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest(properties = ["spring.datasource.url=jdbc:h2:mem:document-controller-test;DB_CLOSE_DELAY=-1"])
@AutoConfigureMockMvc
class DocumentControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val books: BookService,
    @Autowired private val documents: DocumentService,
    @Autowired private val jobs: SummaryJobService,
    @Autowired private val bookRepository: BookRepository,
    @Autowired private val documentRepository: DocumentRepository,
    @Autowired private val summaryRepository: SummaryRepository,
    @Autowired private val jobRepository: JobRepository,
    @Autowired private val mapper: JsonMapper,
    @Autowired @Qualifier("summaryExecutor") private val executor: ThreadPoolTaskExecutor,
) {
    @MockitoBean
    private lateinit var tokenVerifier: FirebaseTokenVerifier

    @MockitoBean
    private lateinit var llmClient: LlmClient

    @BeforeEach
    fun setup() {
        awaitIdle()
        bookRepository.deleteAll()
        `when`(tokenVerifier.verify("owner-token")).thenReturn("owner")
        `when`(tokenVerifier.verify("other-token")).thenReturn("other")
    }

    @AfterEach
    fun finish() {
        awaitIdle()
    }

    @Test
    fun `커밋된 원문을 비동기로 요약하고 작업과 회차 및 책 집계에 반영한다`() {
        val bookId = books.create("owner", "운영체제 교재").id
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        blockGeneration(started, release)
        try {
            val jobId = register(bookId, "  " + TEXT + "  ")
            assertTrue(started.await(5, TimeUnit.SECONDS), "커밋 후 LLM 호출이 시작되어야 한다")
            val documentId = jobs.get("owner", jobId).document.id

            mockMvc.get("/api/v1/jobs/$jobId") { auth() }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("PROCESSING") }
                jsonPath("$.documentId") { doesNotExist() }
            }
            mockMvc.get("/api/v1/documents/$documentId") { auth() }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("PROCESSING") }
                jsonPath("$.extractedText") { value(TEXT) }
                jsonPath("$.summary") { value(null) }
                jsonPath("$.quiz") { value(null) }
            }
            mockMvc.get("/api/v1/books/$bookId") { auth() }.andExpect {
                jsonPath("$.documents[0].preview") { value(TEXT.take(40)) }
                jsonPath("$.documents[0].title") { value(null) }
            }

            release.countDown()
            awaitJob(jobId, ProcessingStatus.DONE)

            mockMvc.get("/api/v1/jobs/$jobId") { auth() }.andExpect {
                jsonPath("$.type") { value("SUMMARY") }
                jsonPath("$.documentId") { value(documentId) }
                jsonPath("$.completedAt") { exists() }
                jsonPath("$.error") { doesNotExist() }
            }
            mockMvc.get("/api/v1/documents/$documentId") { auth() }.andExpect {
                jsonPath("$.bookTitle") { value("운영체제 교재") }
                jsonPath("$.summary.title") { value("프로세스 상태 전이와 PCB") }
                jsonPath("$.summary.keyPoints.length()") { value(3) }
                jsonPath("$.quiz.exists") { value(false) }
                jsonPath("$.quiz.quizId") { value(null) }
            }
            mockMvc.get("/api/v1/books/$bookId") { auth() }.andExpect {
                jsonPath("$.documentCount") { value(1) }
                jsonPath("$.totalCharCount") { value(TEXT.length) }
                jsonPath("$.documents[0].sequence") { value(1) }
                jsonPath("$.documents[0].title") { value("프로세스 상태 전이와 PCB") }
                jsonPath("$.documents[0].preview") { value(null) }
            }
            mockMvc.get("/api/v1/books") { auth() }.andExpect {
                jsonPath("$.items[0].documentCount") { value(1) }
                jsonPath("$.items[0].totalCharCount") { value(TEXT.length) }
                jsonPath("$.items[0].lastStudiedAt") { exists() }
            }
        } finally {
            release.countDown()
        }
    }

    @Test
    fun `네트워크 실패 원문을 보존하고 같은 회차를 재시도하며 중복 요청을 막는다`() {
        `when`(llmClient.generateSummary(anyString(), anyInt())).thenThrow(BusinessException(ErrorCode.LLM_FAILED))
        val bookId = books.create("owner", "책").id
        val firstJobId = register(bookId)
        awaitJob(firstJobId, ProcessingStatus.FAILED)
        val documentId = jobs.get("owner", firstJobId).document.id

        mockMvc.get("/api/v1/jobs/$firstJobId") { auth() }.andExpect {
            jsonPath("$.error.code") { value("LLM_FAILED") }
            jsonPath("$.documentId") { value(documentId) }
        }
        mockMvc.get("/api/v1/documents/$documentId") { auth() }.andExpect {
            jsonPath("$.status") { value("FAILED") }
            jsonPath("$.extractedText") { value(TEXT) }
            jsonPath("$.summary") { value(null) }
        }

        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        blockGeneration(started, release)
        try {
            val retry =
                mockMvc
                    .post("/api/v1/documents/$documentId/retry") { auth() }
                    .andExpect {
                        status { isAccepted() }
                    }.andReturn()
            val retryJobId = JsonPath.read<String>(retry.response.contentAsString, "$.jobId")
            assertTrue(started.await(5, TimeUnit.SECONDS))

            mockMvc.post("/api/v1/documents/$documentId/retry") { auth() }.andExpect {
                status { isConflict() }
                jsonPath("$.error.code") { value("JOB_IN_PROGRESS") }
            }
            release.countDown()
            awaitJob(retryJobId, ProcessingStatus.DONE)
            assertEquals(documentId, jobs.get("owner", retryJobId).document.id)
            assertEquals(ProcessingStatus.FAILED, jobs.get("owner", firstJobId).status)
            assertEquals(1, books.detail("owner", bookId).documentCount)
            verify(llmClient, times(2)).generateSummary(TEXT, 3)

            mockMvc.post("/api/v1/documents/$documentId/retry") { auth() }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("INVALID_REQUEST") }
            }
        } finally {
            release.countDown()
        }
    }

    @Test
    fun `잘못된 LLM 응답을 한 번 재요청하고 재차 실패하면 저장하지 않는다`() {
        `when`(llmClient.generateSummary(anyString(), anyInt())).thenReturn("{}")
        val jobId = register(books.create("owner", "책").id)

        awaitJob(jobId, ProcessingStatus.FAILED)
        assertEquals(ErrorCode.LLM_INVALID_RESPONSE, jobs.get("owner", jobId).errorCode)
        assertEquals(0, summaryRepository.count())
        verify(llmClient, times(2)).generateSummary(TEXT, 3)
    }

    @Test
    fun `검증 재요청이 성공하면 검증된 요약만 저장한다`() {
        `when`(llmClient.generateSummary(anyString(), anyInt())).thenReturn("{}", SummaryFixtures.validJson)
        val jobId = register(books.create("owner", "책").id)

        awaitJob(jobId, ProcessingStatus.DONE)
        assertEquals(1, summaryRepository.count())
        verify(llmClient, times(2)).generateSummary(TEXT, 3)
    }

    @Test
    fun `다른 사용자의 회차와 작업은 조회나 재시도 및 삭제할 수 없다`() {
        `when`(llmClient.generateSummary(anyString(), anyInt())).thenThrow(BusinessException(ErrorCode.LLM_FAILED))
        val bookId = books.create("owner", "책").id
        val jobId = register(bookId)
        awaitJob(jobId, ProcessingStatus.FAILED)
        val documentId = jobs.get("owner", jobId).document.id

        mockMvc.get("/api/v1/jobs/$jobId") { auth("other-token") }.andExpect {
            status { isForbidden() }
        }
        mockMvc.get("/api/v1/documents/$documentId") { auth("other-token") }.andExpect {
            status { isForbidden() }
        }
        mockMvc.post("/api/v1/documents/$documentId/retry") { auth("other-token") }.andExpect {
            status { isForbidden() }
        }
        mockMvc.delete("/api/v1/documents/$documentId") { auth("other-token") }.andExpect {
            status { isForbidden() }
        }
        mockMvc
            .post("/api/v1/documents") {
                auth("other-token")
                contentType = MediaType.APPLICATION_JSON
                content = mapper.writeValueAsString(mapOf("bookId" to bookId, "text" to TEXT))
            }.andExpect { status { isForbidden() } }
        assertEquals(1, documentRepository.count())
    }

    @Test
    fun `책 삭제 이후 도착한 요약은 저장하지 않고 모든 하위 데이터를 삭제한다`() {
        val bookId = books.create("owner", "책").id
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        blockGeneration(started, release)
        try {
            register(bookId)
            assertTrue(started.await(5, TimeUnit.SECONDS))
            mockMvc.delete("/api/v1/books/$bookId") { auth() }.andExpect { status { isNoContent() } }
            release.countDown()
            awaitIdle()

            assertEquals(0, documentRepository.count())
            assertEquals(0, jobRepository.count())
            assertEquals(0, summaryRepository.count())
        } finally {
            release.countDown()
        }
    }

    @Test
    fun `회차 삭제 시 요약과 작업을 삭제하고 남은 순번과 새 순번이 중복되지 않는다`() {
        `when`(llmClient.generateSummary(anyString(), anyInt())).thenReturn(SummaryFixtures.validJson)
        val bookId = books.create("owner", "책").id
        val jobIds = (1..3).map { register(bookId) }
        jobIds.forEach { awaitJob(it, ProcessingStatus.DONE) }
        val secondDocumentId = jobs.get("owner", jobIds[1]).document.id

        mockMvc.delete("/api/v1/documents/$secondDocumentId") { auth() }.andExpect { status { isNoContent() } }
        assertEquals(2, summaryRepository.count())
        assertEquals(2, jobRepository.count())
        val nextJobId = register(bookId)
        awaitJob(nextJobId, ProcessingStatus.DONE)
        assertEquals(listOf(1, 3, 4), books.detail("owner", bookId).documents.map { it.document.sequence })

        mockMvc.delete("/api/v1/books/$bookId") { auth() }.andExpect { status { isNoContent() } }
        assertEquals(0, documentRepository.count())
        assertEquals(0, summaryRepository.count())
        assertEquals(0, jobRepository.count())
    }

    @Test
    fun `정제 후 100자 미만과 원문 10000자 초과 요청은 LLM 호출 없이 거부한다`() {
        val bookId = books.create("owner", "책").id
        listOf("a".repeat(99), " ".repeat(200)).forEach { text ->
            mockMvc
                .post("/api/v1/documents") {
                    auth()
                    contentType = MediaType.APPLICATION_JSON
                    content = mapper.writeValueAsString(mapOf("bookId" to bookId, "text" to text))
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value("TEXT_TOO_SHORT") }
                }
        }
        mockMvc
            .post("/api/v1/documents") {
                auth()
                contentType = MediaType.APPLICATION_JSON
                content = mapper.writeValueAsString(mapOf("bookId" to bookId, "text" to "a".repeat(10001)))
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("INVALID_REQUEST") }
            }
        assertEquals(0, documentRepository.count())
        verify(llmClient, times(0)).generateSummary(anyString(), anyInt())
    }

    @Test
    fun `중단된 작업을 실패로 복구하고 늦은 결과로 상태를 덮어쓰지 않는다`() {
        val bookId = books.create("owner", "책").id
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        blockGeneration(started, release)
        try {
            val jobId = register(bookId)
            assertTrue(started.await(5, TimeUnit.SECONDS))
            assertEquals(1, jobs.recoverInterruptedJobs(Instant.now().plusSeconds(1)))
            release.countDown()
            awaitIdle()

            assertEquals(ProcessingStatus.FAILED, jobs.get("owner", jobId).status)
            val detail = documents.get("owner", jobs.get("owner", jobId).document.id)
            assertEquals(TEXT, detail.document.extractedText)
            assertEquals(ProcessingStatus.FAILED, detail.document.status)
            assertEquals(0, summaryRepository.count())
        } finally {
            release.countDown()
        }
    }

    @Test
    fun `동시에 등록한 회차에도 서로 다른 순번을 부여한다`() {
        `when`(llmClient.generateSummary(anyString(), anyInt())).thenReturn(SummaryFixtures.validJson)
        val bookId = books.create("owner", "책").id
        Executors.newFixedThreadPool(2).use { pool ->
            val pending = (1..2).map { pool.submit<String> { documents.register("owner", bookId, TEXT).id } }
            pending.map { it.get(5, TimeUnit.SECONDS) }.forEach { awaitJob(it, ProcessingStatus.DONE) }
        }
        assertEquals(listOf(1, 2), books.detail("owner", bookId).documents.map { it.document.sequence })
    }

    @Test
    fun `24시간 지난 완료 작업은 조회와 정리 대상에서 제외하고 원문 및 요약을 보존한다`() {
        `when`(llmClient.generateSummary(anyString(), anyInt())).thenReturn(SummaryFixtures.validJson)
        val jobId = register(books.create("owner", "책").id)
        awaitJob(jobId, ProcessingStatus.DONE)
        val document = documentRepository.findById(jobs.get("owner", jobId).document.id).orElseThrow()
        val yesterday = Instant.now().minus(Duration.ofHours(25))
        val expired = Job("job_expired", document, "owner", yesterday).apply { fail(ErrorCode.LLM_FAILED, yesterday) }
        jobRepository.save(expired)

        val exception = assertThrows(BusinessException::class.java) { jobs.get("owner", expired.id) }
        assertEquals(ErrorCode.NOT_FOUND, exception.errorCode)
        assertEquals(1, jobs.deleteExpiredJobs(Instant.now()))
        assertEquals(1, jobRepository.count())
        assertEquals(1, documentRepository.count())
        assertEquals(1, summaryRepository.count())
    }

    private fun register(
        bookId: String,
        text: String = TEXT,
    ): String {
        val result =
            mockMvc
                .post("/api/v1/documents") {
                    auth()
                    contentType = MediaType.APPLICATION_JSON
                    content = mapper.writeValueAsString(mapOf("bookId" to bookId, "text" to text))
                }.andExpect {
                    status { isAccepted() }
                    jsonPath("$.status") { value("PROCESSING") }
                }.andReturn()
        return JsonPath.read(result.response.contentAsString, "$.jobId")
    }

    private fun blockGeneration(
        started: CountDownLatch,
        release: CountDownLatch,
    ) {
        org.mockito.Mockito
            .doAnswer {
                started.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                SummaryFixtures.validJson
            }.`when`(llmClient)
            .generateSummary(anyString(), anyInt())
    }

    private fun awaitJob(
        jobId: String,
        expected: ProcessingStatus,
    ) {
        await().atMost(Duration.ofSeconds(5)).untilAsserted { assertEquals(expected, jobs.get("owner", jobId).status) }
    }

    private fun awaitIdle() {
        await().atMost(Duration.ofSeconds(5)).until { executor.activeCount == 0 && executor.threadPoolExecutor.queue.isEmpty() }
    }

    private fun MockHttpServletRequestDsl.auth(token: String = "owner-token") {
        header("Authorization", "Bearer $token")
    }

    companion object {
        private val TEXT = "프로세스는 실행 중인 프로그램이며 상태 정보를 PCB에 보관한다. 운영체제는 실행 상태와 대기 상태를 관리한다. ".repeat(3).trim()
    }
}
