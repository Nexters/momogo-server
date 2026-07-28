package com.mogumogu.momogo.user.application

import com.mogumogu.momogo.global.error.ApiException
import com.mogumogu.momogo.global.error.ErrorCode
import com.mogumogu.momogo.user.domain.LoginProvider
import com.mogumogu.momogo.user.domain.ProviderTokenAuthenticator
import org.springframework.stereotype.Component

@Component
class ProviderTokenAuthenticationService(
    authenticators: List<ProviderTokenAuthenticator>,
) {

    private val authenticatorsByProvider = authenticators.associateBy { it.provider }

    init {
        check(authenticatorsByProvider.size == authenticators.size) {
            "로그인 제공자별 인증 구현은 하나만 등록할 수 있습니다."
        }
    }

    fun authenticate(
        provider: LoginProvider,
        providerToken: String,
    ): String {
        val authenticator = authenticatorsByProvider[provider]
            ?: throw ApiException.BadRequest(ErrorCode.UNSUPPORTED_PROVIDER)

        return try {
            authenticator.authenticate(providerToken)
        } catch (_: IllegalArgumentException) {
            throw ApiException.BadRequest(ErrorCode.INVALID_REQUEST)
        }
    }
}
