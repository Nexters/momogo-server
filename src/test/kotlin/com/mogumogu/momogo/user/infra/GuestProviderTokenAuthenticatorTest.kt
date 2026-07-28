package com.mogumogu.momogo.user.infra

import com.mogumogu.momogo.user.domain.LoginProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class GuestProviderTokenAuthenticatorTest : BehaviorSpec({

    val authenticator = GuestProviderTokenAuthenticator()

    given("Guest provider token이 유효하면") {
        `when`("provider와 providerId를 확인할 때") {
            then("GUEST를 처리하고 UUID 검증이나 정규화 없이 원문을 반환한다") {
                val providerToken = "  Not-A-UUID/Guest.Token  "

                authenticator.provider shouldBe LoginProvider.GUEST
                authenticator.authenticate(providerToken) shouldBe providerToken
            }
        }

        `when`("길이가 255자인 토큰을 인증할 때") {
            then("현재 DB 컬럼의 경계 길이를 허용한다") {
                val providerToken = "a".repeat(255)

                authenticator.authenticate(providerToken) shouldBe providerToken
            }
        }
    }

    given("Guest provider token이 유효하지 않으면") {
        `when`("공백뿐인 토큰을 인증할 때") {
            then("인증을 거부한다") {
                listOf("", " ", "\t", "\n").forEach { providerToken ->
                    shouldThrow<IllegalArgumentException> {
                        authenticator.authenticate(providerToken)
                    }.message shouldBe "로그인 제공자 토큰은 비어 있을 수 없습니다."
                }
            }
        }

        `when`("길이가 255자를 초과한 토큰을 인증할 때") {
            then("인증을 거부한다") {
                shouldThrow<IllegalArgumentException> {
                    authenticator.authenticate("a".repeat(256))
                }.message shouldBe "로그인 제공자 토큰은 255자를 초과할 수 없습니다."
            }
        }
    }
})
