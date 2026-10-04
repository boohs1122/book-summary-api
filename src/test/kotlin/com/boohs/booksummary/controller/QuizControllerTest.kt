package com.boohs.booksummary.controller

import com.boohs.booksummary.common.BusinessException
import com.boohs.booksummary.common.ErrorCode
import com.boohs.booksummary.config.FirebaseTokenVerifier
import com.boohs.booksummary.domain.Document
import com.boohs.booksummary.domain.Job
import com.boohs.booksummary.domain.JobType
import com.boohs.booksummary.domain.ProcessingStatus
import com.boohs.booksummary.domain.Quiz
import com.boohs.booksummary.domain.Summary
import com.boohs.booksummary.llm.LlmClient
import com.boohs.booksummary.llm.SummaryFixtures
import com.boohs.booksummary.repository.BookRepository
import com.boohs.booksummary.repository.DocumentRepository
import com.boohs.booksummary.repository.JobRepository
import com.boohs.booksummary.repository.QuizRepository
import com.boohs.booksummary.repository.QuizResultRepository
import com.boohs.booksummary.repository.SummaryRepository
import com.boohs.booksummary.service.BookService
import com.boohs.booksummary.service.QuizService
import com.boohs.booksummary.service.SummaryJobService
import com.boohs.booksummary.service.Ulid
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
import org.mockito.Mockito.doAnswer
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
import java.util.concurrent.TimeUnit

