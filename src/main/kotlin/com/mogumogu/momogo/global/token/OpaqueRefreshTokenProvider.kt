package com.mogumogu.momogo.global.token

import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.util.*

@Component
class OpaqueRefreshTokenProvider(
    private val secureRandom: SecureRandom,
    private val clock: Clock,
) : RefreshTokenProvider {

    override fun issue(): IssuedRefreshToken {
        val randomBytes = ByteArray(RANDOM_BYTE_SIZE).also(secureRandom::nextBytes)
        val token = BASE64_URL_ENCODER.encodeToString(randomBytes)

        return IssuedRefreshToken(
            token = token,
            tokenHash = hash(token),
            expiresAt = clock.instant().plus(REFRESH_TOKEN_VALIDITY),
        )
    }

    override fun hash(token: String): String =
        MessageDigest
            .getInstance(SHA_256)
            .digest(token.toByteArray(StandardCharsets.UTF_8))
            .let(HEX_FORMAT::formatHex)

    private companion object {
        const val RANDOM_BYTE_SIZE = 32
        const val SHA_256 = "SHA-256"
        val BASE64_URL_ENCODER: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
        val HEX_FORMAT: HexFormat = HexFormat.of()
        val REFRESH_TOKEN_VALIDITY: Duration = Duration.ofDays(30)
    }
}
