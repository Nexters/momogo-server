package com.mogumogu.momogo.user.presentation

import com.mogumogu.momogo.global.token.JwtProperties
import com.mogumogu.momogo.global.token.RefreshTokenProvider
import com.mogumogu.momogo.user.domain.LoginProvider
import com.mogumogu.momogo.user.domain.RefreshToken
import com.mogumogu.momogo.user.infra.LoginAccountRepository
import com.mogumogu.momogo.user.infra.RefreshTokenRepository
import com.mogumogu.momogo.user.infra.UserRepository
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.engine.concurrency.TestExecutionMode
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.*
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ApplyExtension(SpringExtension::class)
class AuthUserApiIntegrationTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val userRepository: UserRepository,
    private val loginAccountRepository: LoginAccountRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val refreshTokenProvider: RefreshTokenProvider,
    private val jwtEncoder: JwtEncoder,
    private val jwtDecoder: JwtDecoder,
    private val jwtProperties: JwtProperties,
    private val clock: Clock,
) : BehaviorSpec({
    testExecutionMode = TestExecutionMode.Sequential

    fun cleanDatabase() {
        refreshTokenRepository.deleteAllInBatch()
        loginAccountRepository.deleteAllInBatch()
        userRepository.deleteAllInBatch()
    }

    fun json(value: Any): String = objectMapper.writeValueAsString(value)

    fun performJson(
        request: MockHttpServletRequestBuilder,
        content: String,
    ): MockHttpServletResponse =
        mockMvc.perform(
            request
                .contentType(MediaType.APPLICATION_JSON)
                .content(content),
        ).andReturn().response

    fun register(
        providerToken: String,
        nickname: String = "모모",
        provider: String = "GUEST",
    ): MockHttpServletResponse =
        performJson(
            post("/api/v1/user/register"),
            json(
                mapOf(
                    "provider" to provider,
                    "providerToken" to providerToken,
                    "nickname" to nickname,
                ),
            ),
        )

    fun login(
        providerToken: String,
        provider: String = "GUEST",
    ): MockHttpServletResponse =
        performJson(
            post("/api/v1/auth/login"),
            json(
                mapOf(
                    "provider" to provider,
                    "providerToken" to providerToken,
                ),
            ),
        )

    fun reissue(refreshToken: String): MockHttpServletResponse =
        performJson(
            post("/api/v1/auth/reissue"),
            json(mapOf("refreshToken" to refreshToken)),
        )

    fun logout(refreshToken: String): MockHttpServletResponse =
        performJson(
            delete("/api/v1/auth/logout"),
            json(mapOf("refreshToken" to refreshToken)),
        )

    fun updateNickname(
        accessToken: String?,
        nickname: String,
    ): MockHttpServletResponse {
        val request = patch("/api/v1/user")
        if (accessToken != null) {
            request.header("Authorization", "Bearer $accessToken")
        }
        return performJson(request, json(mapOf("nickname" to nickname)))
    }

    fun withdraw(accessToken: String): MockHttpServletResponse =
        mockMvc.perform(
            delete("/api/v1/user")
                .header("Authorization", "Bearer $accessToken"),
        ).andReturn().response

    fun assertJsonResponse(
        response: MockHttpServletResponse,
        status: HttpStatus = HttpStatus.OK,
    ) {
        response.status shouldBe status.value()
        response.contentType shouldBe MediaType.APPLICATION_JSON_VALUE
    }

    fun assertProblem(
        response: MockHttpServletResponse,
        status: HttpStatus,
        detail: String,
    ) {
        response.status shouldBe status.value()
        response.contentType shouldBe MediaType.APPLICATION_PROBLEM_JSON_VALUE
        val body = objectMapper.readTree(response.contentAsString)
        body["status"].intValue() shouldBe status.value()
        body["detail"].stringValue() shouldBe detail
    }

    fun issueCustomAccessToken(
        subject: String,
        issuedAtOffset: Duration,
        expiresAtOffset: Duration,
    ): String {
        val now = clock.instant()
        val headers = JwsHeader
            .with(MacAlgorithm.HS256)
            .type("JWT")
            .build()
        val claims = JwtClaimsSet
            .builder()
            .issuer(jwtProperties.issuer)
            .subject(subject)
            .issuedAt(now.plus(issuedAtOffset))
            .expiresAt(now.plus(expiresAtOffset))
            .build()

        return jwtEncoder
            .encode(JwtEncoderParameters.from(headers, claims))
            .tokenValue
    }

    fun tamper(token: String): String {
        val parts = token.split(".").toMutableList()
        val signature = parts[2]
        parts[2] = (if (signature.startsWith("A")) "B" else "A") + signature.drop(1)
        return parts.joinToString(".")
    }

    fun runConcurrently(request: () -> MockHttpServletResponse): List<MockHttpServletResponse> {
        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)

        return try {
            val futures = (1..2).map {
                executor.submit<MockHttpServletResponse> {
                    ready.countDown()
                    check(start.await(5, TimeUnit.SECONDS)) {
                        "동시 요청 시작 신호를 받지 못했습니다."
                    }
                    request()
                }
            }
            check(ready.await(5, TimeUnit.SECONDS)) {
                "동시 요청 스레드가 준비되지 않았습니다."
            }
            start.countDown()
            futures.map { future -> future.get(15, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }

    given("유효한 Guest 회원가입 요청이 있으면") {
        `when`("UUID가 아닌 원문 providerToken과 공백이 있는 닉네임으로 가입할 때") {
            then("원문 계정을 저장하고 3일 access token과 해시로 저장한 refresh token을 반환한다") {
                cleanDatabase()
                val providerToken = "  Not-A-UUID/MiXeD.Token  "
                val beforeIssue = clock.instant()

                val response = register(
                    providerToken = providerToken,
                    nickname = "  모모  ",
                )
                val afterIssue = clock.instant()

                assertJsonResponse(response)
                val body = objectMapper.readTree(response.contentAsString)
                body.propertyNames().toSet() shouldBe setOf(
                    "userId",
                    "nickname",
                    "accessToken",
                    "refreshToken",
                )
                val userId = body["userId"].longValue()
                val accessToken = body["accessToken"].stringValue()
                val refreshToken = body["refreshToken"].stringValue()
                (userId > 0) shouldBe true
                body["nickname"].stringValue() shouldBe "모모"
                refreshToken.matches(Regex("^[A-Za-z0-9_-]{43}$")) shouldBe true

                val jwt = jwtDecoder.decode(accessToken)
                jwt.subject shouldBe userId.toString()
                jwt.claims["iss"] shouldBe jwtProperties.issuer
                Duration.between(
                    requireNotNull(jwt.issuedAt),
                    requireNotNull(jwt.expiresAt),
                ) shouldBe Duration.ofDays(3)

                val savedAccount = loginAccountRepository.findByProviderAndProviderId(
                    LoginProvider.GUEST,
                    providerToken,
                )
                savedAccount?.providerId shouldBe providerToken
                loginAccountRepository.findByProviderAndProviderId(
                    LoginProvider.GUEST,
                    providerToken.trim(),
                ) shouldBe null
                loginAccountRepository.findByProviderAndProviderId(
                    LoginProvider.GUEST,
                    providerToken.lowercase(),
                ) shouldBe null

                val savedRefreshToken = refreshTokenRepository.findByTokenHash(
                    refreshTokenProvider.hash(refreshToken),
                )
                savedRefreshToken shouldNotBe null
                savedRefreshToken?.tokenHash shouldNotBe refreshToken
                refreshTokenRepository.findAll().none { it.tokenHash == refreshToken } shouldBe true
                val expiresAt = requireNotNull(savedRefreshToken).expiresAt
                expiresAt.isBefore(beforeIssue.plus(Duration.ofDays(30))) shouldBe false
                expiresAt.isAfter(afterIssue.plus(Duration.ofDays(30))) shouldBe false
            }
        }
    }

    given("원문 Guest 로그인 계정이 등록되어 있으면") {
        `when`("같은 원문과 정규화된 다른 값으로 각각 로그인할 때") {
            then("exact equality인 원문만 로그인된다") {
                cleanDatabase()
                val providerToken = "  Guest/MiXeD.Not-UUID  "
                val registerBody = objectMapper.readTree(
                    register(providerToken).contentAsString,
                )

                val exactResponse = login(providerToken)
                assertJsonResponse(exactResponse)
                val exactBody = objectMapper.readTree(exactResponse.contentAsString)
                exactBody.propertyNames().toSet() shouldBe setOf(
                    "userId",
                    "nickname",
                    "accessToken",
                    "refreshToken",
                )
                exactBody["userId"].longValue() shouldBe registerBody["userId"].longValue()
                exactBody["nickname"].stringValue() shouldBe "모모"

                listOf(
                    providerToken.trim(),
                    providerToken.lowercase(),
                ).forEach { changedToken ->
                    assertProblem(
                        response = login(changedToken),
                        status = HttpStatus.UNAUTHORIZED,
                        detail = "인증 정보가 올바르지 않습니다.",
                    )
                }
            }
        }
    }

    given("이미 등록된 Guest 로그인 계정이 있으면") {
        `when`("같은 providerToken으로 다시 가입할 때") {
            then("409를 반환하고 사용자와 로그인 계정을 추가하지 않는다") {
                cleanDatabase()
                val providerToken = "duplicate-guest"
                assertJsonResponse(register(providerToken))

                assertProblem(
                    response = register(providerToken),
                    status = HttpStatus.CONFLICT,
                    detail = "이미 등록된 로그인 계정입니다.",
                )
                userRepository.count() shouldBe 1L
                loginAccountRepository.count() shouldBe 1L
            }
        }

        `when`("동시에 두 번 가입할 때") {
            then("DB unique constraint로 하나만 성공하고 하나는 409가 된다") {
                cleanDatabase()
                val providerToken = "concurrent-duplicate-guest"

                val responses = runConcurrently {
                    register(providerToken)
                }

                responses.map { it.status }.sorted() shouldContainExactly listOf(
                    HttpStatus.OK.value(),
                    HttpStatus.CONFLICT.value(),
                )
                userRepository.count() shouldBe 1L
                loginAccountRepository.count() shouldBe 1L
                refreshTokenRepository.count() shouldBe 1L
            }
        }
    }

    given("GUEST 이외의 로그인 provider가 요청되면") {
        `when`("회원가입과 로그인을 요청할 때") {
            then("미래 확장 enum을 실제 인증 provider로 허용하지 않는다") {
                cleanDatabase()

                listOf("KAKAO", "NAVER", "APPLE").forEach { provider ->
                    assertProblem(
                        response = register(
                            providerToken = "provider-token-$provider",
                            provider = provider,
                        ),
                        status = HttpStatus.BAD_REQUEST,
                        detail = "지원하지 않는 로그인 제공자입니다.",
                    )
                    assertProblem(
                        response = login(
                            providerToken = "provider-token-$provider",
                            provider = provider,
                        ),
                        status = HttpStatus.BAD_REQUEST,
                        detail = "지원하지 않는 로그인 제공자입니다.",
                    )
                }
                userRepository.count() shouldBe 0L
            }
        }
    }

    given("등록되지 않은 Guest 로그인 정보가 있으면") {
        `when`("로그인을 요청할 때") {
            then("401 ProblemDetail을 반환한다") {
                cleanDatabase()

                assertProblem(
                    response = login("unknown-guest"),
                    status = HttpStatus.UNAUTHORIZED,
                    detail = "인증 정보가 올바르지 않습니다.",
                )
            }
        }
    }

    given("인증된 사용자가 있으면") {
        `when`("앞뒤 공백이 있는 닉네임으로 변경할 때") {
            then("공백을 제거한 닉네임과 사용자 ID를 JSON으로 반환한다") {
                cleanDatabase()
                val registerBody = objectMapper.readTree(
                    register("nickname-update-guest").contentAsString,
                )
                val userId = registerBody["userId"].longValue()
                val accessToken = registerBody["accessToken"].stringValue()

                val response = updateNickname(accessToken, "  새 닉네임  ")

                assertJsonResponse(response)
                val body = objectMapper.readTree(response.contentAsString)
                body.propertyNames().toSet() shouldBe setOf("userId", "nickname")
                body["userId"].longValue() shouldBe userId
                body["nickname"].stringValue() shouldBe "새 닉네임"
                userRepository.findById(userId).orElseThrow().nickname shouldBe "새 닉네임"
            }
        }

        `when`("인증 없이 또는 유효하지 않은 닉네임으로 변경할 때") {
            then("각각 401과 400으로 거부한다") {
                cleanDatabase()
                val registerBody = objectMapper.readTree(
                    register("nickname-validation-guest").contentAsString,
                )
                val accessToken = registerBody["accessToken"].stringValue()

                assertProblem(
                    response = updateNickname(null, "새 닉네임"),
                    status = HttpStatus.UNAUTHORIZED,
                    detail = "인증 정보가 올바르지 않습니다.",
                )
                listOf("   ", "가".repeat(13)).forEach { invalidNickname ->
                    assertProblem(
                        response = updateNickname(accessToken, invalidNickname),
                        status = HttpStatus.BAD_REQUEST,
                        detail = "요청 값이 올바르지 않습니다.",
                    )
                }
            }
        }
    }

    given("access token 인증을 사용하는 사용자 API가 있으면") {
        `when`("서명 변조, 만료 또는 Long이 아닌 sub 토큰으로 요청할 때") {
            then("내부 JWT 정보와 토큰 원문을 숨긴 401 ProblemDetail을 반환한다") {
                cleanDatabase()
                val registerBody = objectMapper.readTree(
                    register("invalid-access-token-guest").contentAsString,
                )
                val userId = registerBody["userId"].longValue()
                val validAccessToken = registerBody["accessToken"].stringValue()
                val invalidTokens = listOf(
                    tamper(validAccessToken),
                    issueCustomAccessToken(
                        subject = userId.toString(),
                        issuedAtOffset = Duration.ofDays(-2),
                        expiresAtOffset = Duration.ofDays(-1),
                    ),
                    issueCustomAccessToken(
                        subject = "not-a-long",
                        issuedAtOffset = Duration.ZERO,
                        expiresAtOffset = Duration.ofDays(1),
                    ),
                    "sensitive-malformed-access-token",
                )

                invalidTokens.forEach { invalidToken ->
                    val response = updateNickname(invalidToken, "변경 불가")
                    assertProblem(
                        response = response,
                        status = HttpStatus.UNAUTHORIZED,
                        detail = "인증 정보가 올바르지 않습니다.",
                    )
                    response.contentAsString.contains(invalidToken) shouldBe false
                    response.contentAsString.contains("Jwt") shouldBe false
                }
            }
        }
    }

    given("한 사용자가 여러 번 인증하면") {
        `when`("회원가입 후 다시 로그인할 때") {
            then("서로 다른 활성 refresh token을 발급하고 DB에는 SHA-256 해시만 저장한다") {
                cleanDatabase()
                val providerToken = "multiple-refresh-token-guest"
                val registerBody = objectMapper.readTree(register(providerToken).contentAsString)
                val loginBody = objectMapper.readTree(login(providerToken).contentAsString)
                val firstToken = registerBody["refreshToken"].stringValue()
                val secondToken = loginBody["refreshToken"].stringValue()

                registerBody["userId"].longValue() shouldBe loginBody["userId"].longValue()
                firstToken shouldNotBe secondToken
                val savedTokens = refreshTokenRepository.findAll()
                savedTokens.size shouldBe 2
                savedTokens.map { it.tokenHash }.toSet() shouldBe setOf(
                    refreshTokenProvider.hash(firstToken),
                    refreshTokenProvider.hash(secondToken),
                )
                savedTokens.none { it.tokenHash == firstToken || it.tokenHash == secondToken } shouldBe true
                savedTokens.all { it.isActive(clock.instant()) } shouldBe true
            }
        }
    }

    given("활성 refresh token이 있으면") {
        `when`("토큰을 재발급하고 기존 토큰을 다시 사용할 때") {
            then("기존 토큰을 폐기하고 새 토큰을 저장하며 재사용은 401로 거부한다") {
                cleanDatabase()
                val registerBody = objectMapper.readTree(
                    register("rotation-guest").contentAsString,
                )
                val oldRefreshToken = registerBody["refreshToken"].stringValue()

                val response = reissue(oldRefreshToken)

                assertJsonResponse(response)
                val body = objectMapper.readTree(response.contentAsString)
                body.propertyNames().toSet() shouldBe setOf(
                    "accessToken",
                    "refreshToken",
                )
                val newRefreshToken = body["refreshToken"].stringValue()
                newRefreshToken shouldNotBe oldRefreshToken
                jwtDecoder.decode(body["accessToken"].stringValue()).subject shouldBe
                        registerBody["userId"].longValue().toString()

                val oldSavedToken = refreshTokenRepository.findByTokenHash(
                    refreshTokenProvider.hash(oldRefreshToken),
                )
                val newSavedToken = refreshTokenRepository.findByTokenHash(
                    refreshTokenProvider.hash(newRefreshToken),
                )
                oldSavedToken?.revokedAt shouldNotBe null
                newSavedToken shouldNotBe null
                requireNotNull(newSavedToken).isActive(clock.instant()) shouldBe true

                assertProblem(
                    response = reissue(oldRefreshToken),
                    status = HttpStatus.UNAUTHORIZED,
                    detail = "유효하지 않은 리프레시 토큰입니다.",
                )
            }
        }

        `when`("같은 refresh token으로 동시에 두 번 재발급할 때") {
            then("DB 잠금으로 하나만 성공하고 다른 요청은 401이 된다") {
                cleanDatabase()
                val registerBody = objectMapper.readTree(
                    register("concurrent-rotation-guest").contentAsString,
                )
                val refreshToken = registerBody["refreshToken"].stringValue()

                val responses = runConcurrently {
                    reissue(refreshToken)
                }

                responses.map { it.status }.sorted() shouldContainExactly listOf(
                    HttpStatus.OK.value(),
                    HttpStatus.UNAUTHORIZED.value(),
                )
                val savedTokens = refreshTokenRepository.findAll()
                savedTokens.size shouldBe 2
                savedTokens.count { it.isActive(clock.instant()) } shouldBe 1
                savedTokens.count { it.revokedAt != null } shouldBe 1
            }
        }
    }

    given("발급된 refresh token과 access token이 있으면") {
        `when`("로그아웃을 반복하거나 알 수 없고 만료된 토큰으로 로그아웃할 때") {
            then("항상 실제 JSON 객체를 반환하고 access token은 계속 사용할 수 있다") {
                cleanDatabase()
                val registerBody = objectMapper.readTree(
                    register("logout-guest").contentAsString,
                )
                val userId = registerBody["userId"].longValue()
                val accessToken = registerBody["accessToken"].stringValue()
                val refreshToken = registerBody["refreshToken"].stringValue()
                val user = userRepository.findById(userId).orElseThrow()
                val expiredRawToken = "expired-refresh-token"
                refreshTokenRepository.saveAndFlush(
                    RefreshToken(
                        _user = user,
                        _tokenHash = refreshTokenProvider.hash(expiredRawToken),
                        _expiresAt = clock.instant().minusSeconds(1),
                    ),
                )

                assertProblem(
                    response = reissue(expiredRawToken),
                    status = HttpStatus.UNAUTHORIZED,
                    detail = "유효하지 않은 리프레시 토큰입니다.",
                )
                listOf(
                    logout(refreshToken),
                    logout(refreshToken),
                    logout("unknown-refresh-token"),
                    logout(expiredRawToken),
                ).forEach { response ->
                    assertJsonResponse(response)
                    response.contentAsString shouldBe "{}"
                }

                assertJsonResponse(updateNickname(accessToken, "로그아웃 후"))
                assertProblem(
                    response = reissue(refreshToken),
                    status = HttpStatus.UNAUTHORIZED,
                    detail = "유효하지 않은 리프레시 토큰입니다.",
                )
            }
        }
    }

    given("로그인 계정과 여러 refresh token을 가진 사용자가 있으면") {
        `when`("access token으로 회원 탈퇴할 때") {
            then("연관 데이터를 모두 물리 삭제하고 남은 access token은 사용자 조회에서 404가 된다") {
                cleanDatabase()
                val providerToken = "withdraw-guest"
                val registerBody = objectMapper.readTree(register(providerToken).contentAsString)
                val loginBody = objectMapper.readTree(login(providerToken).contentAsString)
                val accessToken = registerBody["accessToken"].stringValue()
                val refreshToken = loginBody["refreshToken"].stringValue()

                val response = withdraw(accessToken)

                assertJsonResponse(response)
                response.contentAsString shouldBe "{}"
                userRepository.count() shouldBe 0L
                loginAccountRepository.count() shouldBe 0L
                refreshTokenRepository.count() shouldBe 0L

                assertProblem(
                    response = updateNickname(accessToken, "탈퇴 후"),
                    status = HttpStatus.NOT_FOUND,
                    detail = "사용자를 찾을 수 없습니다.",
                )
                assertProblem(
                    response = reissue(refreshToken),
                    status = HttpStatus.UNAUTHORIZED,
                    detail = "유효하지 않은 리프레시 토큰입니다.",
                )
            }
        }
    }

    given("API 경계에 잘못된 JSON이나 요청값이 들어오면") {
        `when`("JSON 파싱, enum 변환, 필수 필드 또는 공백 검증이 실패할 때") {
            then("내부 파싱 메시지를 숨긴 400 ProblemDetail을 반환한다") {
                cleanDatabase()
                val invalidResponses = listOf(
                    performJson(
                        post("/api/v1/user/register"),
                        """{"provider":"GUEST","providerToken":""",
                    ),
                    performJson(
                        post("/api/v1/auth/login"),
                        """{"provider":"UNKNOWN","providerToken":"token"}""",
                    ),
                    performJson(
                        post("/api/v1/auth/reissue"),
                        "{}",
                    ),
                    performJson(
                        delete("/api/v1/auth/logout"),
                        """{"refreshToken":"   "}""",
                    ),
                    register(
                        providerToken = " ",
                        nickname = "모모",
                    ),
                    register(
                        providerToken = "a".repeat(256),
                        nickname = "모모",
                    ),
                    register(
                        providerToken = "valid-provider-token",
                        nickname = "가".repeat(13),
                    ),
                    register(
                        providerToken = "blank-nickname-provider-token",
                        nickname = "   ",
                    ),
                )

                invalidResponses.forEach { response ->
                    assertProblem(
                        response = response,
                        status = HttpStatus.BAD_REQUEST,
                        detail = "요청 값이 올바르지 않습니다.",
                    )
                    response.contentAsString.contains("Json") shouldBe false
                    response.contentAsString.contains("LoginProvider") shouldBe false
                }
                userRepository.count() shouldBe 0L
            }
        }
    }

})
