package com.mogumogu.momogo.global.error

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean
import org.springframework.web.bind.annotation.*
import tools.jackson.databind.json.JsonMapper
import java.sql.SQLException

class GlobalExceptionHandlerTest : BehaviorSpec({

    val validator = LocalValidatorFactoryBean().apply { afterPropertiesSet() }
    val mockMvc = MockMvcBuilders
        .standaloneSetup(TestController())
        .setControllerAdvice(GlobalExceptionHandler())
        .setValidator(validator)
        .build()
    val objectMapper = JsonMapper.builder().build()

    given("상태별 API 예외를 생성하면") {
        then("sealed 하위 타입의 상태와 ErrorCode의 메시지를 사용한다") {
            val expectations = listOf(
                ApiException.BadRequest(ErrorCode.INVALID_REQUEST) to HttpStatus.BAD_REQUEST,
                ApiException.Unauthorized(ErrorCode.INVALID_AUTH_CREDENTIALS) to
                    HttpStatus.UNAUTHORIZED,
                ApiException.Forbidden(ErrorCode.FORBIDDEN) to HttpStatus.FORBIDDEN,
                ApiException.NotFound(ErrorCode.RESOURCE_NOT_FOUND) to HttpStatus.NOT_FOUND,
                ApiException.Conflict(ErrorCode.ALREADY_JOINED) to HttpStatus.CONFLICT,
                ApiException.UnprocessableEntity(ErrorCode.OBJECT_NOT_UPLOADED) to
                    HttpStatus.UNPROCESSABLE_CONTENT,
            )

            expectations.forEach { (exception, expectedStatus) ->
                val errorCode = exception.errorCode

                exception.statusCode.value() shouldBe expectedStatus.value()
                exception.body.status shouldBe expectedStatus.value()
                exception.body.title shouldBe expectedStatus.reasonPhrase
                exception.body.detail shouldBe errorCode.message
            }
        }
    }

    given("API 예외가 발생하면") {
        `when`("RESOURCE_NOT_FOUND ErrorCode로 예외를 생성할 때") {
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
                body["code"].stringValue() shouldBe ErrorCode.RESOURCE_NOT_FOUND.name
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
                body["code"].stringValue() shouldBe ErrorCode.INVALID_REQUEST.name
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
                body["detail"].stringValue() shouldBe "요청 값이 올바르지 않습니다."
                body["instance"].stringValue() shouldBe "/test/validation"
                body["code"].stringValue() shouldBe ErrorCode.INVALID_REQUEST.name
            }
        }

        `when`("요청 필드가 누락됐을 때") {
            val response = mockMvc.perform(
                post("/test/validation")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"),
            ).andReturn().response
            val body = objectMapper.readTree(response.contentAsString)

            then("내부 파싱 메시지를 숨긴 400 ProblemDetail로 응답한다") {
                response.status shouldBe 400
                response.contentType shouldBe MediaType.APPLICATION_PROBLEM_JSON_VALUE
                body["detail"].stringValue() shouldBe "요청 값이 올바르지 않습니다."
                body["instance"].stringValue() shouldBe "/test/validation"
            }
        }

        `when`("존재하지 않는 enum 값을 전달할 때") {
            val response = mockMvc.perform(
                post("/test/enum")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"provider":"UNKNOWN"}"""),
            ).andReturn().response
            val body = objectMapper.readTree(response.contentAsString)

            then("내부 enum 파싱 메시지를 숨긴 400 ProblemDetail로 응답한다") {
                response.status shouldBe 400
                response.contentType shouldBe MediaType.APPLICATION_PROBLEM_JSON_VALUE
                body["detail"].stringValue() shouldBe "요청 값이 올바르지 않습니다."
                body["instance"].stringValue() shouldBe "/test/enum"
            }
        }

        `when`("경로 변수에 숫자가 아니거나 Long 범위를 넘는 값을 전달할 때") {
            then("공통 INVALID_REQUEST ProblemDetail로 응답한다") {
                listOf("not-a-number", "9223372036854775808").forEach { invalidNumber ->
                    val response = mockMvc.perform(get("/test/numbers/$invalidNumber"))
                        .andReturn()
                        .response
                    val body = objectMapper.readTree(response.contentAsString)

                    response.status shouldBe 400
                    response.contentType shouldBe MediaType.APPLICATION_PROBLEM_JSON_VALUE
                    body["status"].intValue() shouldBe 400
                    body["detail"].stringValue() shouldBe ErrorCode.INVALID_REQUEST.message
                    body["instance"].stringValue() shouldBe "/test/numbers/$invalidNumber"
                    body["code"].stringValue() shouldBe ErrorCode.INVALID_REQUEST.name
                }
            }
        }

        `when`("경로 변수 검증이 실패할 때") {
            val response = mockMvc.perform(get("/test/numbers/0"))
                .andReturn()
                .response
            val body = objectMapper.readTree(response.contentAsString)

            then("공통 INVALID_REQUEST ProblemDetail로 응답한다") {
                response.status shouldBe 400
                response.contentType shouldBe MediaType.APPLICATION_PROBLEM_JSON_VALUE
                body["status"].intValue() shouldBe 400
                body["detail"].stringValue() shouldBe ErrorCode.INVALID_REQUEST.message
                body["instance"].stringValue() shouldBe "/test/numbers/0"
                body["code"].stringValue() shouldBe ErrorCode.INVALID_REQUEST.name
            }
        }
    }

    given("DB 무결성 위반이 발생하면") {
        `when`("저장 지점에서 비즈니스 오류로 변환하지 않은 위반일 때") {
            val response = mockMvc.perform(get("/test/other-data-integrity"))
                .andReturn()
                .response
            val body = objectMapper.readTree(response.contentAsString)

            then("constraint 이름을 해석하지 않고 안전한 500 ProblemDetail로 응답한다") {
                response.status shouldBe 500
                response.contentType shouldBe MediaType.APPLICATION_PROBLEM_JSON_VALUE
                body["detail"].stringValue() shouldBe "서버 내부 오류가 발생했습니다."
                body["code"].stringValue() shouldBe ErrorCode.INTERNAL_SERVER_ERROR.name
                response.contentAsString.contains("민감한 providerToken") shouldBe false
                body["instance"].stringValue() shouldBe "/test/other-data-integrity"
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
                body["code"].stringValue() shouldBe ErrorCode.INTERNAL_SERVER_ERROR.name
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
        fun validation(
            @Valid
            @RequestBody
            request: TestRequest
        ) = request

        @PostMapping("/enum")
        fun enum(
            @RequestBody
            request: EnumRequest
        ) = request

        @GetMapping("/numbers/{number}")
        fun number(
            @PathVariable
            @Positive
            number: Long,
        ) = number

        @GetMapping("/other-data-integrity")
        fun otherDataIntegrity(): Nothing =
            throw DataIntegrityViolationException(
                "민감한 providerToken",
                ConstraintViolationException(
                    "민감한 providerToken",
                    SQLException("민감한 providerToken"),
                    "fk_login_account_user",
                ),
            )

        @GetMapping("/illegal-argument")
        fun illegalArgument(): Nothing =
            throw IllegalArgumentException("민감한 내부 메시지")
    }

    private data class TestRequest(
        @field:NotBlank(message = "이름은 비어 있을 수 없습니다.")
        val name: String,
    )

    private data class EnumRequest(
        val provider: TestProvider,
    )

    private enum class TestProvider {
        GUEST,
    }
}
