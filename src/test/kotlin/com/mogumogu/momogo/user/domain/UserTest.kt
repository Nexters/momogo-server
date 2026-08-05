package com.mogumogu.momogo.user.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class UserTest : BehaviorSpec({

    given("유효한 회원 정보가 있으면") {
        `when`("회원을 생성할 때") {
            then("식별자와 앞뒤 공백이 제거된 닉네임을 조회할 수 있다") {
                val user = User(
                    _id = 1L,
                    _nickname = "  모고  ",
                )

                user.id shouldBe 1L
                user.nickname shouldBe "모고"
            }
        }

        `when`("닉네임을 변경할 때") {
            then("앞뒤 공백이 제거된 변경 닉네임을 조회할 수 있다") {
                val user = User(_nickname = "변경 전")

                user.updateNickname(" 변경후 ")

                user.nickname shouldBe "변경후"
            }
        }

        `when`("길이가 1자 또는 6자인 닉네임을 사용할 때") {
            then("경계 길이의 닉네임을 허용한다") {
                User(_nickname = "가").nickname shouldBe "가"
                User(_nickname = "가".repeat(6)).nickname shouldBe "가".repeat(6)
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

        `when`("6자를 초과한 닉네임으로 회원을 생성할 때") {
            then("생성을 거부한다") {
                shouldThrow<IllegalArgumentException> {
                    User(_nickname = "가".repeat(7))
                }.message shouldBe "닉네임은 6자를 초과할 수 없습니다."
            }
        }

        `when`("공백을 제거하면 6자 이하이지만 원문이 6자를 초과한 닉네임으로 회원을 생성할 때") {
            then("요청 원문 길이를 기준으로 생성을 거부한다") {
                shouldThrow<IllegalArgumentException> {
                    User(_nickname = "  모모모  ")
                }.message shouldBe "닉네임은 6자를 초과할 수 없습니다."
            }
        }

        `when`("유효하지 않은 닉네임으로 변경할 때") {
            then("기존 닉네임을 유지한다") {
                val user = User(_nickname = "기존닉네임")

                listOf("   ", "가".repeat(7)).forEach { nickname ->
                    shouldThrow<IllegalArgumentException> {
                        user.updateNickname(nickname)
                    }
                }

                user.nickname shouldBe "기존닉네임"
            }
        }
    }
})
