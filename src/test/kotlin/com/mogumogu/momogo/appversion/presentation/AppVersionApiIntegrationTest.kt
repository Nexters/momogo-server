package com.mogumogu.momogo.appversion.presentation

import com.mogumogu.momogo.global.error.ErrorCode
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import tools.jackson.databind.ObjectMapper

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ApplyExtension(SpringExtension::class)
class AppVersionApiIntegrationTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
) : BehaviorSpec({

    fun checkVersion(
        platform: String,
        appVersion: String,
    ): MockHttpServletResponse =
        mockMvc.perform(
            get("/init/versions")
                .queryParam("platform", platform)
                .queryParam("appVersion", appVersion),
        ).andReturn().response

    given("인증 정보가 없는 IOS 앱이면") {
        `when`("현재 버전이 최소 지원 버전보다 낮을 때") {
            val response = checkVersion(
                platform = "IOS",
                appVersion = "0.9.9",
            )
            val body = objectMapper.readTree(response.contentAsString)

            then("IOS 정책과 강제 업데이트 여부를 반환한다") {
                response.status shouldBe HttpStatus.OK.value()
                response.contentType shouldBe MediaType.APPLICATION_JSON_VALUE
                body.propertyNames().toSet() shouldBe setOf(
                    "latestVersion",
                    "minSupportedVersion",
                    "forceUpdate",
                    "updateUrl",
                )
                body["latestVersion"].stringValue() shouldBe "1.0.0"
                body["minSupportedVersion"].stringValue() shouldBe "1.0.0"
                body["forceUpdate"].booleanValue() shouldBe true
                body["updateUrl"].stringValue() shouldBe
                    "https://apps.apple.com/app/id000000000"
            }
        }

        `when`("현재 버전이 최소 지원 버전과 같거나 높을 때") {
            then("강제 업데이트가 필요하지 않다고 반환한다") {
                listOf("1.0.0", "1.1.0", "2.0.0").forEach { appVersion ->
                    val body = objectMapper.readTree(
                        checkVersion("IOS", appVersion).contentAsString,
                    )

                    body["forceUpdate"].booleanValue() shouldBe false
                }
            }
        }
    }

    given("ANDROID 앱이면") {
        `when`("버전 체크를 요청할 때") {
            val response = checkVersion("ANDROID", "0.9.9")
            val body = objectMapper.readTree(response.contentAsString)

            then("ANDROID 정책으로 판단하고 Android 스토어 URL을 반환한다") {
                response.status shouldBe HttpStatus.OK.value()
                body["latestVersion"].stringValue() shouldBe "1.0.0"
                body["minSupportedVersion"].stringValue() shouldBe "1.0.0"
                body["forceUpdate"].booleanValue() shouldBe true
                body["updateUrl"].stringValue() shouldBe
                    "https://play.google.com/store/apps/details?id=com.mogumogu.momogo"
            }
        }
    }

    given("지원하지 않는 플랫폼이면") {
        `when`("버전 체크를 요청할 때") {
            val response = checkVersion("WEB", "1.2.0")
            val body = objectMapper.readTree(response.contentAsString)

            then("INVALID_PLATFORM 코드가 있는 400 ProblemDetail을 반환한다") {
                response.status shouldBe HttpStatus.BAD_REQUEST.value()
                response.contentType shouldBe MediaType.APPLICATION_PROBLEM_JSON_VALUE
                body["status"].intValue() shouldBe HttpStatus.BAD_REQUEST.value()
                body["detail"].stringValue() shouldBe ErrorCode.INVALID_PLATFORM.message
                body["code"].stringValue() shouldBe ErrorCode.INVALID_PLATFORM.name
            }
        }
    }

    given("SemVer 형식이 아닌 앱 버전이면") {
        `when`("버전 체크를 요청할 때") {
            val response = checkVersion("IOS", "1.2")
            val body = objectMapper.readTree(response.contentAsString)

            then("400 ProblemDetail을 반환한다") {
                response.status shouldBe HttpStatus.BAD_REQUEST.value()
                response.contentType shouldBe MediaType.APPLICATION_PROBLEM_JSON_VALUE
                body["detail"].stringValue() shouldBe ErrorCode.INVALID_REQUEST.message
            }
        }
    }

    given("필수 앱 버전 쿼리 파라미터가 없으면") {
        `when`("플랫폼만 전달해 버전 체크를 요청할 때") {
            val response = mockMvc.perform(
                get("/init/versions")
                    .queryParam("platform", "IOS"),
            ).andReturn().response
            val body = objectMapper.readTree(response.contentAsString)

            then("Spring 내부 메시지를 숨긴 INVALID_REQUEST ProblemDetail을 반환한다") {
                response.status shouldBe HttpStatus.BAD_REQUEST.value()
                response.contentType shouldBe MediaType.APPLICATION_PROBLEM_JSON_VALUE
                body["status"].intValue() shouldBe HttpStatus.BAD_REQUEST.value()
                body["detail"].stringValue() shouldBe ErrorCode.INVALID_REQUEST.message
                body["instance"].stringValue() shouldBe "/init/versions"
                body["code"].stringValue() shouldBe ErrorCode.INVALID_REQUEST.name
            }
        }
    }
})
