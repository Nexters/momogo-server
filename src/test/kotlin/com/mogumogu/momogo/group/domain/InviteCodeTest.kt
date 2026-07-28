package com.mogumogu.momogo.group.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class InviteCodeTest : BehaviorSpec({

    given("영문 대문자와 숫자로 구성된 6자리 값이 있으면") {
        `when`("초대 코드를 생성할 때") {
            then("값을 조회하고 같은 값을 동등하게 비교할 수 있다") {
                val inviteCode = InviteCode(_value = "ABC123")

                inviteCode.value shouldBe "ABC123"
                inviteCode shouldBe InviteCode(_value = "ABC123")
            }
        }
    }

    given("초대 코드 형식에 맞지 않는 값이 있으면") {
        `when`("초대 코드를 생성할 때") {
            then("생성을 거부한다") {
                listOf(
                    "",
                    "ABC12",
                    "ABC1234",
                    "abc123",
                    "ABC-12",
                    "가나다123",
                ).forEach { value ->
                    shouldThrow<IllegalArgumentException> {
                        InviteCode(_value = value)
                    }.message shouldBe "초대 코드는 영문 대문자와 숫자로 구성된 6자리여야 합니다."
                }
            }
        }
    }

    given("새로운 초대 코드가 필요하면") {
        `when`("안전한 난수로 생성할 때") {
            then("모든 값이 초대 코드 형식을 만족한다") {
                repeat(100) {
                    InviteCode.generate().value.matches(Regex("^[A-Z0-9]{6}$")) shouldBe true
                }
            }
        }
    }

    given("JPA가 초대 코드 값 객체를 생성하면") {
        then("공개 setter 없이 기본 생성자를 제공한다") {
            InviteCode::class.java.constructors.any { constructor ->
                constructor.parameterCount == 0
            } shouldBe true
            InviteCode::class.java.methods.none { method ->
                method.name.startsWith("set")
            } shouldBe true
        }
    }
})
