package com.mogumogu.momogo.user.presentation

import com.mogumogu.momogo.global.error.ErrorCode
import com.mogumogu.momogo.global.openapi.ApiErrors
import com.mogumogu.momogo.global.openapi.ApiExamples
import com.mogumogu.momogo.global.openapi.OpenApiExample
import com.mogumogu.momogo.user.application.AuthService
import com.mogumogu.momogo.user.domain.LoginProvider
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.web.bind.annotation.*

@Tag(
    name = "인증",
    description = "로그인, 토큰 재발급, 로그아웃 API",
)
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
) {

    @Operation(
        summary = "로그인",
        description = "로그인 정보를 확인하고 액세스 토큰과 리프레시 토큰을 발급합니다.",
    )
    @ApiExamples(
        request = OpenApiExample.LOGIN_REQUEST,
        success = OpenApiExample.AUTH_RESPONSE,
    )
    @ApiErrors(
        badRequest = [
            ErrorCode.INVALID_REQUEST,
            ErrorCode.UNSUPPORTED_PROVIDER,
        ],
        notFound = [ErrorCode.USER_NOT_FOUND],
    )
    @PostMapping("/login")
    fun login(
        @Valid
        @RequestBody
        request: LoginRequest,
    ): AuthResponse {
        val authenticatedUser = authService.login(
            provider = request.provider,
            providerToken = request.providerToken,
        )

        return AuthResponse(
            userId = authenticatedUser.userId,
            nickname = authenticatedUser.nickname,
            accessToken = authenticatedUser.accessToken,
            refreshToken = authenticatedUser.refreshToken,
        )
    }

    @Operation(
        summary = "토큰 재발급",
        description = "리프레시 토큰을 새 액세스 토큰과 리프레시 토큰으로 교체합니다.",
    )
    @ApiExamples(
        request = OpenApiExample.REFRESH_TOKEN_REQUEST,
        success = OpenApiExample.REISSUE_RESPONSE,
    )
    @ApiErrors(
        badRequest = [ErrorCode.INVALID_REQUEST],
        notFound = [ErrorCode.INVALID_REFRESH_TOKEN],
    )
    @PostMapping("/reissue")
    fun reissue(
        @Valid
        @RequestBody
        request: RefreshTokenRequest,
    ): ReissueResponse {
        val reissuedTokens = authService.reissue(request.refreshToken)

        return ReissueResponse(
            accessToken = reissuedTokens.accessToken,
            refreshToken = reissuedTokens.refreshToken,
        )
    }

    @Operation(
        summary = "로그아웃",
        description = "리프레시 토큰을 폐기합니다. 존재하지 않거나 이미 폐기된 토큰을 보내도 성공하며, 액세스 토큰은 만료 전까지 사용할 수 있습니다.",
    )
    @ApiExamples(
        request = OpenApiExample.REFRESH_TOKEN_REQUEST,
        success = OpenApiExample.EMPTY_OBJECT_RESPONSE,
    )
    @ApiErrors(badRequest = [ErrorCode.INVALID_REQUEST])
    @DeleteMapping("/logout")
    fun logout(
        @Valid
        @RequestBody
        request: RefreshTokenRequest,
    ): Map<String, Any> {
        authService.logout(request.refreshToken)
        return emptyMap()
    }
}

@Schema(description = "로그인 요청")
data class LoginRequest(
    @field:Schema(
        description = "로그인 방식. 현재 GUEST만 지원합니다.",
        allowableValues = ["GUEST"],
        example = "GUEST",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val provider: LoginProvider,

    @field:NotBlank(message = "providerToken은 비어 있을 수 없습니다.")
    @field:Size(min = 1, max = 255, message = "providerToken은 1자 이상 255자 이하여야 합니다.")
    @field:Schema(
        description = "로그인에 사용하는 토큰",
        example = "guest-device-token",
        minLength = 1,
        maxLength = 255,
    )
    val providerToken: String,
)

@Schema(description = "리프레시 토큰 요청")
data class RefreshTokenRequest(
    @field:NotBlank(message = "refreshToken은 비어 있을 수 없습니다.")
    @field:Schema(
        description = "로그인 또는 토큰 재발급 후 받은 리프레시 토큰",
        example = "Y29kZXgtZXhhbXBsZS1yZWZyZXNoLXRva2Vu",
    )
    val refreshToken: String,
)

@Schema(description = "인증 성공 응답")
data class AuthResponse(
    @field:Schema(
        description = "사용자 ID",
        example = "1",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val userId: Long,

    @field:Schema(
        description = "닉네임",
        example = "모모",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val nickname: String,

    @field:Schema(
        description = "API 요청에 사용하는 액세스 토큰",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val accessToken: String,

    @field:Schema(
        description = "토큰 재발급과 로그아웃에 사용하는 리프레시 토큰",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val refreshToken: String,
)

@Schema(description = "토큰 재발급 응답")
data class ReissueResponse(
    @field:Schema(
        description = "새로 발급한 액세스 토큰",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val accessToken: String,

    @field:Schema(
        description = "새로 발급한 리프레시 토큰",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val refreshToken: String,
)
