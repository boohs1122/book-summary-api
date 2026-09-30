package com.boohs.booksummary.common

import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(BusinessException::class)
    fun handleBusiness(e: BusinessException): ResponseEntity<ErrorResponse> {
        log.info("비즈니스 예외 code={} message={}", e.errorCode, e.message)
        return respond(e.errorCode, e.message)
    }

    @ExceptionHandler(
        MethodArgumentNotValidException::class,
        HandlerMethodValidationException::class,
        HttpMessageNotReadableException::class,
        MethodArgumentTypeMismatchException::class,
        MissingServletRequestParameterException::class,
    )
    fun handleInvalidRequest(e: Exception): ResponseEntity<ErrorResponse> {
        log.info("잘못된 요청 {}: {}", e.javaClass.simpleName, e.message)
        return respond(ErrorCode.INVALID_REQUEST)
    }

    // 명세에 405 코드가 없으므로 지원하지 않는 메서드도 없는 경로로 취급한다
    @ExceptionHandler(
        NoResourceFoundException::class,
        HttpRequestMethodNotSupportedException::class,
    )
    fun handleNotFound(e: Exception): ResponseEntity<ErrorResponse> {
        log.info("없는 경로 {}: {}", e.javaClass.simpleName, e.message)
        return respond(ErrorCode.NOT_FOUND)
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ErrorResponse> {
        log.error("처리되지 않은 예외", e)
        return respond(ErrorCode.INTERNAL_ERROR)
    }

    private fun respond(
        errorCode: ErrorCode,
        message: String = errorCode.defaultMessage,
    ) = ResponseEntity.status(errorCode.status).body(ErrorResponse.of(errorCode, message))
}
