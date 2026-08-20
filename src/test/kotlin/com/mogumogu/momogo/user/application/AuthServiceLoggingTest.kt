package com.mogumogu.momogo.user.application

import ch.qos.logback.classic.Level
import com.mogumogu.momogo.global.logging.LogFingerprint
import com.mogumogu.momogo.global.logging.captureLogs
import com.mogumogu.momogo.global.logging.messagesAt
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.engine.concurrency.TestExecutionMode
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import tools.jackson.databind.ObjectMapper

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ApplyExtension(SpringExtension::class)
class AuthServiceLoggingTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
) : BehaviorSpec({
    testExecutionMode = TestExecutionMode.Sequential

    fun performJson(
        request: MockHttpServletRequestBuilder,
        content: String,
    ): MockHttpServletResponse =
        mockMvc.perform(
            request
                .contentType(MediaType.APPLICATION_JSON)
                .content(content),
        ).andReturn().response

    fun register(providerToken: String): MockHttpServletResponse =
        performJson(
            post("/api/v1/user/register"),
            objectMapper.writeValueAsString(
                mapOf(
                    "provider" to "GUEST",
                    "providerToken" to providerToken,
                    "nickname" to "모모",
                ),
            ),
        )

    fun login(providerToken: String): MockHttpServletResponse =
        performJson(
            post("/api/v1/auth/login"),
            objectMapper.writeValueAsString(
                mapOf(
                    "provider" to "GUEST",
                    "providerToken" to providerToken,
                ),
            ),
        )

    fun reissue(refreshToken: String): MockHttpServletResponse =
        performJson(
            post("/api/v1/auth/reissue"),
            objectMapper.writeValueAsString(mapOf("refreshToken" to refreshToken)),
        )

    given("클라이언트가 가입과 로그인에 다른 게스트 토큰을 보내면") {
        val registeredToken = "9BEA747F-B40E-481B-A2B4-A925C861B10F"
        val loginToken = "29EC4D44-0688-4472-A771-021C5B7A3E27"

        `when`("가입한 뒤 다른 토큰으로 로그인할 때") {
            val logs = captureLogs(AuthService::class.java) {
                register(registeredToken).status shouldBe 200
                login(loginToken).status shouldBe 404
            }

            then("가입 성공 로그에 사용한 토큰의 지문을 남긴다") {
                logs.messagesAt(Level.INFO).single { it.startsWith("가입 성공") } shouldContain
                    "providerIdFingerprint=${LogFingerprint.of(registeredToken)}"
            }

            then("로그인 실패 로그에 계정 없음과 시도한 토큰의 지문을 남긴다") {
                val message = logs.messagesAt(Level.WARN).single { it.startsWith("로그인 실패") }

                message shouldContain "providerIdFingerprint=${LogFingerprint.of(loginToken)}"
                message shouldContain "reason=로그인 계정 없음"
            }

            then("두 지문이 달라 클라이언트가 토큰을 유지하지 못한 것을 알 수 있다") {
                LogFingerprint.of(registeredToken) shouldNotBe LogFingerprint.of(loginToken)
            }
        }
    }

    given("토큰 재발급이 실패하면") {
        `when`("저장되지 않은 리프레시 토큰을 보낼 때") {
            val logs = captureLogs(AuthService::class.java) {
                reissue("saved-nowhere-refresh-token").status shouldBe 404
            }

            then("같은 404 안에서도 원인을 구분해 남긴다") {
                logs.messagesAt(Level.WARN)
                    .single { it.startsWith("토큰 재발급 실패") } shouldContain
                    "reason=저장되지 않은 토큰"
            }
        }

        `when`("이미 재발급에 사용한 토큰을 다시 보낼 때") {
            val issuedRefreshToken = objectMapper
                .readTree(register("EEB6B8A4-6B93-4B58-9F44-0F1F9F6C2E11").contentAsString)
                .get("refreshToken")
                .stringValue()

            reissue(issuedRefreshToken).status shouldBe 200

            val logs = captureLogs(AuthService::class.java) {
                reissue(issuedRefreshToken).status shouldBe 404
            }

            then("폐기된 토큰임을 남겨 클라이언트가 회전된 토큰을 저장하지 못한 것을 알 수 있다") {
                val message = logs.messagesAt(Level.WARN)
                    .single { it.startsWith("토큰 재발급 실패") }

                message shouldContain "reason=이미 폐기된 토큰"
                message shouldContain "refreshTokenFingerprint="
            }
        }
    }
})
