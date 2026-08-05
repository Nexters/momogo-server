package com.mogumogu.momogo.user.presentation

import com.mogumogu.momogo.global.config.OpenApiConfiguration
import com.mogumogu.momogo.global.error.ErrorCode
import com.mogumogu.momogo.global.openapi.ApiErrors
import com.mogumogu.momogo.global.openapi.ApiExamples
import com.mogumogu.momogo.global.openapi.OpenApiExample
import com.mogumogu.momogo.global.security.RequestUserId
import com.mogumogu.momogo.user.application.AuthService
import com.mogumogu.momogo.user.application.UpdateNicknameCommand
import com.mogumogu.momogo.user.application.UserService
import com.mogumogu.momogo.user.domain.LoginProvider
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.web.bind.annotation.*

@Tag(
    name = "사용자",
    description = "회원가입과 사용자 정보 관리 API",
)
@RestController
@RequestMapping("/api/v1/user")
class UserController(
    private val authService: AuthService,
    private val userService: UserService,
) {

    @Operation(
        summary = "회원가입",
        description = "로그인 정보와 닉네임으로 가입하고 액세스 토큰과 리프레시 토큰을 발급합니다.",
    )
    @ApiExamples(
        request = OpenApiExample.REGISTER_REQUEST,
        success = OpenApiExample.AUTH_RESPONSE,
    )
    @ApiErrors(
        badRequest = [
            ErrorCode.INVALID_REQUEST,
            ErrorCode.UNSUPPORTED_PROVIDER,
        ],
        conflict = [ErrorCode.DUPLICATE_LOGIN_ACCOUNT],
    )
    @PostMapping("/register")
    fun register(
        @Valid
        @RequestBody
        request: RegisterRequest,
    ): AuthResponse {
        val authenticatedUser = authService.register(
            provider = request.provider,
            providerToken = request.providerToken,
            nickname = request.nickname,
        )

        return AuthResponse(
            userId = authenticatedUser.userId,
            nickname = authenticatedUser.nickname,
            accessToken = authenticatedUser.accessToken,
            refreshToken = authenticatedUser.refreshToken,
        )
    }

    @Operation(
        summary = "내 정보 조회",
        description = "현재 사용자의 ID와 닉네임을 조회합니다.",
    )
    @ApiExamples(success = OpenApiExample.USER_RESPONSE)
    @ApiErrors(notFound = [ErrorCode.USER_NOT_FOUND])
    @GetMapping("/me")
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
    fun getMe(
        @RequestUserId
        userId: Long,
    ): UserResponse {
        val result = userService.getUser(userId)

        return UserResponse(
            userId = result.userId,
            nickname = result.nickname,
        )
    }

    @Operation(
        summary = "닉네임 변경",
        description = "현재 사용자의 닉네임을 변경합니다.",
    )
    @ApiExamples(
        request = OpenApiExample.UPDATE_NICKNAME_REQUEST,
        success = OpenApiExample.UPDATED_USER_RESPONSE,
    )
    @ApiErrors(
        badRequest = [ErrorCode.INVALID_REQUEST],
        notFound = [ErrorCode.USER_NOT_FOUND],
    )
    @PatchMapping
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
    fun updateNickname(
        @RequestUserId
        userId: Long,
        @Valid
        @RequestBody
        request: UpdateNicknameRequest,
    ): UserResponse {
        val result = userService.updateNickname(
            UpdateNicknameCommand(
                userId = userId,
                nickname = request.nickname,
            ),
        )

        return UserResponse(
            userId = result.userId,
            nickname = result.nickname,
        )
    }

    @Operation(
        summary = "회원 탈퇴",
        description = "현재 사용자의 계정과 관련 정보를 삭제합니다.",
    )
    @ApiExamples(success = OpenApiExample.EMPTY_OBJECT_RESPONSE)
    @ApiErrors(notFound = [ErrorCode.USER_NOT_FOUND])
    @DeleteMapping
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
    fun withdraw(
        @RequestUserId
        userId: Long,
    ): Map<String, Any> {
        userService.withdraw(userId)
        return emptyMap()
    }
}

@Schema(description = "회원가입 요청")
data class RegisterRequest(
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

    @field:NotBlank(message = "nickname은 비어 있을 수 없습니다.")
    @field:Size(max = 6, message = "nickname은 6자를 초과할 수 없습니다.")
    @field:Schema(
        description = "사용할 닉네임. 앞뒤 공백은 제거됩니다.",
        example = "모모",
        maxLength = 6,
    )
    val nickname: String,
)

@Schema(description = "닉네임 변경 요청")
data class UpdateNicknameRequest(
    @field:NotBlank(message = "nickname은 비어 있을 수 없습니다.")
    @field:Size(max = 6, message = "nickname은 6자를 초과할 수 없습니다.")
    @field:Schema(
        description = "변경할 닉네임. 앞뒤 공백은 제거됩니다.",
        example = "새 닉네임",
        maxLength = 6,
    )
    val nickname: String,
)

@Schema(description = "사용자 응답")
data class UserResponse(
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
)
