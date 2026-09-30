package com.boohs.booksummary.common

import com.boohs.booksummary.config.FirebaseTokenVerifier
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@WebMvcTest(GlobalExceptionHandlerTest.ProbeController::class)
@Import(GlobalExceptionHandlerTest.ProbeController::class)
class GlobalExceptionHandlerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockitoBean
    private lateinit var tokenVerifier: FirebaseTokenVerifier

    @Test
    fun `비즈니스 예외는 지정한 코드와 상태로 변환한다`() {
        mockMvc.get("/probe/business").andExpectError(409, ErrorCode.JOB_IN_PROGRESS)
    }

    @Test
    fun `본문 검증 실패와 파싱 실패는 INVALID_REQUEST로 변환한다`() {
        mockMvc
            .post("/probe/body") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"title": ""}"""
            }.andExpectError(400, ErrorCode.INVALID_REQUEST)

        mockMvc
            .post("/probe/body") {
                contentType = MediaType.APPLICATION_JSON
                content = "{"
            }.andExpectError(400, ErrorCode.INVALID_REQUEST)
    }

    @Test
    fun `없는 경로와 지원하지 않는 메서드는 NOT_FOUND로 변환한다`() {
        mockMvc.get("/probe/nothing-here").andExpectError(404, ErrorCode.NOT_FOUND)
        mockMvc.post("/probe/business").andExpectError(404, ErrorCode.NOT_FOUND)
    }

    @Test
    fun `예상하지 못한 예외는 스택 트레이스 없이 INTERNAL_ERROR로 변환한다`() {
        mockMvc
            .get("/probe/unexpected")
            .andExpectError(500, ErrorCode.INTERNAL_ERROR)
            .andExpect { jsonPath("$.trace") { doesNotExist() } }
    }

    private fun ResultActionsDsl.andExpectError(
        status: Int,
        errorCode: ErrorCode,
    ) = andExpect {
        status { isEqualTo(status) }
        jsonPath("$.error.code") { value(errorCode.name) }
        jsonPath("$.error.message") { value(errorCode.defaultMessage) }
    }

    data class ProbeRequest(
        @field:NotBlank val title: String,
    )

    @RestController
    class ProbeController {
        @GetMapping("/probe/business")
        fun business(): Nothing = throw BusinessException(ErrorCode.JOB_IN_PROGRESS)

        @PostMapping("/probe/body")
        fun body(
            @Valid @RequestBody request: ProbeRequest,
        ) = request

        @GetMapping("/probe/unexpected")
        fun unexpected(): Nothing = throw IllegalStateException("테스트용 예외")
    }
}
