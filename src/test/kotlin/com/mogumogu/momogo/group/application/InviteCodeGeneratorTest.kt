package com.mogumogu.momogo.group.application

import com.mogumogu.momogo.global.config.JpaConfig
import com.mogumogu.momogo.group.domain.Group
import com.mogumogu.momogo.group.domain.InviteCode
import com.mogumogu.momogo.group.infra.GroupRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.util.ArrayDeque

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaConfig::class, InviteCodeGenerator::class)
@ApplyExtension(SpringExtension::class)
class InviteCodeGeneratorTest(
    private val inviteCodeGenerator: InviteCodeGenerator,
    private val groupRepository: GroupRepository,
) : BehaviorSpec({

    given("이미 사용 중인 초대 코드가 있으면") {
        `when`("다음 후보 코드를 생성할 때") {
            then("중복 코드를 건너뛰고 사용 가능한 코드를 반환한다") {
                groupRepository.saveAndFlush(
                    Group(
                        _name = "기존 그룹",
                        _inviteCode = InviteCode(_value = "USED12"),
                    ),
                )
                val candidates = ArrayDeque(
                    listOf(
                        InviteCode(_value = "USED12"),
                        InviteCode(_value = "NEW456"),
                    ),
                )
                var generationCount = 0

                val inviteCode = inviteCodeGenerator.generate {
                    generationCount += 1
                    candidates.removeFirst()
                }

                inviteCode shouldBe InviteCode(_value = "NEW456")
                generationCount shouldBe 2
            }
        }
    }

    given("중복 초대 코드만 계속 생성되면") {
        `when`("최대 생성 횟수를 모두 사용할 때") {
            then("예외를 발생시킨다") {
                groupRepository.saveAndFlush(
                    Group(
                        _name = "기존 그룹",
                        _inviteCode = InviteCode(_value = "USED12"),
                    ),
                )
                var generationCount = 0

                val exception = shouldThrow<IllegalStateException> {
                    inviteCodeGenerator.generate {
                        generationCount += 1
                        InviteCode(_value = "USED12")
                    }
                }

                exception.message shouldBe "사용 가능한 초대 코드를 생성하지 못했습니다."
                generationCount shouldBe 10
            }
        }
    }
})
