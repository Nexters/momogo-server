package com.mogumogu.momogo.global.error

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.json.JsonMapper

class GlobalExceptionHandlerTest : BehaviorSpec({

    val validator = LocalValidatorFactoryBean().apply { afterPropertiesSet() }
    val mockMvc = MockMvcBuilders
        .standaloneSetup(TestController())
        .setControllerAdvice(GlobalExceptionHandler())
        .setValidator(validator)
        .build()
    val objectMapper = JsonMapper.builder().build()

    given("API 예외 종류가 다르면") {
        val notFound: ApiException = ApiException.NotFound(ErrorCode.RESOURCE_NOT_FOUND)
        val badRequest: ApiException = ApiException.BadRequest(ErrorCode.INVALID_REQUEST)

        then("서로 다른 런타임 타입으로 구분할 수 있다") {
            (notFound is ApiException.NotFound) shouldBe true
            (notFound is ApiException.BadRequest) shouldBe false
            (badRequest is ApiException.BadRequest) shouldBe true
            (badRequest is ApiException.NotFound) shouldBe false
        }
    }

    given("API 예외가 발생하면") {
        `when`("notFound 팩토리로 예외를 생성할 때") {
            val response = mockMvc.perform(get("/test/api-exception"))
                .andReturn()
                .response
            val body = objectMapper.readTree(response.contentAsString)

            then("404 ProblemDetail로 응답한다") {
                response.status shouldBe 404
                response.contentType shouldBe MediaType.APPLICATION_PROBLEM_JSON_VALUE
                body["status"].intValue() shouldBe 404
                body["detail"].stringValue() shouldBe "요청한 리소스를 찾을 수 없습니다."
                body["instance"].stringValue() shouldBe "/test/api-exception"
                body.has("code") shouldBe false
            }
        }
    }

    given("요청 DTO 검증이 실패하면") {
        `when`("빈 이름을 전달할 때") {
            val response = mockMvc.perform(
                post("/test/validation")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":""}"""),
            ).andReturn().response
            val body = objectMapper.readTree(response.contentAsString)

            then("필드 에러를 포함한 400 ProblemDetail로 응답한다") {
                response.status shouldBe 400
                response.contentType shouldBe MediaType.APPLICATION_PROBLEM_JSON_VALUE
                body["errors"][0]["field"].stringValue() shouldBe "name"
                body["errors"][0]["message"].stringValue() shouldBe "이름은 비어 있을 수 없습니다."
                body["instance"].stringValue() shouldBe "/test/validation"
                body.has("code") shouldBe false
            }
        }
    }

    given("Spring MVC 요청 파싱 예외가 발생하면") {
        `when`("잘못된 JSON을 전달할 때") {
            val response = mockMvc.perform(
                post("/test/validation")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":}"""),
            ).andReturn().response
            val body = objectMapper.readTree(response.contentAsString)

            then("표준 필드를 포함한 400 ProblemDetail로 응답한다") {
                response.status shouldBe 400
                response.contentType shouldBe MediaType.APPLICATION_PROBLEM_JSON_VALUE
                body["status"].intValue() shouldBe 400
                body["instance"].stringValue() shouldBe "/test/validation"
                body.has("code") shouldBe false
            }
        }
    }

    given("IllegalArgumentException이 발생하면") {
        `when`("예상하지 못한 내부 예외로 처리할 때") {
            val response = mockMvc.perform(get("/test/illegal-argument"))
                .andReturn()
                .response
            val body = objectMapper.readTree(response.contentAsString)

            then("내부 메시지를 숨기고 500으로 응답한다") {
                response.status shouldBe 500
                response.contentType shouldBe MediaType.APPLICATION_PROBLEM_JSON_VALUE
                body["detail"].stringValue() shouldBe "서버 내부 오류가 발생했습니다."
                body["instance"].stringValue() shouldBe "/test/illegal-argument"
                body.has("code") shouldBe false
            }
        }
    }
}) {
    @RestController
    @RequestMapping("/test")
    private class TestController {

        @GetMapping("/api-exception")
        fun apiException(): Nothing =
            throw ApiException.NotFound(ErrorCode.RESOURCE_NOT_FOUND)

        @PostMapping("/validation")
        fun validation(@Valid @RequestBody request: TestRequest) = request

        @GetMapping("/illegal-argument")
        fun illegalArgument(): Nothing =
            throw IllegalArgumentException("민감한 내부 메시지")
    }

    private data class TestRequest(
        @field:NotBlank(message = "이름은 비어 있을 수 없습니다.")
        val name: String,
    )
}
