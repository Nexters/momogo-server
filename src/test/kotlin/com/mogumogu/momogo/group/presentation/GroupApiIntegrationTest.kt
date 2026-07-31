package com.mogumogu.momogo.group.presentation

import com.mogumogu.momogo.group.infra.GroupMemberRepository
import com.mogumogu.momogo.group.infra.GroupRepository
import com.mogumogu.momogo.photo.infra.PhotoGroupRepository
import com.mogumogu.momogo.photo.infra.PhotoRepository
import com.mogumogu.momogo.user.infra.LoginAccountRepository
import com.mogumogu.momogo.user.infra.RefreshTokenRepository
import com.mogumogu.momogo.user.infra.UserRepository
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.engine.concurrency.TestExecutionMode
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import tools.jackson.databind.ObjectMapper

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ApplyExtension(SpringExtension::class)
class GroupApiIntegrationTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val userRepository: UserRepository,
    private val loginAccountRepository: LoginAccountRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val photoRepository: PhotoRepository,
    private val photoGroupRepository: PhotoGroupRepository,
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
) : BehaviorSpec({
    testExecutionMode = TestExecutionMode.Sequential

    fun cleanDatabase() {
        photoGroupRepository.deleteAllInBatch()
        photoRepository.deleteAllInBatch()
        groupMemberRepository.deleteAllInBatch()
        groupRepository.deleteAllInBatch()
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

    fun register(providerToken: String): MockHttpServletResponse =
        performJson(
            post("/api/v1/user/register"),
            json(
                mapOf(
                    "provider" to "GUEST",
                    "providerToken" to providerToken,
                    "nickname" to "모모",
                ),
            ),
        )

    fun createGroupWithContent(
        accessToken: String?,
        content: String,
    ): MockHttpServletResponse {
        val request = post("/api/v1/groups")
        if (accessToken != null) {
            request.header("Authorization", "Bearer $accessToken")
        }
        return performJson(request, content)
    }

    fun createGroup(
        accessToken: String?,
        name: String,
    ): MockHttpServletResponse =
        createGroupWithContent(accessToken, json(mapOf("name" to name)))

    fun withdraw(accessToken: String): MockHttpServletResponse =
        mockMvc.perform(
            delete("/api/v1/user")
                .header("Authorization", "Bearer $accessToken"),
        ).andReturn().response

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

    given("인증된 사용자가 그룹명을 입력하면") {
        `when`("그룹을 생성할 때") {
            then("초대 코드가 있는 그룹과 생성자의 활성 멤버십을 함께 저장한다") {
                cleanDatabase()
                val registerResponse = register("group-creator")
                registerResponse.status shouldBe HttpStatus.OK.value()
                val registerBody = objectMapper.readTree(registerResponse.contentAsString)
                val userId = registerBody["userId"].longValue()
                val accessToken = registerBody["accessToken"].stringValue()

                val response = createGroup(accessToken, "모고모고")

                response.status shouldBe HttpStatus.OK.value()
                response.contentType shouldBe MediaType.APPLICATION_JSON_VALUE
                val body = objectMapper.readTree(response.contentAsString)
                body.propertyNames().toSet() shouldBe setOf("groupId", "groupName", "inviteCode")
                val groupId = body["groupId"].longValue()
                val inviteCode = body["inviteCode"].stringValue()
                (groupId > 0) shouldBe true
                body["groupName"].stringValue() shouldBe "모고모고"
                inviteCode.matches(Regex("^[A-Z0-9]{6}$")) shouldBe true

                val savedGroup = groupRepository.findById(groupId).orElseThrow()
                savedGroup.name shouldBe "모고모고"
                savedGroup.inviteCode.value shouldBe inviteCode
                val membership = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                membership shouldNotBe null
                requireNotNull(membership).isActive() shouldBe true
                membership.id shouldNotBe null
            }
        }
    }

    given("유효하지 않은 그룹명이 있으면") {
        `when`("공백, 누락 또는 255자를 초과한 이름으로 그룹을 생성할 때") {
            then("400을 반환하고 그룹과 멤버십을 저장하지 않는다") {
                cleanDatabase()
                val accessToken = objectMapper.readTree(
                    register("invalid-group-name").contentAsString,
                )["accessToken"].stringValue()

                listOf(
                    createGroup(accessToken, "   "),
                    createGroup(accessToken, "가".repeat(256)),
                    createGroupWithContent(accessToken, "{}"),
                ).forEach { response ->
                    assertProblem(
                        response = response,
                        status = HttpStatus.BAD_REQUEST,
                        detail = "요청 값이 올바르지 않습니다.",
                    )
                }
                groupRepository.count() shouldBe 0L
                groupMemberRepository.count() shouldBe 0L
            }
        }
    }

    given("인증 정보가 없으면") {
        `when`("그룹 생성을 요청할 때") {
            then("401을 반환하고 아무것도 저장하지 않는다") {
                cleanDatabase()

                assertProblem(
                    response = createGroup(null, "인증 없는 그룹"),
                    status = HttpStatus.UNAUTHORIZED,
                    detail = "인증 정보가 올바르지 않습니다.",
                )
                groupRepository.count() shouldBe 0L
                groupMemberRepository.count() shouldBe 0L
            }
        }
    }

    given("탈퇴해 더 이상 존재하지 않는 사용자의 유효한 access token이 있으면") {
        `when`("그룹 생성을 요청할 때") {
            then("404를 반환하고 그룹과 멤버십을 저장하지 않는다") {
                cleanDatabase()
                val registerBody = objectMapper.readTree(
                    register("withdrawn-group-creator").contentAsString,
                )
                val accessToken = registerBody["accessToken"].stringValue()
                withdraw(accessToken).status shouldBe HttpStatus.OK.value()

                assertProblem(
                    response = createGroup(accessToken, "만들 수 없는 그룹"),
                    status = HttpStatus.NOT_FOUND,
                    detail = "사용자를 찾을 수 없습니다.",
                )
                groupRepository.count() shouldBe 0L
                groupMemberRepository.count() shouldBe 0L
            }
        }
    }
})
