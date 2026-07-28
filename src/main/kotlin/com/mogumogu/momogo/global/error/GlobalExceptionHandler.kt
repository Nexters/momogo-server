package com.mogumogu.momogo.global.error

import jakarta.servlet.http.HttpServletRequest
import org.hibernate.exception.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.*
import org.springframework.http.converter.HttpMessageNotReadableException
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
                detail = ErrorCode.INVALID_REQUEST.message,
            ),
            headers,
            HttpStatus.BAD_REQUEST,
            request,
        )

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolationException(
        exception: DataIntegrityViolationException,
        request: HttpServletRequest,
    ): ProblemDetail {
        if (exception.isDuplicateLoginAccount()) {
            return createProblemDetail(
                status = HttpStatus.CONFLICT,
                detail = ErrorCode.DUPLICATE_LOGIN_ACCOUNT.message,
            )
        }

        logUnexpectedException(exception, request)

        return createProblemDetail(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            detail = ErrorCode.INTERNAL_SERVER_ERROR.message,
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpectedException(
        exception: Exception,
        request: HttpServletRequest,
    ): ProblemDetail {
        logUnexpectedException(exception, request)

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

    private fun DataIntegrityViolationException.isDuplicateLoginAccount(): Boolean =
        generateSequence<Throwable>(this) { it.cause }
            .filterIsInstance<ConstraintViolationException>()
            .mapNotNull { it.constraintName }
            .any(LOGIN_ACCOUNT_UNIQUE_CONSTRAINT_PATTERN::containsMatchIn)

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

    private companion object {
        const val LOGIN_ACCOUNT_UNIQUE_CONSTRAINT = "uk_login_account_provider_provider_id"
        val LOGIN_ACCOUNT_UNIQUE_CONSTRAINT_PATTERN = Regex(
            pattern = """(?i)(?:^|[."`])$LOGIN_ACCOUNT_UNIQUE_CONSTRAINT(?:$|_INDEX_|["`\s])""",
        )
    }
}
