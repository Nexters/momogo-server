package com.mogumogu.momogo.user.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class RefreshTokenTest : BehaviorSpec({

    val expiresAt = Instant.parse("2030-01-01T00:00:00Z")

    given("유효한 리프레시 토큰 정보가 있으면") {
        `when`("리프레시 토큰을 생성할 때") {
            then("토큰 정보를 조회할 수 있다") {
                val user = User(_nickname = "모고")
                val tokenHash = "aB".repeat(32)
                val refreshToken = RefreshToken(
                    _id = 1L,
                    _user = user,
                    _tokenHash = tokenHash,
                    _expiresAt = expiresAt,
                )

                refreshToken.id shouldBe 1L
                refreshToken.user shouldBe user
                refreshToken.tokenHash shouldBe tokenHash
                refreshToken.expiresAt shouldBe expiresAt
                refreshToken.revokedAt shouldBe null
            }
        }
    }

    given("폐기되지 않은 리프레시 토큰이 있으면") {
        `when`("만료 시각 전에 활성 상태를 확인할 때") {
            then("활성 상태다") {
                val refreshToken = createRefreshToken(expiresAt)

                refreshToken.isActive(expiresAt.minusNanos(1)) shouldBe true
            }
        }

        `when`("만료 시각 또는 그 이후에 활성 상태를 확인할 때") {
            then("비활성 상태다") {
                val refreshToken = createRefreshToken(expiresAt)

                refreshToken.isActive(expiresAt) shouldBe false
                refreshToken.isActive(expiresAt.plusNanos(1)) shouldBe false
            }
        }

        `when`("토큰을 폐기할 때") {
            then("폐기 시각을 기록하고 비활성 상태가 된다") {
                val refreshToken = createRefreshToken(expiresAt)
                val revokedAt = Instant.parse("2029-01-01T00:00:00Z")

                refreshToken.revoke(revokedAt)

                refreshToken.revokedAt shouldBe revokedAt
                refreshToken.isActive(revokedAt) shouldBe false
            }
        }
    }

    given("이미 폐기된 리프레시 토큰이 있으면") {
        `when`("다시 폐기할 때") {
            then("최초 폐기 시각을 유지한다") {
                val refreshToken = createRefreshToken(expiresAt)
                val firstRevokedAt = Instant.parse("2029-01-01T00:00:00Z")

                refreshToken.revoke(firstRevokedAt)
                refreshToken.revoke(firstRevokedAt.plusSeconds(1))

                refreshToken.revokedAt shouldBe firstRevokedAt
            }
        }
    }

    given("SHA-256 형식이 아닌 토큰 해시가 있으면") {
        `when`("리프레시 토큰을 생성할 때") {
            then("생성을 거부한다") {
                listOf(
                    "",
                    "a".repeat(63),
                    "a".repeat(65),
                    "g".repeat(64),
                ).forEach { tokenHash ->
                    shouldThrow<IllegalArgumentException> {
                        RefreshToken(
                            _user = User(_nickname = "모고"),
                            _tokenHash = tokenHash,
                            _expiresAt = expiresAt,
                        )
                    }.message shouldBe "리프레시 토큰 해시는 64자리 16진수여야 합니다."
                }
            }
        }
    }
})

private fun createRefreshToken(expiresAt: Instant): RefreshToken =
    RefreshToken(
        _user = User(_nickname = "모고"),
        _tokenHash = "a".repeat(64),
        _expiresAt = expiresAt,
    )
