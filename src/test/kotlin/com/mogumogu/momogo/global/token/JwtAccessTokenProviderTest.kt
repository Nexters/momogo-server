package com.mogumogu.momogo.global.token

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.*
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.*

class JwtAccessTokenProviderTest : BehaviorSpec({

    val properties = JwtProperties(
        issuer = "momogo-token-test",
        secretBase64 = Base64.getEncoder().encodeToString(ByteArray(32) { index -> index.toByte() }),
    )
    val configuration = JwtConfiguration()
    val secretKey = configuration.jwtSecretKey(properties)
    val encoder = configuration.jwtEncoder(secretKey)
    val decoder = configuration.jwtDecoder(secretKey, properties)
    val issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS)
    val provider = JwtAccessTokenProvider(
        jwtEncoder = encoder,
        jwtProperties = properties,
        clock = Clock.fixed(issuedAt, ZoneOffset.UTC),
    )

    given("유효한 userId가 있으면") {
        `when`("access token을 발급할 때") {
            val token = provider.issue(42L)
            val jwt = decoder.decode(token)

            then("HS256 서명과 필수 claim을 포함하고 정확히 3일 뒤 만료한다") {
                jwt.headers["alg"] shouldBe "HS256"
                jwt.getClaimAsString("iss") shouldBe properties.issuer
                jwt.subject shouldBe "42"
                jwt.issuedAt shouldBe issuedAt
                jwt.expiresAt shouldBe issuedAt.plus(Duration.ofDays(3))
            }
        }
    }

    given("서명됐지만 Long으로 안전하게 변환할 수 없는 sub가 있으면") {
        listOf("not-a-number", "9223372036854775808").forEach { invalidSubject ->
            `when`("sub가 $invalidSubject 인 토큰을 검증할 때") {
                val token = encode(
                    encoder = encoder,
                    issuer = properties.issuer,
                    subject = invalidSubject,
                    issuedAt = issuedAt,
                    expiresAt = issuedAt.plusSeconds(60),
                )

                then("토큰 검증을 401 처리 가능한 예외로 거부한다") {
                    shouldThrow<JwtException> {
                        decoder.decode(token)
                    }
                }
            }
        }
    }

    given("필수 시간 claim이 누락된 access token이 있으면") {
        `when`("exp가 누락된 토큰을 검증할 때") {
            val claims = JwtClaimsSet
                .builder()
                .issuer(properties.issuer)
                .subject("42")
                .issuedAt(issuedAt)
                .build()

            then("검증을 거부한다") {
                val token = encode(encoder, claims)
                val payload = String(Base64.getUrlDecoder().decode(token.split(".")[1]))
                payload shouldNotContain "\"exp\""

                shouldThrow<JwtException> {
                    decoder.decode(token)
                }
            }
        }

        `when`("iat가 누락된 토큰을 검증할 때") {
            val claims = JwtClaimsSet
                .builder()
                .issuer(properties.issuer)
                .subject("42")
                .expiresAt(issuedAt.plusSeconds(60))
                .build()

            then("검증을 거부한다") {
                val token = encode(encoder, claims)
                val payload = String(Base64.getUrlDecoder().decode(token.split(".")[1]))
                payload shouldNotContain "\"iat\""

                shouldThrow<JwtException> {
                    decoder.decode(token)
                }
            }
        }
    }

    given("만료되거나 issuer가 다른 access token이 있으면") {
        `when`("만료 토큰을 검증할 때") {
            val token = encode(
                encoder = encoder,
                issuer = properties.issuer,
                subject = "42",
                issuedAt = issuedAt.minus(Duration.ofDays(4)),
                expiresAt = Instant.now().minusSeconds(120),
            )

            then("검증을 거부한다") {
                shouldThrow<JwtException> {
                    decoder.decode(token)
                }
            }
        }

        `when`("issuer가 다른 토큰을 검증할 때") {
            val token = encode(
                encoder = encoder,
                issuer = "another-issuer",
                subject = "42",
                issuedAt = issuedAt,
                expiresAt = issuedAt.plusSeconds(60),
            )

            then("검증을 거부한다") {
                shouldThrow<JwtException> {
                    decoder.decode(token)
                }
            }
        }
    }

    given("서명이 변조된 access token이 있으면") {
        `when`("토큰을 검증할 때") {
            val token = provider.issue(42L)
            val parts = token.split(".")
            val signature = parts[2]
            val changedFirstCharacter = if (signature.first() == 'A') 'B' else 'A'
            val alteredToken = "${parts[0]}.${parts[1]}.$changedFirstCharacter${signature.drop(1)}"

            then("검증을 거부한다") {
                shouldThrow<JwtException> {
                    decoder.decode(alteredToken)
                }
            }
        }
    }

    given("JWT 비밀값 설정이 안전하지 않으면") {
        `when`("Base64 형식이 아닐 때") {
            then("애플리케이션 시작 단계에서 거부한다") {
                shouldThrow<IllegalStateException> {
                    configuration.jwtSecretKey(
                        properties.copy(secretBase64 = "not-base64***"),
                    )
                }
            }
        }

        `when`("디코딩 결과가 32바이트보다 짧을 때") {
            then("애플리케이션 시작 단계에서 거부한다") {
                shouldThrow<IllegalStateException> {
                    configuration.jwtSecretKey(
                        properties.copy(
                            secretBase64 = Base64.getEncoder().encodeToString(ByteArray(31)),
                        ),
                    )
                }
            }
        }
    }
})

private fun encode(
    encoder: JwtEncoder,
    issuer: String,
    subject: String,
    issuedAt: Instant,
    expiresAt: Instant,
): String {
    val claims = JwtClaimsSet
        .builder()
        .issuer(issuer)
        .subject(subject)
        .issuedAt(issuedAt)
        .expiresAt(expiresAt)
        .build()

    return encode(encoder, claims)
}

private fun encode(
    encoder: JwtEncoder,
    claims: JwtClaimsSet,
): String {
    val headers = JwsHeader
        .with(MacAlgorithm.HS256)
        .type("JWT")
        .build()

    return encoder
        .encode(JwtEncoderParameters.from(headers, claims))
        .tokenValue
}
