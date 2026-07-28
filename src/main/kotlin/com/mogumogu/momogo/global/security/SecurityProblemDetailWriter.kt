package com.mogumogu.momogo.global.security

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
        detail: String,
    ) {
        val problemDetail = ProblemDetail
            .forStatusAndDetail(status, detail)
            .apply {
                title = status.reasonPhrase
                instance = URI.create(request.requestURI)
            }

        response.status = status.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        objectMapper.writeValue(response.outputStream, problemDetail)
    }
}
