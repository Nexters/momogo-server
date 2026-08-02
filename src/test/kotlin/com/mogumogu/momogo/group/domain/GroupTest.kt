package com.mogumogu.momogo.group.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class GroupTest : BehaviorSpec({

    given("유효한 그룹 정보가 있으면") {
        `when`("그룹을 생성할 때") {
            then("식별자와 그룹 정보를 조회할 수 있다") {
                val group = Group(
                    _id = 1L,
                    _name = "모고모고",
                    _inviteCode = InviteCode(_value = "ABC123"),
                )

                group.id shouldBe 1L
                group.name shouldBe "모고모고"
                group.inviteCode shouldBe InviteCode(_value = "ABC123")
                group.deletedAt shouldBe null
                group.isActive() shouldBe true
            }
        }

        `when`("그룹을 삭제할 때") {
            then("최초 삭제 시각을 기록하고 비활성 상태가 된다") {
                val group = createGroup()
                val deletedAt = Instant.parse("2030-01-01T00:00:00Z")

                group.delete(deletedAt)
                group.delete(deletedAt.plusSeconds(1))

                group.deletedAt shouldBe deletedAt
                group.isActive() shouldBe false
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

                group.regenerateInviteCode(InviteCode(_value = "NEW456"))

                group.inviteCode shouldBe InviteCode(_value = "NEW456")
            }
        }
    }

    given("가입 인원이 7명인 그룹이 있으면") {
        `when`("새로운 회원의 가입 가능 여부를 확인할 때") {
            then("가입을 허용한다") {
                val group = createGroup()

                shouldNotThrowAny {
                    group.ensureCanJoin(joinedMemberCount = 7)
                }
            }
        }
    }

    given("가입 인원이 8명인 그룹이 있으면") {
        `when`("새로운 회원의 가입 가능 여부를 확인할 때") {
            then("가입을 거부한다") {
                val group = createGroup()

                shouldThrow<IllegalStateException> {
                    group.ensureCanJoin(joinedMemberCount = 8)
                }.message shouldBe "그룹은 최대 8명까지 가입할 수 있습니다."
            }
        }
    }

    given("유효하지 않은 가입 인원이 있으면") {
        `when`("가입 가능 여부를 확인할 때") {
            then("검사를 거부한다") {
                val group = createGroup()

                shouldThrow<IllegalArgumentException> {
                    group.ensureCanJoin(joinedMemberCount = -1)
                }.message shouldBe "가입 인원은 0명 이상이어야 합니다."
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
                            _inviteCode = InviteCode(_value = "ABC123"),
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
                        _inviteCode = InviteCode(_value = "ABC123"),
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
})

private fun createGroup(): Group =
    Group(
        _name = "모고모고",
        _inviteCode = InviteCode(_value = "ABC123"),
    )
