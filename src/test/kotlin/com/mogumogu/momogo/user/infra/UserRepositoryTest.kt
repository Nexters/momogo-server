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
})
