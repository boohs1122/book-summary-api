package com.boohs.booksummary.controller

import com.boohs.booksummary.config.FirebaseTokenVerifier
import com.boohs.booksummary.domain.Document
import com.boohs.booksummary.domain.Quiz
import com.boohs.booksummary.domain.QuizResult
import com.boohs.booksummary.domain.Summary
import com.boohs.booksummary.llm.SummaryFixtures
import com.boohs.booksummary.repository.BookRepository
import com.boohs.booksummary.repository.DocumentRepository
import com.boohs.booksummary.repository.QuizRepository
import com.boohs.booksummary.repository.QuizResultRepository
import com.boohs.booksummary.repository.SummaryRepository
import com.boohs.booksummary.service.BookService
import com.boohs.booksummary.service.Ulid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.time.Instant

@SpringBootTest(properties = ["spring.datasource.url=jdbc:h2:mem:book-title-update-test;DB_CLOSE_DELAY=-1"])
@AutoConfigureMockMvc
@Transactional
class BookTitleUpdateTest(
    @Autowired private val mvc: MockMvc,
    @Autowired private val books: BookService,
    @Autowired private val bookRepository: BookRepository,
    @Autowired private val documents: DocumentRepository,
    @Autowired private val summaries: SummaryRepository,
    @Autowired private val quizzes: QuizRepository,
    @Autowired private val results: QuizResultRepository,
    @Autowired private val mapper: JsonMapper,
) {
    @MockitoBean
    private lateinit var verifier: FirebaseTokenVerifier

    @BeforeEach
    fun setup() {
        `when`(verifier.verify("owner-token")).thenReturn("owner")
        `when`(verifier.verify("other-token")).thenReturn("other")
    }

    @Test
    fun `제목만 수정하고 모든 연결 데이터와 조회 응답에 반영한다`() {
        val book = books.create("owner", "잘못된 제목")
        val createdAt = book.createdAt
        val now = Instant.now()
        val document = Document("doc_${Ulid.generate()}", book, 1, "원문".repeat(100), now).apply { complete(now) }
        documents.save(document)
        val summary = summaries.save(Summary(document.id, document, SummaryFixtures.validJson))
        val question = mapOf("question" to "문제", "options" to listOf("가", "나", "다", "라"), "answerIndex" to 1, "explanation" to "해설")
        val quiz =
            quizzes.save(
                Quiz(
                    "quz_${Ulid.generate()}",
                    document,
                    mapper.writeValueAsString(
                        mapOf(
                            "questions" to
                                List(3) {
                                    question
                                },
                        ),
                    ),
                    now,
                ),
            )
        val result = results.save(QuizResult("res_${Ulid.generate()}", quiz, "[]", 2, 3, now))

        update(book.id, "  수정된 책 제목  ")
        mvc.get("/api/v1/books") { header("Authorization", "Bearer owner-token") }.andExpect {
            status { isOk() }
            jsonPath("$.items[0].title") { value("수정된 책 제목") }
            jsonPath("$.items[0].documentCount") { value(1) }
            jsonPath("$.items[0].latestScore.correct") { value(2) }
        }
        mvc.get("/api/v1/books/${book.id}") { header("Authorization", "Bearer owner-token") }.andExpect {
            status { isOk() }
            jsonPath("$.title") { value("수정된 책 제목") }
            jsonPath("$.documents[0].documentId") { value(document.id) }
            jsonPath("$.documents[0].hasQuiz") { value(true) }
        }
        mvc.get("/api/v1/documents/${document.id}") { header("Authorization", "Bearer owner-token") }.andExpect {
            status { isOk() }
            jsonPath("$.bookTitle") { value("수정된 책 제목") }
            jsonPath("$.summary.title") { exists() }
            jsonPath("$.quiz.quizId") { value(quiz.id) }
        }
        bookRepository.flush()
        assertEquals(createdAt, bookRepository.findById(book.id).orElseThrow().createdAt)
        assertEquals(document.extractedText, documents.findById(document.id).orElseThrow().extractedText)
        assertEquals(summary.contentJson, summaries.findById(summary.id).orElseThrow().contentJson)
        assertEquals(quiz.questionsJson, quizzes.findById(quiz.id).orElseThrow().questionsJson)
        assertEquals(result.answersJson, results.findById(result.id).orElseThrow().answersJson)
        assertEquals(1, results.count())
    }

    @Test
    fun `공백 제거 후 한 글자와 100글자는 허용한다`() {
        val book = books.create("owner", "원래 제목")
        listOf("가", "가".repeat(100)).forEach { title -> update(book.id, "  $title  ") }
    }

    @Test
    fun `공백 제목과 101글자 및 누락 null 제목을 거부하고 기존 제목을 보존한다`() {
        val book = books.create("owner", "원래 제목")
        val bodies =
            listOf("", " \t\n ", "가".repeat(101)).map { mapper.writeValueAsString(mapOf("title" to it)) } +
                listOf("{}", """{"title":null}""")
        bodies.forEach { body ->
            mvc
                .patch("/api/v1/books/${book.id}") {
                    header("Authorization", "Bearer owner-token")
                    contentType = MediaType.APPLICATION_JSON
                    content = body
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value("INVALID_REQUEST") }
                }
        }
        assertEquals("원래 제목", bookRepository.findById(book.id).orElseThrow().title)
    }

    @Test
    fun `다른 사용자의 책 제목 수정은 거부한다`() {
        val book = books.create("owner", "원래 제목")
        mvc
            .patch("/api/v1/books/${book.id}") {
                header("Authorization", "Bearer other-token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"title":"변경"}"""
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.error.code") { value("FORBIDDEN") }
            }
        assertEquals("원래 제목", bookRepository.findById(book.id).orElseThrow().title)
    }

    @Test
    fun `없는 책은 NOT_FOUND를 반환한다`() {
        mvc
            .patch("/api/v1/books/bok_missing") {
                header("Authorization", "Bearer owner-token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"title":"변경"}"""
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.error.code") { value("NOT_FOUND") }
            }
    }

    @Test
    fun `인증 없이 책 제목을 수정할 수 없다`() {
        val book = books.create("owner", "원래 제목")
        mvc
            .patch("/api/v1/books/${book.id}") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"title":"변경"}"""
            }.andExpect {
                status { isUnauthorized() }
                jsonPath("$.error.code") { value("UNAUTHORIZED") }
            }
    }

    private fun update(
        bookId: String,
        title: String,
    ) {
        mvc
            .patch("/api/v1/books/$bookId") {
                header("Authorization", "Bearer owner-token")
                contentType = MediaType.APPLICATION_JSON
                content = mapper.writeValueAsString(mapOf("title" to title))
            }.andExpect {
                status { isOk() }
                jsonPath("$.bookId") { value(bookId) }
                jsonPath("$.title") { value(title.trim()) }
                jsonPath("$.length()") { value(2) }
            }
    }
}
