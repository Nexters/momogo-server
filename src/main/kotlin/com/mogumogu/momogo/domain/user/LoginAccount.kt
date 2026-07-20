package com.mogumogu.momogo.domain.user

class LoginAccount(
    val id: Long? = null,
    val userId: Long,
    var provider: LoginProvider,
    var providerId: String,
) {
    fun changeProvider(
        provider: LoginProvider,
        providerId: String,
    ) {
        this.provider = provider
        this.providerId = providerId
    }
}
