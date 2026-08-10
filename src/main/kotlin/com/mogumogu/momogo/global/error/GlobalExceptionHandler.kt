package com.mogumogu.momogo.global.error

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.TypeMismatchException
import org.springframework.http.*
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.method.annotation.HandlerMethodValidationException
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
            errorCode = ErrorCode.INVALID_REQUEST,
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

    override fun handleHttpMessageNotReadable(
        ex: HttpMessageNotReadableException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? =
        handleExceptionInternal(
            ex,
            createProblemDetail(
                status = HttpStatus.BAD_REQUEST,
                errorCode = ErrorCode.INVALID_REQUEST,
            ),
            headers,
            HttpStatus.BAD_REQUEST,
            request,
        )

    override fun handleTypeMismatch(
        ex: TypeMismatchException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? =
        handleExceptionInternal(
            ex,
            createProblemDetail(
                status = HttpStatus.BAD_REQUEST,
                errorCode = ErrorCode.INVALID_REQUEST,
            ),
            headers,
            HttpStatus.BAD_REQUEST,
            request,
        )

    override fun handleHandlerMethodValidationException(
        ex: HandlerMethodValidationException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? =
        handleExceptionInternal(
            ex,
            createProblemDetail(
                status = HttpStatus.BAD_REQUEST,
                errorCode = ErrorCode.INVALID_REQUEST,
            ),
            headers,
            HttpStatus.BAD_REQUEST,
            request,
        )

    @ExceptionHandler(Exception::class)
    fun handleUnexpectedException(
        exception: Exception,
        request: HttpServletRequest,
    ): ProblemDetail {
        logUnexpectedException(exception, request)

        return createProblemDetail(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            errorCode = ErrorCode.INTERNAL_SERVER_ERROR,
        )
    }

    private fun createProblemDetail(
        status: HttpStatus,
        errorCode: ErrorCode,
    ): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, errorCode.message).apply {
            title = status.reasonPhrase
            withErrorCode(errorCode)
        }

    private fun logUnexpectedException(
        exception: Exception,
        request: HttpServletRequest,
    ) {
        log.error(
            "Unhandled exception: method={}, path={}, exceptionType={}",
            request.method,
            request.requestURI,
            exception.javaClass.name,
        )
    }

    private data class FieldValidationError(
        val field: String,
        val message: String,
    )
}
