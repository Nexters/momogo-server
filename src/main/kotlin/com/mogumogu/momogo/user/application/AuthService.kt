package com.mogumogu.momogo.user.application

import com.mogumogu.momogo.event.domain.ServiceEvent
import com.mogumogu.momogo.event.domain.ServiceEventType
import com.mogumogu.momogo.global.error.ApiException
import com.mogumogu.momogo.global.error.ErrorCode
import com.mogumogu.momogo.global.logging.LogFingerprint
import com.mogumogu.momogo.global.token.RefreshTokenProvider
import com.mogumogu.momogo.user.domain.LoginAccount
import com.mogumogu.momogo.user.domain.LoginProvider
import com.mogumogu.momogo.user.domain.User
import com.mogumogu.momogo.user.infra.LoginAccountRepository
import com.mogumogu.momogo.user.infra.RefreshTokenRepository
import com.mogumogu.momogo.user.infra.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException
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
    private val eventPublisher: ApplicationEventPublisher,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun register(
        provider: LoginProvider,
        providerToken: String,
        nickname: String,
    ): AuthenticatedUser {
        val providerId = providerTokenAuthenticationService.authenticate(provider, providerToken)
        val providerIdFingerprint = LogFingerprint.of(providerId)
        if (loginAccountRepository.existsByProviderAndProviderId(provider, providerId)) {
            log.warn(
                "가입 실패: provider={}, providerIdFingerprint={}, reason=이미 가입된 로그인 계정",
                provider,
                providerIdFingerprint,
            )
            throw ApiException.Conflict(ErrorCode.DUPLICATE_LOGIN_ACCOUNT)
        }

        val user = userRepository.save(createUser(nickname))
        try {
            loginAccountRepository.saveAndFlush(
                LoginAccount(
                    _user = user,
                    _provider = provider,
                    _providerId = providerId,
                ),
            )
        } catch (_: DataIntegrityViolationException) {
            log.warn(
                "가입 실패: provider={}, providerIdFingerprint={}, reason=로그인 계정 저장 중 중복",
                provider,
                providerIdFingerprint,
            )
            throw ApiException.Conflict(ErrorCode.DUPLICATE_LOGIN_ACCOUNT)
        }

        val authenticatedUser = user.toAuthenticatedUser(tokenIssuer.issue(user))

        log.info(
            "가입 성공: provider={}, providerIdFingerprint={}, userId={}",
            provider,
            providerIdFingerprint,
            authenticatedUser.userId,
        )

        eventPublisher.publishEvent(
            ServiceEvent(
                type = ServiceEventType.USER_REGISTERED,
                userId = authenticatedUser.userId,
                totalUserCount = userRepository.count(),
            ),
        )

        return authenticatedUser
    }

    @Transactional
    fun login(
        provider: LoginProvider,
        providerToken: String,
    ): AuthenticatedUser {
        val providerId = providerTokenAuthenticationService.authenticate(provider, providerToken)
        val providerIdFingerprint = LogFingerprint.of(providerId)
        val loginAccount = loginAccountRepository.findByProviderAndProviderId(provider, providerId)
            ?: run {
                log.warn(
                    "로그인 실패: provider={}, providerIdFingerprint={}, reason=로그인 계정 없음",
                    provider,
                    providerIdFingerprint,
                )
                throw ApiException.NotFound(ErrorCode.USER_NOT_FOUND)
            }
        val user = userRepository.findByIdForUpdate(loginAccount.user.id!!)
            ?: run {
                log.warn(
                    "로그인 실패: provider={}, providerIdFingerprint={}, reason=로그인 계정의 사용자 없음",
                    provider,
                    providerIdFingerprint,
                )
                throw ApiException.NotFound(ErrorCode.USER_NOT_FOUND)
            }

        log.info(
            "로그인 성공: provider={}, providerIdFingerprint={}, userId={}",
            provider,
            providerIdFingerprint,
            user.id,
        )

        return user.toAuthenticatedUser(tokenIssuer.issue(user))
    }

    @Transactional
    fun reissue(refreshToken: String): ReissuedTokens {
        val tokenHash = refreshTokenProvider.hash(refreshToken)
        val refreshTokenFingerprint = LogFingerprint.of(tokenHash)
        val savedToken = refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
            ?: run {
                logReissueFailure(refreshTokenFingerprint, "저장되지 않은 토큰")
                throw ApiException.NotFound(ErrorCode.INVALID_REFRESH_TOKEN)
            }
        val user = userRepository.findByIdForUpdate(savedToken.user.id!!)
            ?: run {
                logReissueFailure(refreshTokenFingerprint, "토큰의 사용자 없음")
                throw ApiException.NotFound(ErrorCode.INVALID_REFRESH_TOKEN)
            }
        val now = clock.instant()

        if (!savedToken.isActive(now)) {
            val reason = if (savedToken.revokedAt != null) "이미 폐기된 토큰" else "만료된 토큰"
            logReissueFailure(refreshTokenFingerprint, reason, user.id)
            throw ApiException.NotFound(ErrorCode.INVALID_REFRESH_TOKEN)
        }

        savedToken.revoke(now)
        val issuedTokens = tokenIssuer.issue(user)

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

    /**
     * 재발급 실패는 모두 같은 404 응답이므로 원인을 구분해 남긴다.
     * 지문이 같은 토큰으로 재발급이 반복 실패하면 클라이언트가 회전된 토큰을 저장하지 못한 것으로 볼 수 있다.
     */
    private fun logReissueFailure(
        refreshTokenFingerprint: String,
        reason: String,
        userId: Long? = null,
    ) {
        log.warn(
            "토큰 재발급 실패: refreshTokenFingerprint={}, reason={}, userId={}",
            refreshTokenFingerprint,
            reason,
            userId,
        )
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
