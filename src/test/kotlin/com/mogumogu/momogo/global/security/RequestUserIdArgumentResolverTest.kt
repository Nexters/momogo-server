package com.mogumogu.momogo.global.security

import com.mogumogu.momogo.global.error.ApiException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.core.MethodParameter
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.context.request.ServletWebRequest
import java.time.Instant

class RequestUserIdArgumentResolverTest : BehaviorSpec({

    val resolver = RequestUserIdArgumentResolver()
    val webRequest = ServletWebRequest(MockHttpServletRequest())

    afterTest {
        SecurityContextHolder.clearContext()
    }

    given("RequestUserId가 선언된 Long 파라미터가 있으면") {
        val parameter = methodParameter("requestUserId", requireNotNull(Long::class.javaPrimitiveType))

        `when`("Long sub를 가진 인증된 JWT가 있을 때") {
            SecurityContextHolder.getContext().authentication =
                JwtAuthenticationToken(jwt(subject = "42"), emptyList())

            then("JWT의 sub를 userId로 반환한다") {
                resolver.supportsParameter(parameter) shouldBe true
                resolver.resolveArgument(parameter, null, webRequest, null) shouldBe 42L
            }
        }

        `when`("인증 정보가 없을 때") {
            then("401 API 예외를 발생시킨다") {
                shouldThrow<ApiException.Unauthorized> {
                    resolver.resolveArgument(parameter, null, webRequest, null)
                }.statusCode.value() shouldBe 401
            }
        }

        `when`("JWT sub가 Long이 아닐 때") {
            SecurityContextHolder.getContext().authentication =
                JwtAuthenticationToken(jwt(subject = "not-a-long"), emptyList())

            then("401 API 예외를 발생시킨다") {
                shouldThrow<ApiException.Unauthorized> {
                    resolver.resolveArgument(parameter, null, webRequest, null)
                }.statusCode.value() shouldBe 401
            }
        }
    }

    given("지원하지 않는 컨트롤러 파라미터가 있으면") {
        then("RequestUserId가 없거나 Long 타입이 아니면 처리하지 않는다") {
            resolver.supportsParameter(
                methodParameter("plainUserId", requireNotNull(Long::class.javaPrimitiveType)),
            ) shouldBe false
            resolver.supportsParameter(
                methodParameter("stringUserId", String::class.java),
            ) shouldBe false
        }
    }
})

private fun methodParameter(
    methodName: String,
    parameterType: Class<*>,
): MethodParameter =
    MethodParameter(
        RequestUserIdTestController::class.java.getDeclaredMethod(methodName, parameterType),
        0,
    )

private fun jwt(subject: String): Jwt {
    val now = Instant.parse("2030-01-01T00:00:00Z")
    return Jwt
        .withTokenValue("access-token")
        .header("alg", "HS256")
        .subject(subject)
        .issuedAt(now)
        .expiresAt(now.plusSeconds(60))
        .build()
}

private class RequestUserIdTestController {

    fun requestUserId(@RequestUserId userId: Long) = userId

    fun plainUserId(userId: Long) = userId

    fun stringUserId(@RequestUserId userId: String) = userId
}
