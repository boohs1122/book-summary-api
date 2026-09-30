package com.boohs.booksummary.common

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val status: HttpStatus,
    val defaultMessage: String,
) {
    TEXT_TOO_SHORT(HttpStatus.BAD_REQUEST, "추출된 텍스트가 너무 짧습니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 대상을 찾을 수 없습니다."),
    JOB_IN_PROGRESS(HttpStatus.CONFLICT, "이미 진행 중인 작업이 있습니다."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    LLM_FAILED(HttpStatus.BAD_GATEWAY, "요약 생성에 실패했습니다. 잠시 후 다시 시도해 주세요."),
    LLM_INVALID_RESPONSE(HttpStatus.BAD_GATEWAY, "요약 결과가 올바르지 않습니다. 다시 시도해 주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다."),
}
