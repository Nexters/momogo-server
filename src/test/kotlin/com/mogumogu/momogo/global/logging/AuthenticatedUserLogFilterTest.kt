package com.mogumogu.momogo.global.logging

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import jakarta.servlet.FilterChain
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken

class AuthenticatedUserLogFilterTest : BehaviorSpec({

    val filter = AuthenticatedUserLogFilter()

    afterTest {
        SecurityContextHolder.clearContext()
        MDC.clear()
    }

    given("인증된 요청이면") {
        `when`("액세스 토큰의 subject가 사용자 ID일 때") {
            val jwt = Jwt
                .withTokenValue("access-token")
                .header("alg", "HS256")
                .subject("134")
                .claim("iss", "momogo-server-test")
                .build()
            SecurityContextHolder.getContext().authentication =
                JwtAuthenticationToken(jwt, emptyList())

            var userIdDuringChain: String? = null
            filter.doFilter(
                MockHttpServletRequest("GET", "/api/v1/user/me"),
                MockHttpServletResponse(),
                FilterChain { _, _ -> userIdDuringChain = MDC.get(LogContext.USER_ID_KEY) },
            )

            then("사용자 ID를 MDC에 넣어 동선을 추적할 수 있게 한다") {
                userIdDuringChain shouldBe "134"
            }
        }
    }

    given("인증되지 않은 요청이면") {
        `when`("가입이나 로그인처럼 공개 API를 호출할 때") {
            var userIdDuringChain: String? = null
            filter.doFilter(
                MockHttpServletRequest("POST", "/api/v1/user/register"),
                MockHttpServletResponse(),
                FilterChain { _, _ -> userIdDuringChain = MDC.get(LogContext.USER_ID_KEY) },
            )

            then("사용자 ID를 남기지 않는다") {
                userIdDuringChain shouldBe null
            }
        }
    }
})
