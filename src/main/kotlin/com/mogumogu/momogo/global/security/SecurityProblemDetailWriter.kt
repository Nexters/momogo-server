package com.mogumogu.momogo.global.security

import com.mogumogu.momogo.global.error.ErrorCode
import com.mogumogu.momogo.global.error.withErrorCode
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.net.URI

@Component
class SecurityProblemDetailWriter(
    private val objectMapper: ObjectMapper,
) {

    fun write(
        request: HttpServletRequest,
        response: HttpServletResponse,
        status: HttpStatus,
        errorCode: ErrorCode,
    ) {
        val problemDetail = ProblemDetail
            .forStatusAndDetail(status, errorCode.message)
            .apply {
                title = status.reasonPhrase
                instance = URI.create(request.requestURI)
                withErrorCode(errorCode)
            }

        response.status = status.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        objectMapper.writeValue(response.outputStream, problemDetail)
    }
}
