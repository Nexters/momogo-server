package com.mogumogu.momogo.global.security

import com.mogumogu.momogo.global.error.ErrorCode
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component

@Component
class ProblemDetailAccessDeniedHandler(
    private val problemDetailWriter: SecurityProblemDetailWriter,
) : AccessDeniedHandler {

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ) {
        problemDetailWriter.write(
            request = request,
            response = response,
            status = HttpStatus.FORBIDDEN,
            detail = ErrorCode.FORBIDDEN.message,
        )
    }
}
