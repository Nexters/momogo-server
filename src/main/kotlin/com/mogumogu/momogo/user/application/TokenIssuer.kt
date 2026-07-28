package com.mogumogu.momogo.user.application

import com.mogumogu.momogo.global.token.AccessTokenProvider
import com.mogumogu.momogo.global.token.RefreshTokenProvider
import com.mogumogu.momogo.user.domain.RefreshToken
import com.mogumogu.momogo.user.domain.User
import com.mogumogu.momogo.user.infra.RefreshTokenRepository
import org.springframework.stereotype.Component

@Component
class TokenIssuer(
    private val accessTokenProvider: AccessTokenProvider,
    private val refreshTokenProvider: RefreshTokenProvider,
    private val refreshTokenRepository: RefreshTokenRepository,
) {

    fun issue(user: User): IssuedTokens {
        val userId = checkNotNull(user.id) { "토큰 발급 전에 사용자가 저장되어야 합니다." }
        val issuedRefreshToken = refreshTokenProvider.issue()

        refreshTokenRepository.save(
            RefreshToken(
                _user = user,
                _tokenHash = issuedRefreshToken.tokenHash,
                _expiresAt = issuedRefreshToken.expiresAt,
            ),
        )

        return IssuedTokens(
            accessToken = accessTokenProvider.issue(userId),
            refreshToken = issuedRefreshToken.token,
        )
    }
}

data class IssuedTokens(
    val accessToken: String,
    val refreshToken: String,
)
