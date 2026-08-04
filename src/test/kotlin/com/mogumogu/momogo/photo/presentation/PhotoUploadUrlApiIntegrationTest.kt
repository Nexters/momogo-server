package com.mogumogu.momogo.photo.presentation

import com.mogumogu.momogo.global.error.ErrorCode
import com.mogumogu.momogo.photo.domain.PhotoObjectKey
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.engine.concurrency.TestExecutionMode
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ApplyExtension(SpringExtension::class)
class PhotoUploadUrlApiIntegrationTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) : BehaviorSpec({
    testExecutionMode = TestExecutionMode.Sequential

    fun json(value: Any): String = objectMapper.writeValueAsString(value)

    fun performJson(
        request: MockHttpServletRequestBuilder,
        content: String,
    ): MockHttpServletResponse =
        mockMvc.perform(
            request
                .contentType(MediaType.APPLICATION_JSON)
                .content(content),
        ).andReturn().response

    fun register(providerToken: String): RegisteredUserFixture {
        val response = performJson(
            post("/api/v1/user/register"),
            json(
                mapOf(
                    "provider" to "GUEST",
                    "providerToken" to providerToken,
                    "nickname" to "모모",
                ),
            ),
        )
        response.status shouldBe HttpStatus.OK.value()
        val body = objectMapper.readTree(response.contentAsString)

        return RegisteredUserFixture(
            userId = body["userId"].longValue(),
            accessToken = body["accessToken"].stringValue(),
        )
    }

    fun issueUploadUrl(
        accessToken: String?,
        content: String,
    ): MockHttpServletResponse {
        val request = post("/api/v1/photos/upload-urls")
        if (accessToken != null) {
            request.header("Authorization", "Bearer $accessToken")
        }
        return performJson(request, content)
    }

    fun withdraw(accessToken: String): MockHttpServletResponse =
        mockMvc.perform(
            delete("/api/v1/user")
                .header("Authorization", "Bearer $accessToken"),
        ).andReturn().response

    fun assertProblem(
        response: MockHttpServletResponse,
        status: HttpStatus,
        errorCode: ErrorCode,
    ) {
        response.status shouldBe status.value()
        response.contentType shouldBe MediaType.APPLICATION_PROBLEM_JSON_VALUE
        val body = objectMapper.readTree(response.contentAsString)
        body["status"].intValue() shouldBe status.value()
        body["detail"].stringValue() shouldBe errorCode.message
        body["code"].stringValue() shouldBe errorCode.name
    }

    given("인증된 사용자가 이미지 MIME 타입을 입력하면") {
        `when`("사진 업로드 URL을 발급할 때") {
            then("R2 직접 업로드에 필요한 URL과 오브젝트 키를 반환한다") {
                val registeredUser = register("photo-upload-url-success")
                val issuedAtEarliest = LocalDateTime.now(clock)

                val response = issueUploadUrl(
                    accessToken = registeredUser.accessToken,
                    content = json(mapOf("contentType" to "IMAGE/PNG")),
                )

                val issuedAtLatest = LocalDateTime.now(clock)
                response.status shouldBe HttpStatus.OK.value()
                response.contentType shouldBe MediaType.APPLICATION_JSON_VALUE
                val body = objectMapper.readTree(response.contentAsString)
                body.propertyNames().toSet() shouldBe
                    setOf("uploadUrl", "objectKey", "contentType", "expiresAt")

                val objectKey = PhotoObjectKey.parse(body["objectKey"].stringValue())
                objectKey.belongsTo("test", registeredUser.userId) shouldBe true
                objectKey.uploadDate shouldBe LocalDate.now(clock)
                objectKey.extension shouldBe "png"

                body["contentType"].stringValue() shouldBe "image/png"

                val uploadUrl = URI(body["uploadUrl"].stringValue())
                uploadUrl.path shouldBe "/momogo-test/${objectKey.value}"
                val queryParameters = uploadUrl.queryParameters()
                queryParameters.getValue("X-Amz-Expires") shouldBe "900"
                queryParameters.getValue("X-Amz-SignedHeaders")
                    .split(";")
                    .toSet() shouldBe setOf("content-type", "host")

                val expiresAt = LocalDateTime.parse(body["expiresAt"].stringValue())
                expiresAt.isBefore(issuedAtEarliest.plusMinutes(15).minusSeconds(1)) shouldBe false
                expiresAt.isAfter(issuedAtLatest.plusMinutes(15).plusSeconds(1)) shouldBe false
            }
        }
    }

    given("이미지가 아닌 MIME 타입이 있으면") {
        `when`("사진 업로드 URL을 발급할 때") {
            then("INVALID_CONTENT_TYPE 오류를 반환한다") {
                val registeredUser = register("photo-upload-url-invalid-content-type")

                val response = issueUploadUrl(
                    accessToken = registeredUser.accessToken,
                    content = json(mapOf("contentType" to "application/pdf")),
                )

                assertProblem(response, HttpStatus.BAD_REQUEST, ErrorCode.INVALID_CONTENT_TYPE)
            }
        }
    }

    given("contentType이 비어 있으면") {
        `when`("사진 업로드 URL을 발급할 때") {
            then("INVALID_REQUEST 오류를 반환한다") {
                val registeredUser = register("photo-upload-url-blank-content-type")

                val response = issueUploadUrl(
                    accessToken = registeredUser.accessToken,
                    content = json(mapOf("contentType" to " ")),
                )

                assertProblem(response, HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST)
            }
        }
    }

    given("인증 정보가 없으면") {
        `when`("사진 업로드 URL을 발급할 때") {
            then("401 오류를 반환한다") {
                val response = issueUploadUrl(
                    accessToken = null,
                    content = json(mapOf("contentType" to "image/webp")),
                )

                assertProblem(response, HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_AUTH_CREDENTIALS)
            }
        }
    }

    given("액세스 토큰의 사용자가 탈퇴했으면") {
        `when`("사진 업로드 URL을 발급할 때") {
            then("USER_NOT_FOUND 오류를 반환한다") {
                val registeredUser = register("photo-upload-url-deleted-user")
                withdraw(registeredUser.accessToken).status shouldBe HttpStatus.OK.value()

                val response = issueUploadUrl(
                    accessToken = registeredUser.accessToken,
                    content = json(mapOf("contentType" to "image/webp")),
                )

                assertProblem(response, HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND)
            }
        }
    }
})

private data class RegisteredUserFixture(
    val userId: Long,
    val accessToken: String,
)

private fun URI.queryParameters(): Map<String, String> =
    rawQuery
        .split("&")
        .associate { parameter ->
            val (name, value) = parameter.split("=", limit = 2)
            URLDecoder.decode(name, StandardCharsets.UTF_8) to
                URLDecoder.decode(value, StandardCharsets.UTF_8)
        }
