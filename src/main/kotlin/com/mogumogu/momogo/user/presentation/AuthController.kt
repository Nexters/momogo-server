package com.mogumogu.momogo.user.presentation

import com.mogumogu.momogo.user.application.AuthService
import com.mogumogu.momogo.user.domain.LoginProvider
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
) {

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

data class LoginRequest(
    val provider: LoginProvider,

    @field:NotBlank(message = "providerToken은 비어 있을 수 없습니다.")
    @field:Size(max = 255, message = "providerToken은 255자를 초과할 수 없습니다.")
    val providerToken: String,
)

data class RefreshTokenRequest(
    @field:NotBlank(message = "refreshToken은 비어 있을 수 없습니다.")
    val refreshToken: String,
)

data class AuthResponse(
    val userId: Long,
    val nickname: String,
    val accessToken: String,
    val refreshToken: String,
)

data class ReissueResponse(
    val accessToken: String,
    val refreshToken: String,
)
