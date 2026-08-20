package com.mogumogu.momogo.global.error

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.TypeMismatchException
import org.springframework.http.*
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun handleMissingServletRequestParameter(
        ex: MissingServletRequestParameterException,
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
                ApiValidationError(
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

    /**
     * 이 advice가 처리하는 모든 예외가 지나가는 지점이므로 여기서 응답 실패를 로그로 남긴다.
     * [ApiException]은 [org.springframework.web.ErrorResponseException]으로 처리되어
     * [handleUnexpectedException]까지 오지 않기 때문에 4xx 응답이 로그에 남지 않는 문제를 막는다.
     */
    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        logHandledException(ex, body, statusCode, request)

        return super.handleExceptionInternal(ex, body, headers, statusCode, request)
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

    private fun logHandledException(
        exception: Exception,
        body: Any?,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ) {
        val servletRequest = (request as? ServletWebRequest)?.request
        val arguments = arrayOf<Any?>(
            servletRequest?.method,
            servletRequest?.requestURI,
            statusCode.value(),
            resolveErrorCode(exception, body),
            exception.javaClass.name,
        )

        if (statusCode.is5xxServerError) {
            log.error(HANDLED_EXCEPTION_MESSAGE, *arguments)
        } else {
            log.warn(HANDLED_EXCEPTION_MESSAGE, *arguments)
        }
    }

    private fun resolveErrorCode(
        exception: Exception,
        body: Any?,
    ): String? =
        when {
            exception is ApiException -> exception.errorCode.name
            body is ProblemDetail -> body.properties?.get("code")?.toString()
            else -> null
        }

    private companion object {
        const val HANDLED_EXCEPTION_MESSAGE =
            "API 오류 응답: method={}, path={}, status={}, code={}, exceptionType={}"
    }
}
