package com.mogumogu.momogo.user.infra

import com.mogumogu.momogo.user.domain.LoginProvider
import com.mogumogu.momogo.user.domain.ProviderTokenAuthenticator
import org.springframework.stereotype.Component

@Component
class GuestProviderTokenAuthenticator : ProviderTokenAuthenticator {

    override val provider: LoginProvider = LoginProvider.GUEST

    override fun authenticate(providerToken: String): String {
        require(providerToken.isNotBlank()) { "로그인 제공자 토큰은 비어 있을 수 없습니다." }
        require(providerToken.length <= 255) { "로그인 제공자 토큰은 255자를 초과할 수 없습니다." }
        return providerToken
    }
}
