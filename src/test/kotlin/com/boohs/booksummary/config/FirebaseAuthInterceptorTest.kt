package com.boohs.booksummary.config

import com.boohs.booksummary.common.BusinessException
import com.boohs.booksummary.common.ErrorCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class FirebaseAuthInterceptorTest {
    private val response = MockHttpServletResponse()

    @Test
    fun `토큰이 없거나 형식이 다르면 거부한다`() {
        val interceptor = FirebaseAuthInterceptor { error("토큰 검증이 호출되면 안 됨") }

        listOf(null, "Basic token", "Bearer ").forEach { header ->
            val request = MockHttpServletRequest()
            if (header != null) request.addHeader("Authorization", header)

            val exception =
                assertThrows(BusinessException::class.java) {
                    interceptor.preHandle(request, response, Any())
                }
            assertEquals(ErrorCode.UNAUTHORIZED, exception.errorCode)
        }
    }

    @Test
    fun `검증된 uid를 요청에 저장한다`() {
        val request = MockHttpServletRequest()
        request.addHeader("Authorization", "Bearer valid-token")
        val interceptor =
            FirebaseAuthInterceptor { token ->
                assertEquals("valid-token", token)
                "test-uid"
            }

        assertEquals(true, interceptor.preHandle(request, response, Any()))
        assertEquals("test-uid", request.getAttribute(FirebaseAuthInterceptor.AUTH_UID_ATTRIBUTE))
    }
}
