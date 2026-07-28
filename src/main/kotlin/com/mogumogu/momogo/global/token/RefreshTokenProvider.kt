package com.mogumogu.momogo.global.token

import java.time.Instant

interface RefreshTokenProvider {

    fun issue(): IssuedRefreshToken

    fun hash(token: String): String
}

data class IssuedRefreshToken(
    val token: String,
    val tokenHash: String,
    val expiresAt: Instant,
)
