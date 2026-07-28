package com.mogumogu.momogo.global.security

import com.mogumogu.momogo.global.error.ErrorCode
import com.mogumogu.momogo.global.token.JwtProperties
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import tools.jackson.databind.ObjectMapper
import java.time.Instant

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ApplyExtension(SpringExtension::class)
class SecurityFilterChainTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val jwtEncoder: JwtEncoder,
    private val jwtProperties: JwtProperties,
) : BehaviorSpec({

    given("인증이 필요한 엔드포인트가 있으면") {
        `when`("access token 없이 요청할 때") {
            val result = mockMvc.perform(
                patch("/api/v1/user")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"nickname":"새닉네임"}"""),
            ).andReturn()
            val response = result.response
            val body = objectMapper.readTree(response.contentAsString)

            then("세션을 만들지 않고 401 ProblemDetail을 반환한다") {
                response.status shouldBe 401
                response.contentType shouldBe MediaType.APPLICATION_PROBLEM_JSON_VALUE
                response.getHeader(HttpHeaders.WWW_AUTHENTICATE) shouldBe "Bearer"
                body["status"].intValue() shouldBe 401
                body["detail"].stringValue() shouldBe ErrorCode.INVALID_AUTH_CREDENTIALS.message
                body["instance"].stringValue() shouldBe "/api/v1/user"
                result.request.getSession(false) shouldBe null
            }
        }

        `when`("파싱할 수 없는 Bearer token으로 요청할 때") {
            val response = mockMvc.perform(
                patch("/api/v1/user")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer raw.invalid.token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"nickname":"새닉네임"}"""),
            ).andReturn().response

            then("JWT 파싱 오류나 토큰 원문을 노출하지 않는다") {
                response.status shouldBe 401
                response.contentType shouldBe MediaType.APPLICATION_PROBLEM_JSON_VALUE
                response.contentAsString shouldNotContain "raw.invalid.token"
            }
        }

        `when`("Long으로 변환할 수 없는 sub가 서명된 token으로 요청할 때") {
            val now = Instant.now()
            val token = encode(
                subject = "not-a-long",
                issuer = jwtProperties.issuer,
                issuedAt = now,
                expiresAt = now.plusSeconds(60),
                jwtEncoder = jwtEncoder,
            )
            val response = mockMvc.perform(
                patch("/api/v1/user")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"nickname":"새닉네임"}"""),
            ).andReturn().response

            then("컨트롤러에 도달하기 전에 401로 거부한다") {
                response.status shouldBe 401
                response.contentType shouldBe MediaType.APPLICATION_PROBLEM_JSON_VALUE
                response.contentAsString shouldNotContain token
            }
        }
    }

    given("공개 엔드포인트와 같은 경로가 있으면") {
        `when`("허용하지 않은 HTTP 메서드로 요청할 때") {
            val response = mockMvc.perform(get("/api/v1/auth/login"))
                .andReturn()
                .response

            then("경로만으로 공개하지 않고 인증을 요구한다") {
                response.status shouldBe 401
            }
        }
    }

    given("local이 아닌 프로필에서 H2 console 경로를 요청하면") {
        `when`("인증 없이 접근할 때") {
            val response = mockMvc.perform(get("/h2-console/"))
                .andReturn()
                .response

            then("local 전용 예외가 적용되지 않는다") {
                response.status shouldBe 401
            }
        }
    }
})

private fun encode(
    subject: String,
    issuer: String,
    issuedAt: Instant,
    expiresAt: Instant,
    jwtEncoder: JwtEncoder,
): String {
    val header = JwsHeader
        .with(MacAlgorithm.HS256)
        .type("JWT")
        .build()
    val claims = JwtClaimsSet
        .builder()
        .issuer(issuer)
        .subject(subject)
        .issuedAt(issuedAt)
        .expiresAt(expiresAt)
        .build()

    return jwtEncoder
        .encode(JwtEncoderParameters.from(header, claims))
        .tokenValue
}
