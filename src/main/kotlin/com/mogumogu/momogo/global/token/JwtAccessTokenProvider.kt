package com.mogumogu.momogo.global.token

import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration

@Component
class JwtAccessTokenProvider(
    private val jwtEncoder: JwtEncoder,
    private val jwtProperties: JwtProperties,
    private val clock: Clock,
) : AccessTokenProvider {

    override fun issue(userId: Long): String {
        require(userId > 0) {
            "access token의 userId는 양수여야 합니다."
        }

        val issuedAt = clock.instant()
        val headers = JwsHeader
            .with(MacAlgorithm.HS256)
            .type("JWT")
            .build()
        val claims = JwtClaimsSet
            .builder()
            .issuer(jwtProperties.issuer)
            .subject(userId.toString())
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plus(ACCESS_TOKEN_VALIDITY))
            .build()

        return jwtEncoder
            .encode(JwtEncoderParameters.from(headers, claims))
            .tokenValue
    }

    private companion object {
        val ACCESS_TOKEN_VALIDITY: Duration = Duration.ofDays(3)
    }
}
