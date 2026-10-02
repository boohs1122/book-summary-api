package com.boohs.booksummary.controller

import com.boohs.booksummary.config.FirebaseTokenVerifier
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(properties = ["spring.datasource.url=jdbc:h2:mem:book-controller-test;DB_CLOSE_DELAY=-1"])
@AutoConfigureMockMvc
@Transactional
class BookControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockitoBean
    private lateinit var tokenVerifier: FirebaseTokenVerifier

    @Test
    fun `책을 저장하면 목록과 상세에서 조회하고 삭제할 수 있다`() {
        `when`(tokenVerifier.verify("owner-token")).thenReturn("owner")

        val created =
            mockMvc
                .post("/api/v1/books") {
                    auth("owner-token")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"title":" 운영체제 교재 "}"""
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.bookId") { value(org.hamcrest.Matchers.matchesPattern("bok_[0-9A-HJKMNP-TV-Z]{26}")) }
                    jsonPath("$.title") { value("운영체제 교재") }
                    jsonPath("$.createdAt") { exists() }
                }.andReturn()

        val bookId = JsonPath.read<String>(created.response.contentAsString, "$.bookId")

        mockMvc
            .get("/api/v1/books") { auth("owner-token") }
            .andExpect {
                status { isOk() }
                jsonPath("$.items[0].bookId") { value(bookId) }
                jsonPath("$.items[0].documentCount") { value(0) }
                jsonPath("$.items[0].totalCharCount") { value(0) }
                jsonPath("$.nextCursor") { value(null) }
            }

        mockMvc
            .get("/api/v1/books/$bookId") { auth("owner-token") }
            .andExpect {
                status { isOk() }
                jsonPath("$.bookId") { value(bookId) }
                jsonPath("$.documents") { isArray() }
                jsonPath("$.documents") { isEmpty() }
            }

        mockMvc.delete("/api/v1/books/$bookId") { auth("owner-token") }.andExpect {
            status { isNoContent() }
            content { string("") }
        }
        mockMvc.get("/api/v1/books/$bookId") { auth("owner-token") }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("NOT_FOUND") }
        }
    }

    @Test
    fun `다른 사용자 책은 목록에 없고 상세와 삭제는 거부한다`() {
        `when`(tokenVerifier.verify("owner-token")).thenReturn("owner")
        `when`(tokenVerifier.verify("other-token")).thenReturn("other")
        val created =
            mockMvc
                .post("/api/v1/books") {
                    auth("owner-token")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"title":"책"}"""
                }.andReturn()
        val bookId = JsonPath.read<String>(created.response.contentAsString, "$.bookId")

        mockMvc.get("/api/v1/books") { auth("other-token") }.andExpect {
            status { isOk() }
            jsonPath("$.items") { isEmpty() }
        }
        mockMvc.get("/api/v1/books/$bookId") { auth("other-token") }.andExpect {
            status { isForbidden() }
            jsonPath("$.error.code") { value("FORBIDDEN") }
        }
        mockMvc.delete("/api/v1/books/$bookId") { auth("other-token") }.andExpect {
            status { isForbidden() }
            jsonPath("$.error.code") { value("FORBIDDEN") }
        }
        mockMvc.get("/api/v1/books/$bookId") { auth("owner-token") }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `빈 제목과 100자를 넘는 제목은 거부한다`() {
        `when`(tokenVerifier.verify("owner-token")).thenReturn("owner")
        listOf(" ", "a".repeat(101)).forEach { title ->
            mockMvc
                .post("/api/v1/books") {
                    auth("owner-token")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"title":"$title"}"""
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value("INVALID_REQUEST") }
                }
        }
    }

    private fun org.springframework.test.web.servlet.MockHttpServletRequestDsl.auth(token: String) {
        header("Authorization", "Bearer $token")
    }
}
