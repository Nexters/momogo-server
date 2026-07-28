package com.mogumogu.momogo.global.token

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.*

class OpaqueRefreshTokenProviderTest : BehaviorSpec({

    val now = Instant.parse("2030-01-01T00:00:00Z")
    val provider = OpaqueRefreshTokenProvider(
        secureRandom = SecureRandom(),
        clock = Clock.fixed(now, ZoneOffset.UTC),
    )

    given("refresh token을 발급할 때") {
        val issuedToken = provider.issue()

        then("URL-safe Base64로 인코딩한 32바이트 난수를 사용한다") {
            issuedToken.token shouldMatch Regex("^[A-Za-z0-9_-]{43}$")
            Base64.getUrlDecoder().decode(issuedToken.token).size shouldBe 32
        }

        then("원문과 다른 SHA-256 해시 및 30일 만료 시각을 반환한다") {
            issuedToken.tokenHash shouldNotBe issuedToken.token
            issuedToken.tokenHash shouldMatch Regex("^[0-9a-f]{64}$")
            issuedToken.tokenHash shouldBe provider.hash(issuedToken.token)
            issuedToken.expiresAt shouldBe now.plus(Duration.ofDays(30))
        }
    }

    given("동일한 refresh token 원문이 있으면") {
        `when`("여러 번 해시할 때") {
            then("항상 같은 SHA-256 해시를 만든다") {
                provider.hash("refresh-token") shouldBe
                        "0eb17643d4e9261163783a420859c92c7d212fa9624106a12b510afbec266120"
            }
        }
    }

    given("refresh token을 두 번 발급하면") {
        `when`("원문을 비교할 때") {
            then("서로 다른 예측 불가능한 값을 반환한다") {
                provider.issue().token shouldNotBe provider.issue().token
            }
        }
    }
})
