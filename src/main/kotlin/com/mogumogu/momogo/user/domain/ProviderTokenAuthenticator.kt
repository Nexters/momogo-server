package com.mogumogu.momogo.user.domain

interface ProviderTokenAuthenticator {

    val provider: LoginProvider

    fun authenticate(providerToken: String): String
}
