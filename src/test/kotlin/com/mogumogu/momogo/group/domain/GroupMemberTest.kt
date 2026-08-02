package com.mogumogu.momogo.group.domain

import com.mogumogu.momogo.user.domain.User
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class GroupMemberTest : BehaviorSpec({

    given("그룹에 가입한 회원이 있으면") {
        `when`("그룹 멤버십을 생성할 때") {
            then("그룹과 회원 정보를 조회할 수 있고 가입 상태다") {
                val group = createGroupForMember()
                val user = User(_nickname = "모고")
                val groupMember = GroupMember(
                    _id = 1L,
                    _group = group,
                    _user = user,
                )

                groupMember.id shouldBe 1L
                groupMember.group shouldBe group
                groupMember.user shouldBe user
                groupMember.deletedAt shouldBe null
                groupMember.isJoined() shouldBe true
            }
        }

        `when`("그룹에서 나갈 때") {
            then("탈퇴 시각을 기록하고 탈퇴 상태가 된다") {
                val groupMember = createGroupMember()
                val deletedAt = Instant.parse("2030-01-01T00:00:00Z")

                groupMember.leave(deletedAt)

                groupMember.deletedAt shouldBe deletedAt
                groupMember.isJoined() shouldBe false
            }
        }

        `when`("다시 가입할 수 있는지 확인할 때") {
            then("이미 가입한 멤버십은 가입을 거부한다") {
                val groupMember = createGroupMember()

                shouldThrow<IllegalStateException> {
                    groupMember.ensureCanJoin()
                }.message shouldBe "이미 그룹에 가입되어 있습니다."
            }
        }
    }

    given("이미 그룹에서 나간 회원이 있으면") {
        `when`("다시 그룹에서 나갈 때") {
            then("최초 탈퇴 시각을 유지한다") {
                val groupMember = createGroupMember()
                val firstDeletedAt = Instant.parse("2030-01-01T00:00:00Z")

                groupMember.leave(firstDeletedAt)
                groupMember.leave(firstDeletedAt.plusSeconds(1))

                groupMember.deletedAt shouldBe firstDeletedAt
            }
        }

        `when`("다시 가입할 수 있는지 확인할 때") {
            then("탈퇴한 멤버십은 재가입을 허용한다") {
                val groupMember = createGroupMember()
                groupMember.leave(Instant.parse("2030-01-01T00:00:00Z"))

                shouldNotThrowAny {
                    groupMember.ensureCanJoin()
                }
            }
        }
    }
})

private fun createGroupMember(): GroupMember =
    GroupMember(
        _group = createGroupForMember(),
        _user = User(_nickname = "모고"),
    )

private fun createGroupForMember(): Group =
    Group(
        _name = "모고모고",
        _inviteCode = InviteCode(_value = "MEMBER"),
    )
