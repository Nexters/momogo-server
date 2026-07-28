package com.mogumogu.momogo.user.presentation

import com.mogumogu.momogo.global.security.RequestUserId
import com.mogumogu.momogo.user.application.AuthService
import com.mogumogu.momogo.user.application.UpdateNicknameCommand
import com.mogumogu.momogo.user.application.UserService
import com.mogumogu.momogo.user.domain.LoginProvider
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/user")
class UserController(
    private val authService: AuthService,
    private val userService: UserService,
) {

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

    @PatchMapping
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

    @DeleteMapping
    fun withdraw(
        @RequestUserId
        userId: Long,
    ): Map<String, Any> {
        userService.withdraw(userId)
        return emptyMap()
    }
}

data class RegisterRequest(
    val provider: LoginProvider,

    @field:NotBlank(message = "providerToken은 비어 있을 수 없습니다.")
    @field:Size(max = 255, message = "providerToken은 255자를 초과할 수 없습니다.")
    val providerToken: String,

    @field:NotBlank(message = "nickname은 비어 있을 수 없습니다.")
    val nickname: String,
)

data class UpdateNicknameRequest(
    @field:NotBlank(message = "nickname은 비어 있을 수 없습니다.")
    val nickname: String,
)

data class UserResponse(
    val userId: Long,
    val nickname: String,
)
