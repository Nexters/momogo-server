package com.mogumogu.momogo.global.security

import com.mogumogu.momogo.global.error.ErrorCode
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

@Component
class ProblemDetailAuthenticationEntryPoint(
    private val problemDetailWriter: SecurityProblemDetailWriter,
) : AuthenticationEntryPoint {

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authenticationException: AuthenticationException,
    ) {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
        problemDetailWriter.write(
            request = request,
            response = response,
            status = HttpStatus.UNAUTHORIZED,
            errorCode = ErrorCode.INVALID_AUTH_CREDENTIALS,
        )
    }
}