@SpringBootTest(properties = ["spring.datasource.url=jdbc:h2:mem:quiz-controller-test;DB_CLOSE_DELAY=-1"])
@AutoConfigureMockMvc
class QuizControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val books: BookService,
    @Autowired private val bookRepository: BookRepository,
    @Autowired private val documents: DocumentRepository,
    @Autowired private val summaries: SummaryRepository,
    @Autowired private val jobs: JobRepository,
    @Autowired private val quizRepository: QuizRepository,
    @Autowired private val results: QuizResultRepository,
    @Autowired private val jobService: SummaryJobService,
    @Autowired private val quizService: QuizService,
    @Autowired private val mapper: JsonMapper,
    @Autowired @Qualifier("summaryExecutor") private val executor: ThreadPoolTaskExecutor,
) {
    @MockitoBean private lateinit var tokenVerifier: FirebaseTokenVerifier

    @MockitoBean private lateinit var llm: LlmClient

    @BeforeEach
    fun setup() {
        awaitIdle()
        bookRepository.deleteAll()
        `when`(tokenVerifier.verify("owner-token")).thenReturn("owner")
        `when`(tokenVerifier.verify("other-token")).thenReturn("other")
        `when`(llm.generateQuiz(anyString(), anyString(), anyInt())).thenReturn(QUIZ_JSON)
    }

    @AfterEach
    fun finish() = awaitIdle()

    @Test
    fun `비동기 생성 완료 후 퀴즈에 정답을 노출하지 않고 기존 세트는 200을 반환한다`() {
        val (bookId, documentId) = doneDocument()
        val accepted =
            mockMvc
                .post("/api/v1/documents/$documentId/quiz") { auth() }
                .andExpect {
                    status { isAccepted() }
                    jsonPath("$.status") { value("PROCESSING") }
                }.andReturn()
        val jobId = JsonPath.read<String>(accepted.response.contentAsString, "$.jobId")
        awaitJob(jobId, ProcessingStatus.DONE)
        val quizId = requireNotNull(jobService.get("owner", jobId).quizId)

        mockMvc.get("/api/v1/jobs/$jobId") { auth() }.andExpect {
            jsonPath("$.type") { value("QUIZ") }
            jsonPath("$.quizId") { value(quizId) }
            jsonPath("$.documentId") { value(documentId) }
        }
        mockMvc.get("/api/v1/documents/$documentId/quiz") { auth() }.andExpect {
            status { isOk() }
            jsonPath("$.quizId") { value(quizId) }
            jsonPath("$.questions.length()") { value(3) }
            jsonPath("$.questions[0].answerIndex") { doesNotExist() }
            jsonPath("$.questions[0].explanation") { doesNotExist() }
        }
        mockMvc.get("/api/v1/documents/$documentId") { auth() }.andExpect {
            jsonPath("$.quiz.exists") { value(true) }
            jsonPath("$.quiz.latestScore") { value(null) }
        }
        mockMvc.get("/api/v1/books/$bookId") { auth() }.andExpect {
            jsonPath("$.documents[0].hasQuiz") { value(true) }
            jsonPath("$.documents[0].latestScore") { value(null) }
        }
        mockMvc.post("/api/v1/documents/$documentId/quiz") { auth() }.andExpect {
            status { isOk() }
            jsonPath("$.quizId") { value(quizId) }
            jsonPath("$.status") { value("DONE") }
        }
    }

    @Test
    fun `answers를 index 순서로 채점하고 미응답은 오답 처리하며 재응시 결과와 최신점수를 갱신한다`() {
        val (bookId, documentId) = doneDocument()
        val quizId = makeQuiz(documentId)

        listOf(
            """{"answers":[{"index":0,"selected":1},{"index":0,"selected":1},{"index":2,"selected":1}]}""",
            """{"answers":[{"index":0,"selected":4},{"index":1,"selected":1},{"index":2,"selected":1}]}""",
            """{"answers":[{"index":3,"selected":1},{"index":1,"selected":1},{"index":2,"selected":1}]}""",
            """{"answers":[{"selected":1},{"index":1,"selected":1},{"index":2,"selected":1}]}""",
            """{"answers":[{"index":0,"selected":1},{"index":1,"selected":1}]}""",
        ).forEach { body ->
            mockMvc
                .post("/api/v1/quizzes/$quizId/results") {
                    auth()
                    contentType = MediaType.APPLICATION_JSON
                    content = body
                }.andExpect { status { isBadRequest() } }
        }

        val first =
            submit(
                quizId,
                listOf(
                    mapOf("index" to 2, "selected" to 1),
                    mapOf("index" to 0, "selected" to null),
                    mapOf(
                        "index" to 1,
                        "selected" to 2,
                    ),
                ),
            )
        assertEquals(1, JsonPath.read<Int>(first, "$.score.correct"))
        assertEquals(0, JsonPath.read<Int>(first, "$.items[0].index"))
        assertEquals(null, JsonPath.read<Any?>(first, "$.items[0].selected"))
        assertEquals(false, JsonPath.read<Boolean>(first, "$.items[0].correct"))
        assertEquals(1, JsonPath.read<Int>(first, "$.items[0].answerIndex"))
        assertEquals("해설", JsonPath.read<String>(first, "$.items[0].explanation"))

        submit(quizId, (0..2).map { mapOf("index" to it, "selected" to 1) })
        assertEquals(2, results.count())
        mockMvc.get("/api/v1/documents/$documentId") { auth() }.andExpect {
            jsonPath("$.quiz.latestScore.correct") { value(3) }
            jsonPath("$.quiz.latestScore.total") { value(3) }
        }
        mockMvc.get("/api/v1/books/$bookId") { auth() }.andExpect {
            jsonPath("$.documents[0].latestScore.correct") { value(3) }
        }
        mockMvc.get("/api/v1/books") { auth() }.andExpect {
            jsonPath("$.items[0].latestScore.correct") { value(3) }
        }
    }

    @Test
    fun `실패한 퀴즈는 문서를 유지하고 재요청할 수 있으며 진행 중 중복은 409다`() {
        val (_, documentId) = doneDocument()
        `when`(llm.generateQuiz(anyString(), anyString(), anyInt())).thenThrow(BusinessException(ErrorCode.LLM_FAILED))
        val failedJobId = requestQuiz(documentId)
        awaitJob(failedJobId, ProcessingStatus.FAILED)
        assertEquals(ProcessingStatus.DONE, documents.findById(documentId).orElseThrow().status)

        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        doAnswer {
            started.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            QUIZ_JSON
        }.`when`(llm).generateQuiz(anyString(), anyString(), anyInt())
        try {
            mockMvc.post("/api/v1/documents/$documentId/quiz") { auth() }.andExpect { status { isAccepted() } }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            mockMvc.post("/api/v1/documents/$documentId/quiz") { auth() }.andExpect {
                status { isConflict() }
                jsonPath("$.error.code") { value("JOB_IN_PROGRESS") }
            }
            release.countDown()
            awaitIdle()
            assertEquals(ProcessingStatus.DONE, documents.findById(documentId).orElseThrow().status)
        } finally {
            release.countDown()
        }
    }

    @Test
    fun `잘못된 LLM 응답은 한 번 재요청하고 두 번 실패해도 문서 상태를 보존한다`() {
        val (_, validDocumentId) = doneDocument()
        `when`(llm.generateQuiz(anyString(), anyString(), anyInt())).thenReturn("{}", QUIZ_JSON)
        val validJobId = requestQuiz(validDocumentId)
        awaitJob(validJobId, ProcessingStatus.DONE)
        assertEquals(1, quizRepository.count())

        val (_, invalidDocumentId) = doneDocument()
        `when`(llm.generateQuiz(anyString(), anyString(), anyInt())).thenReturn("{}", "{}")
        val invalidJobId = requestQuiz(invalidDocumentId)
        awaitJob(invalidJobId, ProcessingStatus.FAILED)
        assertEquals(ErrorCode.LLM_INVALID_RESPONSE, jobService.get("owner", invalidJobId).errorCode)
        assertEquals(1, quizRepository.count())
        assertEquals(ProcessingStatus.DONE, documents.findById(invalidDocumentId).orElseThrow().status)
        verify(llm, times(4)).generateQuiz(TEXT, SummaryFixtures.validJson, 3)
    }

    @Test
    fun `타 사용자의 퀴즈 조회 제출 요청을 금지한다`() {
        val (_, documentId) = doneDocument()
        val quizId = makeQuiz(documentId)
        mockMvc.post("/api/v1/documents/$documentId/quiz") { auth("other-token") }.andExpect { status { isForbidden() } }
        mockMvc.get("/api/v1/documents/$documentId/quiz") { auth("other-token") }.andExpect { status { isForbidden() } }
        mockMvc
            .post("/api/v1/quizzes/$quizId/results") {
                auth("other-token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"answers":[{"index":0,"selected":1},{"index":1,"selected":1},{"index":2,"selected":1}]}"""
            }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `요약 생성 중에는 퀴즈 요청을 JOB_IN_PROGRESS로 거부한다`() {
        val book = books.create("owner", "진행 중 책")
        val now = Instant.now()
        val document = documents.save(Document("doc_${Ulid.generate()}", book, 1, TEXT, now))
        jobs.save(Job("job_${Ulid.generate()}", document, "owner", now, JobType.SUMMARY))

        mockMvc.post("/api/v1/documents/${document.id}/quiz") { auth() }.andExpect {
            status { isConflict() }
            jsonPath("$.error.code") { value("JOB_IN_PROGRESS") }
        }
    }

    @Test
    fun `삭제가 먼저 커밋되면 늦게 도착한 LLM 응답으로 퀴즈를 부활시키지 않는다`() {
        val (_, documentId) = doneDocument()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        doAnswer {
            started.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            QUIZ_JSON
        }.`when`(llm).generateQuiz(anyString(), anyString(), anyInt())
        try {
            val jobId = requestQuiz(documentId)
            assertTrue(started.await(5, TimeUnit.SECONDS))
            mockMvc.delete("/api/v1/documents/$documentId") { auth() }.andExpect { status { isNoContent() } }
            release.countDown()
            awaitIdle()
            assertEquals(0, jobs.count())
            assertEquals(0, quizRepository.count())
            assertEquals(0, results.count())
            val error = assertThrows(BusinessException::class.java) { jobService.get("owner", jobId) }
            assertEquals(ErrorCode.NOT_FOUND, error.errorCode)
        } finally {
            release.countDown()
        }
    }

    @Test
    fun `중단된 퀴즈 작업 복구에서 완료 문서 상태를 유지한다`() {
        val (_, documentId) = doneDocument()
        val document = documents.findById(documentId).orElseThrow()
        val old = Instant.now().minusSeconds(120)
        val job = jobs.save(Job("job_${Ulid.generate()}", document, "owner", old, JobType.QUIZ))
        job.start(old)
        jobs.save(job)

        assertEquals(1, jobService.recoverInterruptedJobs(Instant.now().minusSeconds(60)))
        assertEquals(ProcessingStatus.FAILED, jobService.get("owner", job.id).status)
        assertEquals(ProcessingStatus.DONE, documents.findById(documentId).orElseThrow().status)
    }

    @Test
    fun `풀이 결과가 있는 퀴즈를 책에서 삭제하면 하위 데이터와 작업을 cascade 삭제한다`() {
        val (bookId, documentId) = doneDocument()
        val quizId = makeQuiz(documentId)
        submit(quizId, (0..2).map { mapOf("index" to it, "selected" to 1) })
        val now = Instant.now()
        val quiz = quizRepository.findById(quizId).orElseThrow()
        jobs.save(Job("job_${Ulid.generate()}", quiz.document, "owner", now, JobType.QUIZ).apply { complete(now, quizId) })

        mockMvc.delete("/api/v1/books/$bookId") { auth() }.andExpect { status { isNoContent() } }

        assertEquals(0, documents.count())
        assertEquals(0, summaries.count())
        assertEquals(0, quizRepository.count())
        assertEquals(0, results.count())
        assertEquals(0, jobs.count())
    }

    private fun doneDocument(): Pair<String, String> {
        val book = books.create("owner", "퀴즈 책")
        val now = Instant.now()
        val document = Document("doc_${Ulid.generate()}", book, 1, TEXT, now).apply { complete(now) }
        documents.save(document)
        summaries.save(Summary("sum_${Ulid.generate()}", document, SummaryFixtures.validJson))
        return book.id to document.id
    }

    private fun makeQuiz(documentId: String): String {
        val doc = documents.findById(documentId).orElseThrow()
        return quizRepository.save(Quiz("quz_${Ulid.generate()}", doc, QUIZ_JSON, Instant.now())).id
    }

    private fun requestQuiz(documentId: String): String {
        val response = mockMvc.post("/api/v1/documents/$documentId/quiz") { auth() }.andReturn()
        return JsonPath.read(response.response.contentAsString, "$.jobId")
    }

    private fun submit(
        quizId: String,
        answers: List<Map<String, Int?>>,
    ): String =
        mockMvc
            .post("/api/v1/quizzes/$quizId/results") {
                auth()
                contentType = MediaType.APPLICATION_JSON
                content = mapper.writeValueAsString(mapOf("answers" to answers))
            }.andExpect { status { isCreated() } }
            .andReturn()
            .response.contentAsString

    private fun awaitJob(
        jobId: String,
        status: ProcessingStatus,
    ) {
        await().atMost(Duration.ofSeconds(5)).untilAsserted { assertEquals(status, jobService.get("owner", jobId).status) }
    }

    private fun awaitIdle() {
        await().atMost(Duration.ofSeconds(5)).until { executor.activeCount == 0 && executor.threadPoolExecutor.queue.isEmpty() }
    }

    private fun MockHttpServletRequestDsl.auth(token: String = "owner-token") {
        header("Authorization", "Bearer $token")
    }

    companion object {
        private val TEXT = "운영체제는 프로세스의 상태와 자원을 관리한다. ".repeat(8).trim()
        private val QUIZ_JSON =
            """{"questions":[{"question":"문항1","options":["가","나","다","라"],"answerIndex":1,"explanation":"해설"},{"question":"문항2","options":["가","나","다","라"],"answerIndex":1,"explanation":"해설"},{"question":"문항3","options":["가","나","다","라"],"answerIndex":1,"explanation":"해설"}]}"""
    }
}
