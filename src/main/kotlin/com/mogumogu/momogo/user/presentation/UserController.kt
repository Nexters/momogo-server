package com.mogumogu.momogo.user.presentation

import com.mogumogu.momogo.global.config.OpenApiConfiguration
import com.mogumogu.momogo.global.security.RequestUserId
import com.mogumogu.momogo.user.application.AuthService
import com.mogumogu.momogo.user.application.UpdateNicknameCommand
import com.mogumogu.momogo.user.application.UserService
import com.mogumogu.momogo.user.domain.LoginProvider
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.ProblemDetail
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
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "회원가입 성공",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = AuthResponse::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "요청 값이 올바르지 않거나 지원하지 않는 로그인 방식",
                content = [
                    Content(
                        mediaType = "application/problem+json",
                        schema = Schema(implementation = ProblemDetail::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "409",
                description = "이미 가입된 계정",
                content = [
                    Content(
                        mediaType = "application/problem+json",
                        schema = Schema(implementation = ProblemDetail::class),
                    ),
                ],
            ),
        ],
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
        summary = "닉네임 변경",
        description = "현재 사용자의 닉네임을 변경합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "닉네임 변경 성공",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = UserResponse::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "닉네임이 비어 있거나 12자를 초과함",
                content = [
                    Content(
                        mediaType = "application/problem+json",
                        schema = Schema(implementation = ProblemDetail::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "사용자를 찾을 수 없음",
                content = [
                    Content(
                        mediaType = "application/problem+json",
                        schema = Schema(implementation = ProblemDetail::class),
                    ),
                ],
            ),
        ],
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
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "회원 탈퇴 성공",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(type = "object", example = "{}"),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "사용자를 찾을 수 없음",
                content = [
                    Content(
                        mediaType = "application/problem+json",
                        schema = Schema(implementation = ProblemDetail::class),
                    ),
                ],
            ),
        ],
    )
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
    @field:Schema(
        description = "사용할 닉네임. 앞뒤 공백은 제거됩니다.",
        example = "모모",
        maxLength = 12,
    )
    val nickname: String,
)

@Schema(description = "닉네임 변경 요청")
data class UpdateNicknameRequest(
    @field:NotBlank(message = "nickname은 비어 있을 수 없습니다.")
    @field:Schema(
        description = "변경할 닉네임. 앞뒤 공백은 제거됩니다.",
        example = "새 닉네임",
        maxLength = 12,
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
