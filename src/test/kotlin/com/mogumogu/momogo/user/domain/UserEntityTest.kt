package com.mogumogu.momogo.user.domain

import com.mogumogu.momogo.global.config.JpaConfig
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.engine.concurrency.TestExecutionMode
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.shouldBe
import jakarta.persistence.Column
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
class UserEntityTest(
    private val entityManager: EntityManager,
) : BehaviorSpec({
    testExecutionMode = TestExecutionMode.Sequential

    given("회원과 로그인 계정, 리프레시 토큰이 있으면") {
        `when`("하나의 도메인 모델을 JPA 엔티티로 저장할 때") {
            then("연관관계와 감사 시각을 포함해 다시 조회할 수 있다") {
                val user = User(_nickname = "  모고  ")
                val loginAccount = LoginAccount(
                    _user = user,
                    _provider = LoginProvider.APPLE,
                    _providerId = "apple-user-1",
                )
                val expiresAt = Instant.parse("2030-01-01T00:00:00Z")
                val refreshToken = RefreshToken(
                    _user = user,
                    _tokenHash = "a".repeat(64),
                    _expiresAt = expiresAt,
                )

                entityManager.persist(user)
                entityManager.persist(loginAccount)
                entityManager.persist(refreshToken)
                entityManager.flush()

                val userId = requireNotNull(user.id)
                val loginAccountId = requireNotNull(loginAccount.id)
                val refreshTokenId = requireNotNull(refreshToken.id)
                entityManager.clear()

                val savedUser = entityManager.find(User::class.java, userId)
                val savedLoginAccount = entityManager.find(LoginAccount::class.java, loginAccountId)
                val savedRefreshToken = entityManager.find(RefreshToken::class.java, refreshTokenId)

                savedUser.nickname shouldBe "모고"
                savedUser.updatedAt shouldBeGreaterThanOrEqualTo savedUser.createdAt
                savedLoginAccount.user.id shouldBe userId
                savedLoginAccount.provider shouldBe LoginProvider.APPLE
                savedLoginAccount.providerId shouldBe "apple-user-1"
                savedLoginAccount.updatedAt shouldBeGreaterThanOrEqualTo savedLoginAccount.createdAt
                savedRefreshToken.user.id shouldBe userId
                savedRefreshToken.tokenHash shouldBe "a".repeat(64)
                savedRefreshToken.expiresAt shouldBe expiresAt
                savedRefreshToken.revokedAt shouldBe null
                savedRefreshToken.updatedAt shouldBeGreaterThanOrEqualTo savedRefreshToken.createdAt
            }
        }
    }

    given("저장된 회원과 리프레시 토큰이 있으면") {
        `when`("닉네임을 바꾸고 토큰을 무효화할 때") {
            then("도메인 변경이 데이터베이스에 반영된다") {
                val user = User(_nickname = "변경 전")
                val refreshToken = RefreshToken(
                    _user = user,
                    _tokenHash = "b".repeat(64),
                    _expiresAt = Instant.parse("2030-01-01T00:00:00Z"),
                )
                entityManager.persist(user)
                entityManager.persist(refreshToken)
                entityManager.flush()

                val userId = requireNotNull(user.id)
                val refreshTokenId = requireNotNull(refreshToken.id)
                val revokedAt = Instant.parse("2029-01-01T00:00:00Z")

                user.updateNickname("변경 후")
                refreshToken.revoke(revokedAt)
                entityManager.flush()
                entityManager.clear()

                val savedUser = entityManager.find(User::class.java, userId)
                val savedRefreshToken = entityManager.find(RefreshToken::class.java, refreshTokenId)

                savedUser.nickname shouldBe "변경 후"
                savedRefreshToken.revokedAt shouldBe revokedAt
                savedRefreshToken.isActive(revokedAt) shouldBe false
            }
        }
    }

    given("이미 저장된 리프레시 토큰 해시가 있으면") {
        `when`("같은 해시를 다시 저장할 때") {
            then("고유 제약으로 저장을 거부한다") {
                val user = User(_nickname = "모고")
                val tokenHash = "c".repeat(64)
                val expiresAt = Instant.parse("2030-01-01T00:00:00Z")
                entityManager.persist(user)
                entityManager.persist(
                    RefreshToken(
                        _user = user,
                        _tokenHash = tokenHash,
                        _expiresAt = expiresAt,
                    ),
                )
                entityManager.flush()

                shouldThrow<ConstraintViolationException> {
                    entityManager.persist(
                        RefreshToken(
                            _user = user,
                            _tokenHash = tokenHash,
                            _expiresAt = expiresAt,
                        ),
                    )
                    entityManager.flush()
                }
            }
        }
    }

    given("이미 저장된 로그인 제공자 계정이 있으면") {
        `when`("같은 provider와 providerId를 다시 저장할 때") {
            then("복합 고유 제약으로 저장을 거부한다") {
                val firstUser = User(_nickname = "첫 회원")
                val secondUser = User(_nickname = "둘째 회원")
                entityManager.persist(firstUser)
                entityManager.persist(secondUser)
                entityManager.persist(
                    LoginAccount(
                        _user = firstUser,
                        _provider = LoginProvider.GUEST,
                        _providerId = "guest-token",
                    ),
                )
                entityManager.flush()

                shouldThrow<ConstraintViolationException> {
                    entityManager.persist(
                        LoginAccount(
                            _user = secondUser,
                            _provider = LoginProvider.GUEST,
                            _providerId = "guest-token",
                        ),
                    )
                    entityManager.flush()
                }
            }
        }
    }

    given("엔티티의 영속 필드가 캡슐화되어 있으면") {
        then("공개 setter 없이 JPA 기본 생성자와 getter만 제공한다") {
            val entityClasses = listOf(
                User::class.java,
                LoginAccount::class.java,
                RefreshToken::class.java,
            )

            entityClasses.forEach { entityClass ->
                entityClass.constructors.any { constructor ->
                    constructor.parameterCount == 0
                } shouldBe true
                entityClass.methods.none { method ->
                    method.name.startsWith("set")
                } shouldBe true
            }
        }

        then("닉네임 컬럼 길이를 도메인 최대 길이와 같은 12자로 제한한다") {
            User::class.java
                .getDeclaredField("_nickname")
                .getAnnotation(Column::class.java)
                .length shouldBe 12
        }
    }
})
