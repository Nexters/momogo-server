package com.mogumogu.momogo.global.logging

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.annotation.Order
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 인증된 사용자 ID를 MDC에 넣어 한 사용자의 동선을 로그에서 이어볼 수 있게 한다.
 * 시큐리티 필터 체인(기본 순서 -100) 이후에 실행되어야 인증 정보를 읽을 수 있으므로 순서를 0으로 둔다.
 * MDC 정리는 가장 바깥의 [RequestLogFilter]가 담당한다.
 */
@Component
@Order(0)
class AuthenticatedUserLogFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        resolveUserId()?.let { userId -> MDC.put(LogContext.USER_ID_KEY, userId) }

        filterChain.doFilter(request, response)
    }

    private fun resolveUserId(): String? =
        (
            SecurityContextHolder
                .getContext()
                .authentication
                ?.takeIf { it.isAuthenticated }
                ?.principal as? Jwt
            )?.subject
}
