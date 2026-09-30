package com.boohs.booksummary.config

import com.boohs.booksummary.common.BusinessException
import com.boohs.booksummary.common.ErrorCode
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.web.servlet.HandlerInterceptor

class FirebaseAuthInterceptor(
    private val verifyToken: (String) -> String,
) : HandlerInterceptor {
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)
        val token = header?.takeIf { it.startsWith(BEARER_PREFIX) }?.removePrefix(BEARER_PREFIX)?.trim()
        if (token.isNullOrEmpty()) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }
        request.setAttribute(AUTH_UID_ATTRIBUTE, verifyToken(token))
        return true
    }

    companion object {
        const val AUTH_UID_ATTRIBUTE = "authenticatedUid"
        private const val BEARER_PREFIX = "Bearer "
    }
}
