package com.mogumogu.momogo.user.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class LoginAccountTest : BehaviorSpec({

    given("유효한 로그인 계정 정보가 있으면") {
        `when`("로그인 계정을 생성할 때") {
            then("회원과 로그인 제공자 정보를 조회할 수 있다") {
                val user = User(_nickname = "모고")
                val loginAccount = LoginAccount(
                    _id = 1L,
                    _user = user,
                    _provider = LoginProvider.APPLE,
                    _providerId = "apple-user-1",
                )

                loginAccount.id shouldBe 1L
                loginAccount.user shouldBe user
                loginAccount.provider shouldBe LoginProvider.APPLE
                loginAccount.providerId shouldBe "apple-user-1"
            }
        }
    }

    given("유효하지 않은 로그인 제공자 회원 ID가 있으면") {
        val user = User(_nickname = "모고")

        `when`("빈 값으로 로그인 계정을 생성할 때") {
            then("생성을 거부한다") {
                listOf("", " ", "\t").forEach { providerId ->
                    shouldThrow<IllegalArgumentException> {
                        LoginAccount(
                            _user = user,
                            _provider = LoginProvider.GUEST,
                            _providerId = providerId,
                        )
                    }.message shouldBe "로그인 제공자 회원 ID는 비어 있을 수 없습니다."
                }
            }
        }

        `when`("255자를 초과한 값으로 로그인 계정을 생성할 때") {
            then("생성을 거부한다") {
                shouldThrow<IllegalArgumentException> {
                    LoginAccount(
                        _user = user,
                        _provider = LoginProvider.KAKAO,
                        _providerId = "a".repeat(256),
                    )
                }.message shouldBe "로그인 제공자 회원 ID는 255자를 초과할 수 없습니다."
            }
        }
    }
})
