package com.mogumogu.momogo.user.infra

import com.mogumogu.momogo.global.config.JpaConfig
import com.mogumogu.momogo.user.domain.LoginAccount
import com.mogumogu.momogo.user.domain.LoginProvider
import com.mogumogu.momogo.user.domain.RefreshToken
import com.mogumogu.momogo.user.domain.User
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import jakarta.persistence.LockModeType
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.Lock
import org.springframework.test.context.ActiveProfiles
import java.time.Instant

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaConfig::class)
@ApplyExtension(SpringExtension::class)
class UserRepositoryTest(
    private val userRepository: UserRepository,
    private val loginAccountRepository: LoginAccountRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
) : BehaviorSpec({

    given("회원과 로그인 계정, 리프레시 토큰이 있으면") {
        `when`("각 Repository로 저장하고 조회할 때") {
            then("기본 JPA CRUD를 사용할 수 있다") {
                val user = userRepository.saveAndFlush(
                    User(_nickname = "모고"),
                )
                val userId = requireNotNull(user.id)

                val loginAccount = loginAccountRepository.saveAndFlush(
                    LoginAccount(
                        _user = user,
                        _provider = LoginProvider.KAKAO,
                        _providerId = "kakao-user-1",
                    ),
                )
                val loginAccountId = requireNotNull(loginAccount.id)

                val refreshToken = refreshTokenRepository.saveAndFlush(
                    RefreshToken(
                        _user = user,
                        _tokenHash = "d".repeat(64),
                        _expiresAt = Instant.parse("2030-01-01T00:00:00Z"),
                    ),
                )
                val refreshTokenId = requireNotNull(refreshToken.id)

                userRepository.findById(userId).orElseThrow().nickname shouldBe "모고"
                loginAccountRepository.findById(loginAccountId).orElseThrow().provider shouldBe LoginProvider.KAKAO
                refreshTokenRepository.findById(refreshTokenId).orElseThrow().tokenHash shouldBe "d".repeat(64)

                refreshTokenRepository.deleteById(refreshTokenId)
                refreshTokenRepository.flush()

                refreshTokenRepository.existsById(refreshTokenId) shouldBe false
            }
        }
    }

    given("원문 Guest 로그인 계정이 저장되어 있으면") {
        `when`("provider와 providerId로 조회할 때") {
            then("대소문자나 앞뒤 공백을 바꾸지 않고 exact equality로만 찾는다") {
                val user = userRepository.saveAndFlush(User(_nickname = "게스트"))
                val providerId = "  Not-A-UUID/Guest.Token  "
                val loginAccount = loginAccountRepository.saveAndFlush(
                    LoginAccount(
                        _user = user,
                        _provider = LoginProvider.GUEST,
                        _providerId = providerId,
                    ),
                )

                loginAccountRepository
                    .findByProviderAndProviderId(LoginProvider.GUEST, providerId)
                    ?.id shouldBe loginAccount.id
                loginAccountRepository
                    .existsByProviderAndProviderId(LoginProvider.GUEST, providerId) shouldBe true
                loginAccountRepository
                    .findByProviderAndProviderId(LoginProvider.GUEST, providerId.trim()) shouldBe null
                loginAccountRepository
                    .findByProviderAndProviderId(LoginProvider.GUEST, providerId.lowercase()) shouldBe null
                loginAccountRepository
                    .findByProviderAndProviderId(LoginProvider.APPLE, providerId) shouldBe null
            }
        }
    }

    given("저장된 리프레시 토큰 해시가 있으면") {
        `when`("일반 조회와 rotation용 잠금 조회를 호출할 때") {
            then("exact equality로 같은 토큰을 반환하고 잠금 조회에는 비관적 쓰기 잠금이 선언된다") {
                val user = userRepository.saveAndFlush(User(_nickname = "토큰 회원"))
                val tokenHash = "e".repeat(64)
                val refreshToken = refreshTokenRepository.saveAndFlush(
                    RefreshToken(
                        _user = user,
                        _tokenHash = tokenHash,
                        _expiresAt = Instant.parse("2030-01-01T00:00:00Z"),
                    ),
                )

                refreshTokenRepository.findByTokenHash(tokenHash)?.id shouldBe refreshToken.id
                refreshTokenRepository.findByTokenHash("f".repeat(64)) shouldBe null
                refreshTokenRepository.findByTokenHashForUpdate(tokenHash)?.id shouldBe refreshToken.id

                RefreshTokenRepository::class.java
                    .getMethod("findByTokenHashForUpdate", String::class.java)
                    .getAnnotation(Lock::class.java)
                    .value shouldBe LockModeType.PESSIMISTIC_WRITE
            }
        }
    }

    given("한 회원에게 로그인 계정과 여러 리프레시 토큰이 있으면") {
        `when`("회원 식별자로 연관 데이터를 삭제할 때") {
            then("다른 회원의 데이터는 유지하고 대상 회원의 데이터만 모두 삭제한다") {
                val targetUser = userRepository.saveAndFlush(User(_nickname = "탈퇴 회원"))
                val otherUser = userRepository.saveAndFlush(User(_nickname = "유지 회원"))
                val targetUserId = requireNotNull(targetUser.id)

                loginAccountRepository.saveAllAndFlush(
                    listOf(
                        LoginAccount(
                            _user = targetUser,
                            _provider = LoginProvider.GUEST,
                            _providerId = "target-guest",
                        ),
                        LoginAccount(
                            _user = otherUser,
                            _provider = LoginProvider.GUEST,
                            _providerId = "other-guest",
                        ),
                    ),
                )
                refreshTokenRepository.saveAllAndFlush(
                    listOf(
                        RefreshToken(
                            _user = targetUser,
                            _tokenHash = "1".repeat(64),
                            _expiresAt = Instant.parse("2030-01-01T00:00:00Z"),
                        ),
                        RefreshToken(
                            _user = targetUser,
                            _tokenHash = "2".repeat(64),
                            _expiresAt = Instant.parse("2030-01-01T00:00:00Z"),
                        ),
                        RefreshToken(
                            _user = otherUser,
                            _tokenHash = "3".repeat(64),
                            _expiresAt = Instant.parse("2030-01-01T00:00:00Z"),
                        ),
                    ),
                )

                refreshTokenRepository.deleteAllByUser_Id(targetUserId) shouldBe 2
                loginAccountRepository.deleteAllByUser_Id(targetUserId) shouldBe 1

                refreshTokenRepository.findByTokenHash("1".repeat(64)) shouldBe null
                refreshTokenRepository.findByTokenHash("2".repeat(64)) shouldBe null
                refreshTokenRepository.findByTokenHash("3".repeat(64))?.user?.id shouldBe otherUser.id
                loginAccountRepository
                    .findByProviderAndProviderId(LoginProvider.GUEST, "target-guest") shouldBe null
                loginAccountRepository
                    .findByProviderAndProviderId(LoginProvider.GUEST, "other-guest")
                    ?.user
                    ?.id shouldBe otherUser.id
            }
        }
    }
})
