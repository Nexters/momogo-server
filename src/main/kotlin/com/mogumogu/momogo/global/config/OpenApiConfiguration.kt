package com.mogumogu.momogo.global.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springframework.boot.info.BuildProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
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
            .components(
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
                    ),
            )

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
                Content().addMediaType(
                    "application/problem+json",
                    MediaType().schema(
                        Schema<Any>().apply {
                            `$ref` = "#/components/schemas/ProblemDetail"
                        },
                    ),
                ),
            )

    companion object {
        const val BEARER_AUTH = "bearerAuth"
        const val BEARER_UNAUTHORIZED_RESPONSE = "BearerUnauthorized"
    }
}
