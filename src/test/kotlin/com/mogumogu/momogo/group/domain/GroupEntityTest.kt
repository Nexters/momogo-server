package com.mogumogu.momogo.group.domain

import com.mogumogu.momogo.global.config.JpaConfig
import com.mogumogu.momogo.user.domain.User
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.engine.concurrency.TestExecutionMode
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.shouldBe
import jakarta.persistence.EntityManager
import org.hibernate.exception.ConstraintViolationException
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
class GroupEntityTest(
    private val entityManager: EntityManager,
) : BehaviorSpec({
    testExecutionMode = TestExecutionMode.Sequential

    given("그룹과 그룹 멤버가 있으면") {
        `when`("JPA 엔티티로 저장할 때") {
            then("연관관계와 감사 시각을 포함해 다시 조회할 수 있다") {
                val user = User(_nickname = "모고")
                val group = Group(
                    _name = "모고모고",
                    _inviteCode = InviteCode(_value = "AAA111"),
                )
                val groupMember = GroupMember(
                    _group = group,
                    _user = user,
                )

                entityManager.persist(user)
                entityManager.persist(group)
                entityManager.persist(groupMember)
                entityManager.flush()

                val groupId = requireNotNull(group.id)
                val groupMemberId = requireNotNull(groupMember.id)
                val userId = requireNotNull(user.id)
                entityManager.clear()

                val savedGroup = entityManager.find(Group::class.java, groupId)
                val savedGroupMember = entityManager.find(GroupMember::class.java, groupMemberId)

                savedGroup.name shouldBe "모고모고"
                savedGroup.inviteCode shouldBe InviteCode(_value = "AAA111")
                savedGroup.updatedAt shouldBeGreaterThanOrEqualTo savedGroup.createdAt
                savedGroupMember.group.id shouldBe groupId
                savedGroupMember.user.id shouldBe userId
                savedGroupMember.deletedAt shouldBe null
                savedGroupMember.updatedAt shouldBeGreaterThanOrEqualTo savedGroupMember.createdAt
            }
        }
    }

    given("저장된 그룹과 그룹 멤버가 있으면") {
        `when`("그룹 정보와 멤버십 상태를 변경할 때") {
            then("변경된 상태가 데이터베이스에 반영된다") {
                val user = User(_nickname = "모고")
                val group = Group(
                    _name = "모고모고",
                    _inviteCode = InviteCode(_value = "BBB222"),
                )
                val groupMember = GroupMember(
                    _group = group,
                    _user = user,
                )
                entityManager.persist(user)
                entityManager.persist(group)
                entityManager.persist(groupMember)
                entityManager.flush()

                val groupId = requireNotNull(group.id)
                val groupMemberId = requireNotNull(groupMember.id)
                val deletedAt = Instant.parse("2030-01-01T00:00:00Z")

                group.updateName("변경된 그룹")
                group.regenerateInviteCode(InviteCode(_value = "CCC333"))
                groupMember.leave(deletedAt)
                entityManager.flush()
                entityManager.clear()

                val savedGroup = entityManager.find(Group::class.java, groupId)
                val savedGroupMember = entityManager.find(GroupMember::class.java, groupMemberId)

                savedGroup.name shouldBe "변경된 그룹"
                savedGroup.inviteCode shouldBe InviteCode(_value = "CCC333")
                savedGroupMember.deletedAt shouldBe deletedAt
                savedGroupMember.isActive() shouldBe false
            }
        }
    }

    given("동일 그룹에 가입한 회원이 있으면") {
        `when`("같은 회원의 멤버십을 다시 저장할 때") {
            then("복합 고유 제약으로 저장을 거부한다") {
                val user = User(_nickname = "모고")
                val group = Group(
                    _name = "모고모고",
                    _inviteCode = InviteCode(_value = "DDD444"),
                )
                entityManager.persist(user)
                entityManager.persist(group)
                entityManager.persist(
                    GroupMember(
                        _group = group,
                        _user = user,
                    ),
                )
                entityManager.flush()

                shouldThrow<ConstraintViolationException> {
                    entityManager.persist(
                        GroupMember(
                            _group = group,
                            _user = user,
                        ),
                    )
                    entityManager.flush()
                }
            }
        }
    }

    given("이미 저장된 초대 코드가 있으면") {
        `when`("같은 초대 코드로 그룹을 저장할 때") {
            then("고유 제약으로 저장을 거부한다") {
                entityManager.persist(
                    Group(
                        _name = "첫 번째 그룹",
                        _inviteCode = InviteCode(_value = "EEE555"),
                    ),
                )
                entityManager.flush()

                shouldThrow<ConstraintViolationException> {
                    entityManager.persist(
                        Group(
                            _name = "두 번째 그룹",
                            _inviteCode = InviteCode(_value = "EEE555"),
                        ),
                    )
                    entityManager.flush()
                }
            }
        }
    }

    given("그룹 엔티티의 영속 필드가 캡슐화되어 있으면") {
        then("공개 setter 없이 JPA 기본 생성자와 getter만 제공한다") {
            listOf(
                Group::class.java,
                GroupMember::class.java,
                InviteCode::class.java,
            ).forEach { entityClass ->
                entityClass.constructors.any { constructor ->
                    constructor.parameterCount == 0
                } shouldBe true
                entityClass.methods.none { method ->
                    method.name.startsWith("set")
                } shouldBe true
            }
        }
    }
})
