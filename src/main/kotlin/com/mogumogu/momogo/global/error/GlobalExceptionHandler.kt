package com.mogumogu.momogo.global.error

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val problemDetail = createProblemDetail(
            status = HttpStatus.BAD_REQUEST,
            detail = ErrorCode.INVALID_REQUEST.message,
        )

        problemDetail.setProperty(
            "errors",
            ex.bindingResult.fieldErrors.map { fieldError ->
                FieldValidationError(
                    field = fieldError.field,
                    message = fieldError.defaultMessage ?: "올바르지 않은 값입니다.",
                )
            },
        )

        return handleExceptionInternal(ex, problemDetail, headers, status, request)
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpectedException(
        exception: Exception,
        request: HttpServletRequest,
    ): ProblemDetail {
        log.error(
            "Unhandled exception: method={}, path={}",
            request.method,
            request.requestURI,
            exception,
        )

        return createProblemDetail(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            detail = ErrorCode.INTERNAL_SERVER_ERROR.message,
        )
    }

    private fun createProblemDetail(
        status: HttpStatus,
        detail: String,
    ): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detail).apply {
            title = status.reasonPhrase
        }

    private data class FieldValidationError(
        val field: String,
        val message: String,
    )
}
