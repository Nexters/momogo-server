package com.mogumogu.momogo.domain.user

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class DomainModelTest : BehaviorSpec({

    given("User를 생성할 때") {
        `when`("닉네임이 1자이면") {
            then("생성할 수 있다") {
                User(nickname = "가").nickname.length shouldBe 1
            }
        }

        `when`("닉네임이 12자이면") {
            then("생성할 수 있다") {
                User(nickname = "가".repeat(12)).nickname.length shouldBe 12
            }
        }

        `when`("닉네임이 비어 있으면") {
            then("생성할 수 없다") {
                shouldThrow<IllegalArgumentException> {
                    User(nickname = "")
                }
            }
        }

        `when`("닉네임이 12자를 초과하면") {
            then("생성할 수 없다") {
                shouldThrow<IllegalArgumentException> {
                    User(nickname = "가".repeat(13))
                }
            }
        }

        `when`("닉네임을 12자 초과 값으로 변경하면") {
            then("변경할 수 없다") {
                val user = User(nickname = "모모")

                shouldThrow<IllegalArgumentException> {
                    user.changeNickname("가".repeat(13))
                }
            }
        }

        `when`("닉네임을 올바른 값으로 변경하면") {
            then("변경한 값을 반환한다") {
                val user = User(nickname = "모모")

                user.changeNickname("고모")

                user.nickname shouldBe "고모"
            }
        }
    }

    given("LoginAccount를 생성할 때") {
        `when`("로그인 제공자 식별자가 255자이면") {
            then("생성할 수 있다") {
                val loginAccount = LoginAccount(
                    userId = 1L,
                    provider = LoginProvider.GUEST,
                    providerId = "a".repeat(255),
                )

                loginAccount.providerId.length shouldBe 255
            }
        }

        `when`("로그인 제공자 식별자가 255자를 초과하면") {
            then("생성할 수 없다") {
                shouldThrow<IllegalArgumentException> {
                    LoginAccount(
                        userId = 1L,
                        provider = LoginProvider.GUEST,
                        providerId = "a".repeat(256),
                    )
                }
            }
        }

        `when`("로그인 제공자를 255자 초과 식별자로 변경하면") {
            then("변경할 수 없다") {
                val loginAccount = LoginAccount(
                    userId = 1L,
                    provider = LoginProvider.GUEST,
                    providerId = "guest-device-id",
                )

                shouldThrow<IllegalArgumentException> {
                    loginAccount.changeProvider(
                        provider = LoginProvider.NAVER,
                        providerId = "a".repeat(256),
                    )
                }

                loginAccount.provider shouldBe LoginProvider.GUEST
                loginAccount.providerId shouldBe "guest-device-id"
            }
        }

        `when`("로그인 제공자를 올바른 값으로 변경하면") {
            then("제공자와 식별자를 함께 변경한다") {
                val loginAccount = LoginAccount(
                    userId = 1L,
                    provider = LoginProvider.KAKAO,
                    providerId = "kakao-user-id",
                )

                loginAccount.changeProvider(
                    provider = LoginProvider.NAVER,
                    providerId = "naver-user-id",
                )

                loginAccount.provider shouldBe LoginProvider.NAVER
                loginAccount.providerId shouldBe "naver-user-id"
            }
        }
    }
})
