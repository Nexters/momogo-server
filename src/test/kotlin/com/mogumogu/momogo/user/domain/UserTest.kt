package com.mogumogu.momogo.user.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class UserTest : BehaviorSpec({

    given("유효한 회원 정보가 있으면") {
        `when`("회원을 생성할 때") {
            then("식별자와 닉네임을 조회할 수 있다") {
                val user = User(
                    _id = 1L,
                    _nickname = "모고",
                )

                user.id shouldBe 1L
                user.nickname shouldBe "모고"
            }
        }

        `when`("닉네임을 변경할 때") {
            then("변경된 닉네임을 조회할 수 있다") {
                val user = User(_nickname = "변경 전")

                user.updateNickname("변경 후")

                user.nickname shouldBe "변경 후"
            }
        }
    }

    given("유효하지 않은 닉네임이 있으면") {
        `when`("빈 닉네임으로 회원을 생성할 때") {
            then("생성을 거부한다") {
                listOf("", " ", "\t").forEach { nickname ->
                    shouldThrow<IllegalArgumentException> {
                        User(_nickname = nickname)
                    }.message shouldBe "닉네임은 비어 있을 수 없습니다."
                }
            }
        }

        `when`("255자를 초과한 닉네임으로 회원을 생성할 때") {
            then("생성을 거부한다") {
                shouldThrow<IllegalArgumentException> {
                    User(_nickname = "가".repeat(256))
                }.message shouldBe "닉네임은 255자를 초과할 수 없습니다."
            }
        }

        `when`("유효하지 않은 닉네임으로 변경할 때") {
            then("기존 닉네임을 유지한다") {
                val user = User(_nickname = "기존 닉네임")

                shouldThrow<IllegalArgumentException> {
                    user.updateNickname("")
                }

                user.nickname shouldBe "기존 닉네임"
            }
        }
    }
})
