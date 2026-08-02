package com.mogumogu.momogo.group.infra

import com.mogumogu.momogo.global.config.JpaConfig
import com.mogumogu.momogo.group.domain.Group
import com.mogumogu.momogo.group.domain.GroupMember
import com.mogumogu.momogo.group.domain.InviteCode
import com.mogumogu.momogo.user.domain.User
import com.mogumogu.momogo.user.infra.UserRepository
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.Instant

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaConfig::class)
@ApplyExtension(SpringExtension::class)
class GroupRepositoryTest(
    private val userRepository: UserRepository,
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
) : BehaviorSpec({

    given("그룹과 그룹 멤버가 있으면") {
        `when`("각 Repository로 저장하고 조회할 때") {
            then("기본 JPA CRUD를 사용할 수 있다") {
                val user = userRepository.saveAndFlush(
                    User(_nickname = "모고"),
                )
                val group = groupRepository.saveAndFlush(
                    Group(
                        _name = "모고모고",
                        _inviteCode = InviteCode(_value = "FFF666"),
                    ),
                )
                val groupId = requireNotNull(group.id)

                val groupMember = groupMemberRepository.saveAndFlush(
                    GroupMember(
                        _group = group,
                        _user = user,
                    ),
                )
                val groupMemberId = requireNotNull(groupMember.id)

                groupRepository.findById(groupId).orElseThrow().name shouldBe "모고모고"
                groupRepository.findByInviteCode(InviteCode(_value = "FFF666"))?.id shouldBe groupId
                groupMemberRepository.findById(groupMemberId).orElseThrow().group.id shouldBe groupId
                groupMemberRepository.findByGroupIdAndUserId(
                    groupId = groupId,
                    userId = requireNotNull(user.id),
                )?.id shouldBe groupMemberId

                groupMemberRepository.deleteById(groupMemberId)
                groupMemberRepository.flush()

                groupMemberRepository.existsById(groupMemberId) shouldBe false
            }
        }
    }

    given("가입 중인 멤버와 탈퇴한 멤버가 있는 그룹이면") {
        `when`("현재 가입 인원을 조회할 때") {
            then("탈퇴한 멤버를 제외한 수를 반환한다") {
                val joinedUser = userRepository.saveAndFlush(
                    User(_nickname = "가입 회원"),
                )
                val leftUser = userRepository.saveAndFlush(
                    User(_nickname = "탈퇴 회원"),
                )
                val group = groupRepository.saveAndFlush(
                    Group(
                        _name = "인원 제한 그룹",
                        _inviteCode = InviteCode(_value = "COUNT8"),
                    ),
                )
                val leftMember = GroupMember(
                    _group = group,
                    _user = leftUser,
                ).apply {
                    leave(Instant.parse("2030-01-01T00:00:00Z"))
                }

                groupMemberRepository.saveAndFlush(
                    GroupMember(
                        _group = group,
                        _user = joinedUser,
                    ),
                )
                groupMemberRepository.saveAndFlush(leftMember)

                groupMemberRepository.countJoinedByGroupId(
                    groupId = requireNotNull(group.id),
                ) shouldBe 1L
            }
        }
    }

    given("삭제된 그룹이 있으면") {
        `when`("활성 그룹 조회와 전체 초대 코드 조회를 함께 사용할 때") {
            then("API용 조회에서는 제외하고 초대 코드 중복 확인에는 포함한다") {
                val inviteCode = InviteCode(_value = "SOFT01")
                val group = groupRepository.saveAndFlush(
                    Group(
                        _name = "삭제된 그룹",
                        _inviteCode = inviteCode,
                    ).apply {
                        delete(Instant.parse("2030-01-01T00:00:00Z"))
                    },
                )

                groupRepository.findByInviteCode(inviteCode)?.id shouldBe group.id
                groupRepository.findActiveByInviteCode(inviteCode) shouldBe null
                groupRepository.findActiveByInviteCodeForUpdate(inviteCode) shouldBe null
                groupRepository.findActiveByIdForUpdate(requireNotNull(group.id)) shouldBe null
            }
        }
    }

    given("탈퇴할 회원과 그룹에 남을 회원의 멤버십이 있으면") {
        `when`("탈퇴할 회원의 멤버십을 모두 삭제할 때") {
            then("해당 회원의 멤버십만 삭제한다") {
                val withdrawingUser = userRepository.saveAndFlush(
                    User(_nickname = "탈퇴 대상"),
                )
                val remainingUser = userRepository.saveAndFlush(
                    User(_nickname = "잔류 회원"),
                )
                val group = groupRepository.saveAndFlush(
                    Group(
                        _name = "회원 탈퇴 그룹",
                        _inviteCode = InviteCode(_value = "DELETE"),
                    ),
                )
                val withdrawingMember = GroupMember(
                    _group = group,
                    _user = withdrawingUser,
                ).apply {
                    leave(Instant.parse("2030-01-01T00:00:00Z"))
                }
                groupMemberRepository.saveAndFlush(withdrawingMember)
                val remainingMember = groupMemberRepository.saveAndFlush(
                    GroupMember(
                        _group = group,
                        _user = remainingUser,
                    ),
                )

                groupMemberRepository.deleteAllByUserId(
                    userId = requireNotNull(withdrawingUser.id),
                ) shouldBe 1

                groupMemberRepository.findByGroupIdAndUserId(
                    groupId = requireNotNull(group.id),
                    userId = requireNotNull(withdrawingUser.id),
                ) shouldBe null
                groupMemberRepository.findById(requireNotNull(remainingMember.id)).isPresent shouldBe true
            }
        }
    }
})
