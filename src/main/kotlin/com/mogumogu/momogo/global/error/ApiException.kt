package com.mogumogu.momogo.global.error

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.ErrorResponseException

sealed class ApiException protected constructor(
    status: HttpStatus,
    val errorCode: ErrorCode,
    detail: String,
) : ErrorResponseException(
    status,
    ProblemDetail.forStatusAndDetail(status, detail).apply {
        title = status.reasonPhrase
    },
    null,
) {

    class BadRequest(
        errorCode: ErrorCode,
        detail: String = errorCode.message,
    ) : ApiException(
        status = HttpStatus.BAD_REQUEST,
        errorCode = errorCode,
        detail = detail,
    )

    class NotFound(
        errorCode: ErrorCode,
        detail: String = errorCode.message,
    ) : ApiException(
        status = HttpStatus.NOT_FOUND,
        errorCode = errorCode,
        detail = detail,
    )

    class Conflict(
        errorCode: ErrorCode,
        detail: String = errorCode.message,
    ) : ApiException(
        status = HttpStatus.CONFLICT,
        errorCode = errorCode,
        detail = detail,
    )
}
