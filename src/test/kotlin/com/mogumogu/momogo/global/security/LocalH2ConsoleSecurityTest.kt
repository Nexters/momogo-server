package com.mogumogu.momogo.global.security

import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

@SpringBootTest(
    properties = [
        "momogo.security.jwt.issuer=momogo-local-security-test",
        "momogo.security.jwt.secret-base64=bW9tb2dvLWxvY2FsLXNlY3VyaXR5LXRlc3Qtc2VjcmV0LTMyaA==",
    ],
)
@AutoConfigureMockMvc
@ActiveProfiles("local")
@ApplyExtension(SpringExtension::class)
class LocalH2ConsoleSecurityTest(
    private val mockMvc: MockMvc,
) : BehaviorSpec({

    given("local 프로필에서 H2 console 경로를 요청하면") {
        `when`("인증 없이 접근할 때") {
            val response = mockMvc.perform(get("/h2-console/"))
                .andReturn()
                .response

            then("인증을 요구하지 않고 frame을 local same-origin으로 제한한다") {
                response.status shouldNotBe 401
                response.getHeader("X-Frame-Options") shouldBe "SAMEORIGIN"
            }
        }
    }
})
