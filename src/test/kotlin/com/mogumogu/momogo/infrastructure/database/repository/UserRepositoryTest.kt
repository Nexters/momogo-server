package com.mogumogu.momogo.infrastructure.database.repository

import com.mogumogu.momogo.domain.user.User
import com.mogumogu.momogo.infrastructure.database.entity.UserEntity
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
@ApplyExtension(SpringExtension::class)
class UserRepositoryTest(
    private val userRepository: UserRepository,
) : BehaviorSpec({

    given("UserRepository가 주어졌을 때") {
        `when`("User를 생성, 조회, 수정, 삭제하면") {
            then("각 변경 사항을 데이터베이스에 반영한다") {
                var userId: Long? = null

                try {
                    val savedUser = userRepository
                        .save(UserEntity.fromDomain(User(nickname = "모모")))
                        .toDomain()
                    userId = savedUser.id.shouldNotBeNull()

                    val foundUserEntity = userRepository.findById(userId).orElseThrow()
                    val foundUser = foundUserEntity.toDomain()
                    foundUser.nickname shouldBe "모모"

                    foundUser.changeNickname("고모")
                    foundUserEntity.nickname = foundUser.nickname
                    userRepository.save(foundUserEntity)

                    userRepository.findById(userId).orElseThrow().nickname shouldBe "고모"
                    userRepository.findAll().map { it.toDomain().id } shouldContainExactly listOf(userId)

                    userRepository.deleteById(userId)
                    userId = null

                    userRepository.findById(savedUser.id.shouldNotBeNull()).orElse(null).shouldBeNull()
                } finally {
                    userId?.let(userRepository::deleteById)
                }
            }
        }
    }
})
