package com.mogumogu.momogo.infrastructure.database.repository

import com.mogumogu.momogo.domain.user.LoginAccount
import com.mogumogu.momogo.domain.user.LoginProvider
import com.mogumogu.momogo.domain.user.User
import com.mogumogu.momogo.infrastructure.database.entity.LoginAccountEntity
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
class LoginAccountRepositoryTest(
    private val userRepository: UserRepository,
    private val loginAccountRepository: LoginAccountRepository,
) : BehaviorSpec({

    given("LoginAccountRepository가 주어졌을 때") {
        `when`("LoginAccount를 생성, 조회, 수정, 삭제하면") {
            then("각 변경 사항을 데이터베이스에 반영한다") {
                var userId: Long? = null
                var loginAccountId: Long? = null

                try {
                    val savedUser = userRepository
                        .save(UserEntity.fromDomain(User(nickname = "모모")))
                        .toDomain()
                    userId = savedUser.id.shouldNotBeNull()

                    val savedLoginAccount = loginAccountRepository
                        .save(
                            LoginAccountEntity.fromDomain(
                                LoginAccount(
                                    userId = userId,
                                    provider = LoginProvider.GUEST,
                                    providerId = "guest-device-id",
                                ),
                            ),
                        )
                        .toDomain()
                    loginAccountId = savedLoginAccount.id.shouldNotBeNull()

                    val foundLoginAccountEntity = loginAccountRepository
                        .findById(loginAccountId)
                        .orElseThrow()
                    val foundLoginAccount = foundLoginAccountEntity.toDomain()
                    foundLoginAccount.provider shouldBe LoginProvider.GUEST

                    foundLoginAccount.changeProvider(
                        provider = LoginProvider.APPLE,
                        providerId = "apple-user-id",
                    )
                    foundLoginAccountEntity.provider = foundLoginAccount.provider
                    foundLoginAccountEntity.providerId = foundLoginAccount.providerId
                    loginAccountRepository.save(foundLoginAccountEntity)

                    val updatedLoginAccount = loginAccountRepository
                        .findById(loginAccountId)
                        .orElseThrow()
                        .toDomain()
                    updatedLoginAccount.provider shouldBe LoginProvider.APPLE
                    updatedLoginAccount.providerId shouldBe "apple-user-id"
                    loginAccountRepository.findAllByUserId(userId)
                        .map { it.toDomain().id } shouldContainExactly listOf(loginAccountId)

                    loginAccountRepository.deleteById(loginAccountId)
                    loginAccountId = null
                    loginAccountRepository.findById(savedLoginAccount.id.shouldNotBeNull())
                        .orElse(null)
                        .shouldBeNull()

                    userRepository.deleteById(userId)
                    userId = null
                } finally {
                    loginAccountId?.let(loginAccountRepository::deleteById)
                    userId?.let(userRepository::deleteById)
                }
            }
        }
    }
})
