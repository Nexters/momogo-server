package com.mogumogu.momogo.global.config

import com.mogumogu.momogo.global.error.ApiProblemDetail
import com.mogumogu.momogo.global.error.ErrorCode
import com.mogumogu.momogo.global.openapi.ApiErrors
import com.mogumogu.momogo.global.openapi.ApiExamples
import com.mogumogu.momogo.global.openapi.OpenApiExample
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType as OpenApiMediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.boot.info.BuildProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE
import org.springdoc.core.customizers.OpenApiCustomizer

@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration(
    private val buildProperties: BuildProperties,
    @param:Value("\${momogo.openapi.server-url}")
    private val serverUrl: String,
) {

    @Bean
    fun momogoOpenApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("Momogo API")
                    .description("Momogo 서버 API 문서")
                    .version(buildProperties.version),
            )
            .servers(listOf(Server().url(serverUrl)))
            .components(openApiComponents())

    @Bean
    fun apiDocumentationOperationCustomizer(): OperationCustomizer =
        OperationCustomizer { operation, handlerMethod ->
            val apiExamples = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.method,
                ApiExamples::class.java,
            )
            val apiErrors = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.method,
                ApiErrors::class.java,
            )

            applyExamples(operation, apiExamples)
            applyErrors(operation, apiErrors)
            operation
        }

    @Bean
    fun bearerUnauthorizedResponseCustomizer(): OpenApiCustomizer =
        OpenApiCustomizer { openApi ->
            openApi.paths
                ?.values
                ?.flatMap { pathItem -> pathItem.readOperations() }
                ?.filter { operation ->
                    operation.security?.any { requirement ->
                        requirement.containsKey(BEARER_AUTH)
                    } == true
                }
                ?.filterNot { operation -> operation.responses.containsKey("401") }
                ?.forEach { operation ->
                    operation.responses.addApiResponse(
                        "401",
                        ApiResponse().apply {
                            `$ref` = "#/components/responses/$BEARER_UNAUTHORIZED_RESPONSE"
                        },
                    )
                }
        }

    private fun bearerUnauthorizedResponse(): ApiResponse =
        ApiResponse()
            .description("액세스 토큰이 없거나 유효하지 않음")
            .content(
                problemDetailContent(
                    status = HttpStatus.UNAUTHORIZED,
                    errorCodes = listOf(ErrorCode.INVALID_AUTH_CREDENTIALS),
                ),
            )

    private fun openApiComponents(): Components =
        Components()
            .addSecuritySchemes(
                BEARER_AUTH,
                SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("회원가입 또는 로그인 후 받은 액세스 토큰을 입력합니다."),
            )
            .addResponses(
                BEARER_UNAUTHORIZED_RESPONSE,
                bearerUnauthorizedResponse(),
            )
            .apply {
                ModelConverters.getInstance()
                    .readAll(ApiProblemDetail::class.java)
                    .forEach { (name, schema) -> addSchemas(name, schema) }

                OpenApiExample.entries
                    .filterNot { example -> example == OpenApiExample.NONE }
                    .forEach { example ->
                        addExamples(
                            example.componentName,
                            Example()
                                .summary(example.summary)
                                .value(example.value),
                        )
                    }
            }

    private fun applyExamples(
        operation: Operation,
        apiExamples: ApiExamples?,
    ) {
        if (apiExamples == null) {
            return
        }

        if (apiExamples.request != OpenApiExample.NONE) {
            val requestContent = checkNotNull(operation.requestBody?.content) {
                "요청 본문이 없는 API에는 request 예시를 지정할 수 없습니다."
            }
            requestContent.addExampleReference(apiExamples.request)
        }

        if (apiExamples.success != OpenApiExample.NONE) {
            val successResponse = checkNotNull(operation.responses?.get(SUCCESS_RESPONSE_CODE)) {
                "$SUCCESS_RESPONSE_CODE 성공 응답이 없어 예시를 연결할 수 없습니다."
            }
            val successContent = checkNotNull(successResponse.content) {
                "$SUCCESS_RESPONSE_CODE 성공 응답 본문이 없어 예시를 연결할 수 없습니다."
            }
            successContent.addExampleReference(apiExamples.success)
            operation.summary
                ?.takeIf(String::isNotBlank)
                ?.let { summary -> successResponse.description("$summary 성공") }
        }
    }

    private fun applyErrors(
        operation: Operation,
        apiErrors: ApiErrors?,
    ) {
        apiErrors
            ?.responses()
            ?.forEach { (status, errorCodes) ->
                operation.responses.addApiResponse(
                    status.value().toString(),
                    ApiResponse()
                        .description(errorCodes.joinToString(" / ") { errorCode -> errorCode.message })
                        .content(problemDetailContent(status, errorCodes)),
                )
            }
    }

    private fun ApiErrors.responses(): List<Pair<HttpStatus, List<ErrorCode>>> =
        listOf(
            HttpStatus.BAD_REQUEST to badRequest.toList(),
            HttpStatus.UNAUTHORIZED to unauthorized.toList(),
            HttpStatus.FORBIDDEN to forbidden.toList(),
            HttpStatus.NOT_FOUND to notFound.toList(),
            HttpStatus.CONFLICT to conflict.toList(),
            HttpStatus.UNPROCESSABLE_CONTENT to unprocessableEntity.toList(),
            HttpStatus.INTERNAL_SERVER_ERROR to internalServerError.toList(),
        ).filter { (_, errorCodes) -> errorCodes.isNotEmpty() }

    private fun Content.addExampleReference(example: OpenApiExample) {
        val mediaType = checkNotNull(this[APPLICATION_JSON_VALUE]) {
            "$APPLICATION_JSON_VALUE 미디어 타입이 없어 예시를 연결할 수 없습니다."
        }
        mediaType.addExamples(
            example.componentName,
            exampleReference(example.componentName),
        )
    }

    private fun problemDetailContent(
        status: HttpStatus,
        errorCodes: List<ErrorCode>,
    ): Content =
        Content().addMediaType(
            APPLICATION_PROBLEM_JSON_VALUE,
            OpenApiMediaType()
                .schema(
                    Schema<Any>().apply {
                        `$ref` = PROBLEM_DETAIL_SCHEMA_REF
                    },
                )
                .apply {
                    errorCodes.forEach { errorCode ->
                        addExamples(
                            errorCode.name,
                            Example()
                                .summary(errorCode.message)
                                .value(errorCode.problemDetailExample(status)),
                        )
                    }
                },
        )

    private fun exampleReference(componentName: String): Example =
        Example().apply {
            `$ref` = "#/components/examples/$componentName"
        }

    private fun ErrorCode.problemDetailExample(status: HttpStatus): Map<String, Any> =
        mapOf(
            "title" to status.reasonPhrase,
            "status" to status.value(),
            "detail" to message,
            "instance" to "/requested/path",
            "code" to name,
        )

    companion object {
        const val BEARER_AUTH = "bearerAuth"
        const val BEARER_UNAUTHORIZED_RESPONSE = "BearerUnauthorized"
        private const val SUCCESS_RESPONSE_CODE = "200"
        private const val PROBLEM_DETAIL_SCHEMA_REF = "#/components/schemas/ApiProblemDetail"
    }
}
