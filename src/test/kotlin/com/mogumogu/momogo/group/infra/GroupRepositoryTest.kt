package com.mogumogu.momogo.group.infra

import com.mogumogu.momogo.global.config.JpaConfig
import com.mogumogu.momogo.group.domain.Group
import com.mogumogu.momogo.group.domain.GroupMember
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
                        _inviteCode = "repository-invite-code",
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
                groupRepository.findByInviteCode("repository-invite-code")?.id shouldBe groupId
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
})
