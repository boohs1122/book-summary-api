package com.boohs.booksummary.common

data class ErrorResponse(
    val error: ErrorBody,
) {
    data class ErrorBody(
        val code: String,
        val message: String,
    )

    companion object {
        fun of(
            errorCode: ErrorCode,
            message: String = errorCode.defaultMessage,
        ) = ErrorResponse(ErrorBody(errorCode.name, message))
    }
}
