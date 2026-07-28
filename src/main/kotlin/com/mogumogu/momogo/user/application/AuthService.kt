package com.mogumogu.momogo.user.application

import com.mogumogu.momogo.global.error.ApiException
import com.mogumogu.momogo.global.error.ErrorCode
import com.mogumogu.momogo.global.token.RefreshTokenProvider
import com.mogumogu.momogo.user.domain.LoginAccount
import com.mogumogu.momogo.user.domain.LoginProvider
import com.mogumogu.momogo.user.domain.User
import com.mogumogu.momogo.user.infra.LoginAccountRepository
import com.mogumogu.momogo.user.infra.RefreshTokenRepository
import com.mogumogu.momogo.user.infra.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val loginAccountRepository: LoginAccountRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val providerTokenAuthenticationService: ProviderTokenAuthenticationService,
    private val refreshTokenProvider: RefreshTokenProvider,
    private val tokenIssuer: TokenIssuer,
    private val clock: Clock,
) {

    @Transactional
    fun register(
        provider: LoginProvider,
        providerToken: String,
        nickname: String,
    ): AuthenticatedUser {
        val providerId = providerTokenAuthenticationService.authenticate(provider, providerToken)
        if (loginAccountRepository.existsByProviderAndProviderId(provider, providerId)) {
            throw ApiException.Conflict(ErrorCode.DUPLICATE_LOGIN_ACCOUNT)
        }

        val user = userRepository.save(createUser(nickname))
        loginAccountRepository.saveAndFlush(
            LoginAccount(
                _user = user,
                _provider = provider,
                _providerId = providerId,
            ),
        )

        return user.toAuthenticatedUser(tokenIssuer.issue(user))
    }

    @Transactional
    fun login(
        provider: LoginProvider,
        providerToken: String,
    ): AuthenticatedUser {
        val providerId = providerTokenAuthenticationService.authenticate(provider, providerToken)
        val loginAccount = loginAccountRepository.findByProviderAndProviderId(provider, providerId)
            ?: throw ApiException.Unauthorized(ErrorCode.INVALID_AUTH_CREDENTIALS)
        val user = loginAccount.user

        return user.toAuthenticatedUser(tokenIssuer.issue(user))
    }

    @Transactional
    fun reissue(refreshToken: String): ReissuedTokens {
        val tokenHash = refreshTokenProvider.hash(refreshToken)
        val savedToken = refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
            ?: throw ApiException.Unauthorized(ErrorCode.INVALID_REFRESH_TOKEN)
        val now = clock.instant()

        if (!savedToken.isActive(now)) {
            throw ApiException.Unauthorized(ErrorCode.INVALID_REFRESH_TOKEN)
        }

        savedToken.revoke(now)
        val issuedTokens = tokenIssuer.issue(savedToken.user)

        return ReissuedTokens(
            accessToken = issuedTokens.accessToken,
            refreshToken = issuedTokens.refreshToken,
        )
    }

    @Transactional
    fun logout(refreshToken: String) {
        val tokenHash = refreshTokenProvider.hash(refreshToken)
        val savedToken = refreshTokenRepository.findByTokenHashForUpdate(tokenHash) ?: return

        savedToken.revoke(clock.instant())
    }

    private fun createUser(nickname: String): User =
        try {
            User(_nickname = nickname)
        } catch (_: IllegalArgumentException) {
            throw ApiException.BadRequest(ErrorCode.INVALID_REQUEST)
        }

    private fun User.toAuthenticatedUser(issuedTokens: IssuedTokens): AuthenticatedUser =
        AuthenticatedUser(
            userId = checkNotNull(id) { "저장된 사용자 ID가 없습니다." },
            nickname = nickname,
            accessToken = issuedTokens.accessToken,
            refreshToken = issuedTokens.refreshToken,
        )
}

data class AuthenticatedUser(
    val userId: Long,
    val nickname: String,
    val accessToken: String,
    val refreshToken: String,
)

data class ReissuedTokens(
    val accessToken: String,
    val refreshToken: String,
)
