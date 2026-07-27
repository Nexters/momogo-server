package com.mogumogu.momogo.group.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class GroupTest : BehaviorSpec({

    given("유효한 그룹 정보가 있으면") {
        `when`("그룹을 생성할 때") {
            then("식별자와 그룹 정보를 조회할 수 있다") {
                val group = Group(
                    _id = 1L,
                    _name = "모고모고",
                    _inviteCode = "invite-code",
                )

                group.id shouldBe 1L
                group.name shouldBe "모고모고"
                group.inviteCode shouldBe "invite-code"
            }
        }

        `when`("그룹명을 변경할 때") {
            then("변경된 그룹명을 조회할 수 있다") {
                val group = createGroup()

                group.updateName("변경된 그룹")

                group.name shouldBe "변경된 그룹"
            }
        }

        `when`("초대 코드를 재발급할 때") {
            then("새로운 초대 코드를 조회할 수 있다") {
                val group = createGroup()

                group.regenerateInviteCode("new-invite-code")

                group.inviteCode shouldBe "new-invite-code"
            }
        }
    }

    given("유효하지 않은 그룹명이 있으면") {
        `when`("빈 그룹명으로 그룹을 생성할 때") {
            then("생성을 거부한다") {
                listOf("", " ", "\t").forEach { name ->
                    shouldThrow<IllegalArgumentException> {
                        Group(
                            _name = name,
                            _inviteCode = "invite-code",
                        )
                    }.message shouldBe "그룹명은 비어 있을 수 없습니다."
                }
            }
        }

        `when`("255자를 초과한 그룹명으로 그룹을 생성할 때") {
            then("생성을 거부한다") {
                shouldThrow<IllegalArgumentException> {
                    Group(
                        _name = "가".repeat(256),
                        _inviteCode = "invite-code",
                    )
                }.message shouldBe "그룹명은 255자를 초과할 수 없습니다."
            }
        }

        `when`("유효하지 않은 그룹명으로 변경할 때") {
            then("기존 그룹명을 유지한다") {
                val group = createGroup()

                shouldThrow<IllegalArgumentException> {
                    group.updateName("")
                }

                group.name shouldBe "모고모고"
            }
        }
    }

    given("유효하지 않은 초대 코드가 있으면") {
        `when`("빈 초대 코드로 그룹을 생성할 때") {
            then("생성을 거부한다") {
                listOf("", " ", "\t").forEach { inviteCode ->
                    shouldThrow<IllegalArgumentException> {
                        Group(
                            _name = "모고모고",
                            _inviteCode = inviteCode,
                        )
                    }.message shouldBe "초대 코드는 비어 있을 수 없습니다."
                }
            }
        }

        `when`("255자를 초과한 초대 코드로 그룹을 생성할 때") {
            then("생성을 거부한다") {
                shouldThrow<IllegalArgumentException> {
                    Group(
                        _name = "모고모고",
                        _inviteCode = "a".repeat(256),
                    )
                }.message shouldBe "초대 코드는 255자를 초과할 수 없습니다."
            }
        }

        `when`("유효하지 않은 초대 코드로 재발급할 때") {
            then("기존 초대 코드를 유지한다") {
                val group = createGroup()

                shouldThrow<IllegalArgumentException> {
                    group.regenerateInviteCode("")
                }

                group.inviteCode shouldBe "invite-code"
            }
        }
    }
})

private fun createGroup(): Group =
    Group(
        _name = "모고모고",
        _inviteCode = "invite-code",
    )
