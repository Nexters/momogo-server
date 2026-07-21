package com.mogumogu.momogo.domain.user

class LoginAccount(
    val id: Long? = null,
    val userId: Long,
    val provider: LoginProvider,
    val providerId: String,
) {
    init {
        validateProviderId(providerId)
    }

    private fun validateProviderId(providerId: String) {
        require(providerId.length <= MAX_PROVIDER_ID_LENGTH) {
            "로그인 제공자 식별자는 ${MAX_PROVIDER_ID_LENGTH}자 이하여야 합니다."
        }
    }

    private companion object {
        const val MAX_PROVIDER_ID_LENGTH = 255
    }
}
