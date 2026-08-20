package com.mogumogu.momogo.global.logging

import ch.qos.logback.classic.Level
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldMatch
import jakarta.servlet.FilterChain
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class RequestLogFilterTest : BehaviorSpec({

    val filter = RequestLogFilter()

    given("요청 식별자 헤더가 없으면") {
        val request = MockHttpServletRequest("GET", "/api/v1/groups")
        val response = MockHttpServletResponse()
        var requestIdDuringChain: String? = null

        filter.doFilter(request, response, FilterChain { _, _ ->
            requestIdDuringChain = MDC.get(LogContext.REQUEST_ID_KEY)
        })

        `when`("요청을 처리할 때") {
            then("식별자를 생성해 MDC에 넣는다") {
                requestIdDuringChain shouldNotBe null
                requestIdDuringChain!! shouldMatch Regex("^[0-9a-f]{8}$")
            }

            then("클라이언트가 대조할 수 있도록 응답 헤더로도 내려준다") {
                response.getHeader(LogContext.REQUEST_ID_HEADER) shouldBe requestIdDuringChain
            }

            then("처리가 끝나면 MDC를 정리한다") {
                MDC.get(LogContext.REQUEST_ID_KEY) shouldBe null
                MDC.get(LogContext.USER_ID_KEY) shouldBe null
            }
        }
    }

    given("허용한 형식의 요청 식별자 헤더가 오면") {
        val request = MockHttpServletRequest("GET", "/api/v1/groups").apply {
            addHeader(LogContext.REQUEST_ID_HEADER, "client-request-1")
        }
        val response = MockHttpServletResponse()
        var requestIdDuringChain: String? = null

        filter.doFilter(request, response, FilterChain { _, _ ->
            requestIdDuringChain = MDC.get(LogContext.REQUEST_ID_KEY)
        })

        `when`("요청을 처리할 때") {
            then("클라이언트가 보낸 값을 그대로 사용한다") {
                requestIdDuringChain shouldBe "client-request-1"
            }
        }
    }

    given("로그를 위조하려는 요청 식별자 헤더가 오면") {
        val request = MockHttpServletRequest("GET", "/api/v1/groups").apply {
            addHeader(LogContext.REQUEST_ID_HEADER, "fake\n INFO 위조된 로그 줄")
        }
        val response = MockHttpServletResponse()
        var requestIdDuringChain: String? = null

        filter.doFilter(request, response, FilterChain { _, _ ->
            requestIdDuringChain = MDC.get(LogContext.REQUEST_ID_KEY)
        })

        `when`("요청을 처리할 때") {
            then("헤더를 무시하고 새 식별자를 생성한다") {
                requestIdDuringChain!! shouldMatch Regex("^[0-9a-f]{8}$")
            }
        }
    }

    given("요청 처리가 끝나면") {
        `when`("정상 응답일 때") {
            val request = MockHttpServletRequest("POST", "/api/v1/user/register")
            val response = MockHttpServletResponse()
            val logs = captureLogs(RequestLogFilter::class.java) {
                filter.doFilter(request, response, FilterChain { _, res ->
                    (res as MockHttpServletResponse).status = 200
                })
            }

            then("메서드와 경로, 상태, 소요 시간을 액세스 로그로 남긴다") {
                val message = logs.messagesAt(Level.INFO).single()

                message shouldContain "method=POST"
                message shouldContain "path=/api/v1/user/register"
                message shouldContain "status=200"
                message shouldContain "durationMs="
            }
        }

        `when`("처리 중 예외가 발생할 때") {
            val request = MockHttpServletRequest("GET", "/api/v1/groups")
            val response = MockHttpServletResponse()

            then("예외를 그대로 전파하면서 MDC는 정리한다") {
                shouldThrow<IllegalStateException> {
                    filter.doFilter(request, response, FilterChain { _, _ ->
                        throw IllegalStateException("처리 실패")
                    })
                }

                MDC.get(LogContext.REQUEST_ID_KEY) shouldBe null
                MDC.get(LogContext.USER_ID_KEY) shouldBe null
            }
        }
    }
})
