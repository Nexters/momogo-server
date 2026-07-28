package com.mogumogu.momogo.global.token

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.*
import java.util.*
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties::class)
class JwtConfiguration {

    @Bean
    fun jwtSecretKey(properties: JwtProperties): SecretKey {
        require(properties.issuer.isNotBlank()) {
            "JWT issuer는 비어 있을 수 없습니다."
        }

        val secretBytes = try {
            Base64.getDecoder().decode(properties.secretBase64)
        } catch (exception: IllegalArgumentException) {
            throw IllegalStateException(
                "JWT secret은 표준 Base64 형식이어야 합니다.",
                exception,
            )
        }

        check(secretBytes.size >= MINIMUM_SECRET_BYTE_SIZE) {
            "JWT secret은 Base64 디코딩 기준 32바이트 이상이어야 합니다."
        }

        return SecretKeySpec(secretBytes, HMAC_SHA_256)
    }

    @Bean
    fun jwtEncoder(jwtSecretKey: SecretKey): JwtEncoder =
        NimbusJwtEncoder
            .withSecretKey(jwtSecretKey)
            .algorithm(MacAlgorithm.HS256)
            .build()

    @Bean
    fun jwtDecoder(
        jwtSecretKey: SecretKey,
        properties: JwtProperties,
    ): JwtDecoder {
        val defaultClaimSetConverter = MappedJwtClaimSetConverter.withDefaults(emptyMap())
        val decoder = NimbusJwtDecoder
            .withSecretKey(jwtSecretKey)
            .macAlgorithm(MacAlgorithm.HS256)
            .build()

        decoder.setClaimSetConverter { rawClaims ->
            defaultClaimSetConverter.convert(rawClaims).toMutableMap().apply {
                if (!rawClaims.containsKey(JwtClaimNames.IAT)) {
                    remove(JwtClaimNames.IAT)
                }
            }
        }
        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator(
                JwtValidators.createDefaultWithIssuer(properties.issuer),
                RequiredAccessTokenClaimsValidator,
                LongSubjectValidator,
            ),
        )

        return decoder
    }

    private companion object {
        const val MINIMUM_SECRET_BYTE_SIZE = 32
        const val HMAC_SHA_256 = "HmacSHA256"
    }
}

private object RequiredAccessTokenClaimsValidator : OAuth2TokenValidator<Jwt> {

    private val missingClaim = OAuth2Error(
        "invalid_token",
        "The token is missing a required claim.",
        null,
    )

    override fun validate(token: Jwt): OAuth2TokenValidatorResult =
        if (
            token.claims.containsKey(JwtClaimNames.IAT) &&
            token.claims.containsKey(JwtClaimNames.EXP)
        ) {
            OAuth2TokenValidatorResult.success()
        } else {
            OAuth2TokenValidatorResult.failure(missingClaim)
        }
}

private object LongSubjectValidator : OAuth2TokenValidator<Jwt> {

    private val invalidSubject = OAuth2Error(
        "invalid_token",
        "The token subject is invalid.",
        null,
    )

    override fun validate(token: Jwt): OAuth2TokenValidatorResult =
        if (token.subject?.toLongOrNull() != null) {
            OAuth2TokenValidatorResult.success()
        } else {
            OAuth2TokenValidatorResult.failure(invalidSubject)
        }
}
