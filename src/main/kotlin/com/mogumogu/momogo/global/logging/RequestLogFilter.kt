package com.mogumogu.momogo.global.logging

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 요청마다 식별자를 MDC에 넣고 처리 결과를 액세스 로그로 남긴다.
 * 가장 바깥 필터이므로 [LogContext]의 MDC 키 정리도 이 필터가 담당한다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestLogFilter : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val requestId = resolveRequestId(request)
        val startedAt = System.nanoTime()

        MDC.put(LogContext.REQUEST_ID_KEY, requestId)
        response.setHeader(LogContext.REQUEST_ID_HEADER, requestId)

        try {
            filterChain.doFilter(request, response)
        } finally {
            logAccess(request, response, startedAt)
            MDC.remove(LogContext.REQUEST_ID_KEY)
            MDC.remove(LogContext.USER_ID_KEY)
        }
    }

    private fun logAccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        startedAt: Long,
    ) {
        log.info(
            "요청 처리: method={}, path={}, status={}, durationMs={}",
            request.method,
            request.requestURI,
            response.status,
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
        )
    }

    /**
     * 클라이언트가 보낸 식별자는 로그 위조에 쓰일 수 있으므로 허용한 형식일 때만 그대로 사용한다.
     */
    private fun resolveRequestId(request: HttpServletRequest): String {
        val provided = request.getHeader(LogContext.REQUEST_ID_HEADER)

        return if (provided != null && ALLOWED_REQUEST_ID.matches(provided)) {
            provided
        } else {
            UUID.randomUUID().toString().replace("-", "").take(GENERATED_REQUEST_ID_LENGTH)
        }
    }

    private companion object {
        const val GENERATED_REQUEST_ID_LENGTH = 8
        val ALLOWED_REQUEST_ID = Regex("^[A-Za-z0-9._-]{1,64}$")
    }
}
