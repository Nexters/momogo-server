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

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolationException(
        exception: DataIntegrityViolationException,
        request: HttpServletRequest,
    ): ProblemDetail {
        val errorCode = when {
            exception.hasConstraint(PHOTO_OBJECT_KEY_UNIQUE_CONSTRAINT_PATTERN) ->
                ErrorCode.PHOTO_ALREADY_REGISTERED
            exception.hasConstraint(LOGIN_ACCOUNT_UNIQUE_CONSTRAINT_PATTERN) ->
                ErrorCode.DUPLICATE_LOGIN_ACCOUNT
            else -> null
        }
        if (errorCode != null) {
            return createProblemDetail(
                status = HttpStatus.CONFLICT,
                errorCode = errorCode,
            )
        }

        logUnexpectedException(exception, request)

        return createProblemDetail(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            errorCode = ErrorCode.INTERNAL_SERVER_ERROR,
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

    private fun DataIntegrityViolationException.hasConstraint(pattern: Regex): Boolean =
        generateSequence<Throwable>(this) { it.cause }
            .filterIsInstance<ConstraintViolationException>()
            .mapNotNull { it.constraintName }
            .any(pattern::containsMatchIn)

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
        const val PHOTO_OBJECT_KEY_UNIQUE_CONSTRAINT = "uq_photo_object_key"
        val LOGIN_ACCOUNT_UNIQUE_CONSTRAINT_PATTERN = Regex(
            constraintPattern(LOGIN_ACCOUNT_UNIQUE_CONSTRAINT),
        )
        val PHOTO_OBJECT_KEY_UNIQUE_CONSTRAINT_PATTERN = Regex(
            constraintPattern(PHOTO_OBJECT_KEY_UNIQUE_CONSTRAINT),
        )
        fun constraintPattern(constraintName: String): String =
            """(?i)(?:^|[."`])$constraintName(?:$|_INDEX_|["`\s])"""
    }
}
