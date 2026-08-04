package com.mogumogu.momogo.global.security

import com.mogumogu.momogo.global.error.ErrorCode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.json.ProblemDetailJacksonMixin
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import tools.jackson.databind.json.JsonMapper

class SecurityProblemDetailHandlerTest : BehaviorSpec({

    val objectMapper = JsonMapper
        .builder()
        .addMixIn(ProblemDetail::class.java, ProblemDetailJacksonMixin::class.java)
        .build()
    val writer = SecurityProblemDetailWriter(objectMapper)

    given("인증에 실패하면") {
        val entryPoint = ProblemDetailAuthenticationEntryPoint(writer)
        val request = MockHttpServletRequest("PATCH", "/api/v1/user")
        val response = MockHttpServletResponse()

        `when`("내부 JWT 오류에 민감한 원문이 들어 있어도") {
            entryPoint.commence(
                request,
                response,
                BadCredentialsException("raw-token-and-parser-detail"),
            )

            val body = objectMapper.readTree(response.contentAsString)

            then("내부 정보를 숨긴 401 ProblemDetail을 반환한다") {
                response.status shouldBe 401
                response.contentType shouldBe MediaType.APPLICATION_PROBLEM_JSON_VALUE
                body["status"].intValue() shouldBe 401
                body["title"].stringValue() shouldBe "Unauthorized"
                body["detail"].stringValue() shouldBe ErrorCode.INVALID_AUTH_CREDENTIALS.message
                body["code"].stringValue() shouldBe ErrorCode.INVALID_AUTH_CREDENTIALS.name
                body["instance"].stringValue() shouldBe "/api/v1/user"
                response.contentAsString shouldNotContain "raw-token-and-parser-detail"
            }
        }
    }

    given("인증됐지만 접근 권한이 없으면") {
        val handler = ProblemDetailAccessDeniedHandler(writer)
        val request = MockHttpServletRequest("GET", "/admin")
        val response = MockHttpServletResponse()

        `when`("인가 예외가 발생할 때") {
            handler.handle(
                request,
                response,
                AccessDeniedException("sensitive-authorization-detail"),
            )

            val body = objectMapper.readTree(response.contentAsString)

            then("내부 정보를 숨긴 403 ProblemDetail을 반환한다") {
                response.status shouldBe 403
                response.contentType shouldBe MediaType.APPLICATION_PROBLEM_JSON_VALUE
                body["status"].intValue() shouldBe 403
                body["title"].stringValue() shouldBe "Forbidden"
                body["detail"].stringValue() shouldBe ErrorCode.FORBIDDEN.message
                body["code"].stringValue() shouldBe ErrorCode.FORBIDDEN.name
                body["instance"].stringValue() shouldBe "/admin"
                response.contentAsString shouldNotContain "sensitive-authorization-detail"
            }
        }
    }
})
