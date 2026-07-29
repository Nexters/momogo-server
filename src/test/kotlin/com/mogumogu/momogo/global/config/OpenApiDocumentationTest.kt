package com.mogumogu.momogo.global.config

import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

@SpringBootTest(
    properties = [
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true",
        "momogo.openapi.server-url=https://api.dev.mogumogo.com",
    ],
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ApplyExtension(SpringExtension::class)
class OpenApiDocumentationTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
) : BehaviorSpec({

    given("Swagger 문서가 활성화된 상태에서") {
        `when`("OpenAPI 문서를 요청하면") {
            val response = mockMvc.perform(get("/v3/api-docs"))
                .andReturn()
                .response
            val document = objectMapper.readTree(response.contentAsString)

            then("기본 정보와 Bearer JWT 인증 방식을 반환한다") {
                response.status shouldBe HttpStatus.OK.value()
                document["info"]["title"].stringValue() shouldBe "Momogo API"
                document["info"]["description"].stringValue() shouldBe "Momogo 서버 API 문서"
                document["info"]["version"].stringValue() shouldBe "0.0.1-SNAPSHOT"
                document["servers"][0]["url"].stringValue() shouldBe "https://api.dev.mogumogo.com"

                val bearerAuth = document["components"]["securitySchemes"][
                    OpenApiConfiguration.BEARER_AUTH
                ]
                bearerAuth["type"].stringValue() shouldBe "http"
                bearerAuth["scheme"].stringValue() shouldBe "bearer"
                bearerAuth["bearerFormat"].stringValue() shouldBe "JWT"
                bearerAuth["description"].stringValue() shouldBe
                    "회원가입 또는 로그인 후 받은 액세스 토큰을 입력합니다."
            }

            then("보호된 사용자 API에만 Bearer 인증을 표시한다") {
                document.operation("/api/v1/user", "patch").requiresBearerAuth() shouldBe true
                document.operation("/api/v1/user", "delete").requiresBearerAuth() shouldBe true
                document.operation("/api/v1/user/register", "post").requiresBearerAuth() shouldBe false
                document.operation("/api/v1/auth/login", "post").requiresBearerAuth() shouldBe false
                document.operation("/api/v1/auth/reissue", "post").requiresBearerAuth() shouldBe false
                document.operation("/api/v1/auth/logout", "delete").requiresBearerAuth() shouldBe false
            }

            then("Bearer 인증 API에 공통 401 응답을 적용한다") {
                val commonResponseRef =
                    "#/components/responses/${OpenApiConfiguration.BEARER_UNAUTHORIZED_RESPONSE}"

                document.operation("/api/v1/user", "patch")["responses"]["401"]["\$ref"]
                    .stringValue() shouldBe commonResponseRef
                document.operation("/api/v1/user", "delete")["responses"]["401"]["\$ref"]
                    .stringValue() shouldBe commonResponseRef

                val commonResponse = document["components"]["responses"][
                    OpenApiConfiguration.BEARER_UNAUTHORIZED_RESPONSE
                ]
                commonResponse["description"].stringValue() shouldBe
                    "액세스 토큰이 없거나 유효하지 않음"
                commonResponse["content"].has("application/problem+json") shouldBe true
            }

            then("서버가 주입하는 userId를 요청 파라미터로 노출하지 않는다") {
                document.operation("/api/v1/user", "patch").hasParameter("userId") shouldBe false
                document.operation("/api/v1/user", "delete").hasParameter("userId") shouldBe false
            }

            then("user와 auth API의 설명과 주요 응답을 제공한다") {
                val register = document.operation("/api/v1/user/register", "post")
                val login = document.operation("/api/v1/auth/login", "post")
                val reissue = document.operation("/api/v1/auth/reissue", "post")
                val logout = document.operation("/api/v1/auth/logout", "delete")
                val updateNickname = document.operation("/api/v1/user", "patch")
                val withdraw = document.operation("/api/v1/user", "delete")

                register["summary"].stringValue() shouldBe "회원가입"
                register.responseCodes() shouldBe setOf("200", "400", "409")
                register.hasResponseMediaType("200", "application/json") shouldBe true
                register.hasResponseMediaType("400", "application/problem+json") shouldBe true
                login["summary"].stringValue() shouldBe "로그인"
                login.responseCodes() shouldBe setOf("200", "400", "401")
                login.hasResponseMediaType("401", "application/problem+json") shouldBe true
                reissue["summary"].stringValue() shouldBe "토큰 재발급"
                reissue.responseCodes() shouldBe setOf("200", "400", "401")
                logout["summary"].stringValue() shouldBe "로그아웃"
                logout.responseCodes() shouldBe setOf("200", "400")
                updateNickname["summary"].stringValue() shouldBe "닉네임 변경"
                updateNickname.responseCodes() shouldBe setOf("200", "400", "401", "404")
                withdraw["summary"].stringValue() shouldBe "회원 탈퇴"
                withdraw.responseCodes() shouldBe setOf("200", "401", "404")
            }

            then("요청 스키마에 현재 지원 범위와 입력 제한을 표시한다") {
                val schemas = document["components"]["schemas"]
                schemas["LoginRequest"]["properties"]["provider"]["enum"]
                    .stringValues() shouldBe listOf("GUEST")
                schemas["RegisterRequest"]["properties"]["provider"]["enum"]
                    .stringValues() shouldBe listOf("GUEST")
                schemas["RegisterRequest"]["properties"]["providerToken"]["maxLength"]
                    .intValue() shouldBe 255
                schemas["RegisterRequest"]["properties"]["providerToken"]["minLength"]
                    .intValue() shouldBe 1
                schemas["RegisterRequest"]["properties"]["nickname"]["maxLength"]
                    .intValue() shouldBe 12
                schemas["RegisterRequest"]["required"].stringValues().toSet() shouldBe
                    setOf("provider", "providerToken", "nickname")
                schemas["LoginRequest"]["required"].stringValues().toSet() shouldBe
                    setOf("provider", "providerToken")
                schemas["AuthResponse"]["required"].stringValues().toSet() shouldBe
                    setOf("userId", "nickname", "accessToken", "refreshToken")
                schemas["ReissueResponse"]["required"].stringValues().toSet() shouldBe
                    setOf("accessToken", "refreshToken")
                schemas["UserResponse"]["required"].stringValues().toSet() shouldBe
                    setOf("userId", "nickname")
            }
        }

        `when`("Swagger UI 진입 경로를 요청하면") {
            val response = mockMvc.perform(get("/swagger-ui.html"))
                .andReturn()
                .response

            then("Swagger UI 화면으로 이동한다") {
                response.status shouldBe HttpStatus.FOUND.value()
                response.redirectedUrl shouldBe "/swagger-ui/index.html"
            }
        }
    }
})

private fun JsonNode.operation(
    path: String,
    method: String,
): JsonNode = this["paths"][path][method]

private fun JsonNode.requiresBearerAuth(): Boolean =
    this["security"]
        ?.any { requirement -> requirement.has(OpenApiConfiguration.BEARER_AUTH) }
        ?: false

private fun JsonNode.hasParameter(name: String): Boolean =
    this["parameters"]
        ?.any { parameter -> parameter["name"]?.stringValue() == name }
        ?: false

private fun JsonNode.responseCodes(): Set<String> =
    this["responses"].propertyNames().asSequence().toSet()

private fun JsonNode.hasResponseMediaType(
    responseCode: String,
    mediaType: String,
): Boolean =
    this["responses"][responseCode]["content"].has(mediaType)

private fun JsonNode.stringValues(): List<String> =
    values().map { value -> value.stringValue() }
