package com.mogumogu.momogo.group.presentation

import com.mogumogu.momogo.group.domain.Group
import com.mogumogu.momogo.group.domain.GroupMember
import com.mogumogu.momogo.group.domain.InviteCode
import com.mogumogu.momogo.group.infra.GroupMemberRepository
import com.mogumogu.momogo.group.infra.GroupRepository
import com.mogumogu.momogo.photo.infra.PhotoGroupRepository
import com.mogumogu.momogo.photo.infra.PhotoRepository
import com.mogumogu.momogo.user.domain.User
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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

    fun updateGroupNameWithContent(
        accessToken: String?,
        groupId: Long,
        content: String,
    ): MockHttpServletResponse {
        val request = patch("/api/v1/groups/{groupId}", groupId)
        if (accessToken != null) {
            request.header("Authorization", "Bearer $accessToken")
        }
        return performJson(request, content)
    }

    fun updateGroupName(
        accessToken: String?,
        groupId: Long,
        name: String,
    ): MockHttpServletResponse =
        updateGroupNameWithContent(
            accessToken = accessToken,
            groupId = groupId,
            content = json(mapOf("name" to name)),
        )

    fun withdraw(accessToken: String): MockHttpServletResponse =
        mockMvc.perform(
            delete("/api/v1/user")
                .header("Authorization", "Bearer $accessToken"),
        ).andReturn().response

    fun invitationInfo(
        accessToken: String?,
        code: String,
    ): MockHttpServletResponse {
        val request = get("/api/v1/groups/invitations")
            .queryParam("code", code)
        if (accessToken != null) {
            request.header("Authorization", "Bearer $accessToken")
        }
        return mockMvc.perform(request).andReturn().response
    }

    fun joinGroup(
        accessToken: String?,
        code: String,
    ): MockHttpServletResponse {
        val request = post("/api/v1/groups/invitations")
        if (accessToken != null) {
            request.header("Authorization", "Bearer $accessToken")
        }
        return performJson(request, json(mapOf("code" to code)))
    }

    fun registerUser(providerToken: String): RegisteredUserFixture {
        val response = register(providerToken)
        response.status shouldBe HttpStatus.OK.value()
        val body = objectMapper.readTree(response.contentAsString)
        val userId = body["userId"].longValue()
        return RegisteredUserFixture(
            user = userRepository.findById(userId).orElseThrow(),
            accessToken = body["accessToken"].stringValue(),
        )
    }

    fun saveGroup(
        name: String,
        code: String,
    ): Group =
        groupRepository.saveAndFlush(
            Group(
                _name = name,
                _inviteCode = InviteCode(_value = code),
            ),
        )

    fun saveMember(
        group: Group,
        user: User,
        deletedAt: Instant? = null,
    ): GroupMember =
        groupMemberRepository.saveAndFlush(
            GroupMember(
                _group = group,
                _user = user,
            ).apply {
                deletedAt?.let(::leave)
            },
        )

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
            then("초대 코드가 있는 그룹과 생성자의 멤버십을 함께 저장한다") {
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
                requireNotNull(membership).isJoined() shouldBe true
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

    given("현재 가입 중인 그룹 멤버가 있으면") {
        `when`("그룹명을 변경할 때") {
            then("변경된 그룹 정보를 반환하고 데이터베이스에 반영한다") {
                cleanDatabase()
                val registeredUser = registerUser("update-group-name-member")
                val group = saveGroup(
                    name = "기존 그룹",
                    code = "UPDATE",
                )
                saveMember(group, registeredUser.user)

                val response = updateGroupName(
                    accessToken = registeredUser.accessToken,
                    groupId = requireNotNull(group.id),
                    name = "우리 가족 하우스",
                )

                response.status shouldBe HttpStatus.OK.value()
                response.contentType shouldBe MediaType.APPLICATION_JSON_VALUE
                val body = objectMapper.readTree(response.contentAsString)
                body.propertyNames().toSet() shouldBe setOf("groupId", "groupName")
                body["groupId"].longValue() shouldBe group.id
                body["groupName"].stringValue() shouldBe "우리 가족 하우스"
                groupRepository.findById(requireNotNull(group.id)).orElseThrow().name shouldBe
                    "우리 가족 하우스"
            }
        }
    }

    given("현재 그룹에 가입하지 않은 사용자와 탈퇴한 멤버가 있으면") {
        `when`("그룹명 변경을 요청할 때") {
            then("403을 반환하고 그룹명을 변경하지 않는다") {
                cleanDatabase()
                val nonMember = registerUser("update-group-name-non-member")
                val leftMember = registerUser("update-group-name-left-member")
                val group = saveGroup(
                    name = "변경 전 그룹",
                    code = "FORBID",
                )
                saveMember(
                    group = group,
                    user = leftMember.user,
                    deletedAt = Instant.parse("2030-01-01T00:00:00Z"),
                )

                listOf(
                    updateGroupName(
                        accessToken = nonMember.accessToken,
                        groupId = requireNotNull(group.id),
                        name = "비멤버가 변경한 이름",
                    ),
                    updateGroupName(
                        accessToken = leftMember.accessToken,
                        groupId = requireNotNull(group.id),
                        name = "탈퇴 멤버가 변경한 이름",
                    ),
                ).forEach { response ->
                    assertProblem(
                        response = response,
                        status = HttpStatus.FORBIDDEN,
                        detail = "그룹 멤버가 아닙니다.",
                    )
                }
                groupRepository.findById(requireNotNull(group.id)).orElseThrow().name shouldBe
                    "변경 전 그룹"
            }
        }
    }

    given("존재하지 않는 그룹 ID가 있으면") {
        `when`("그룹명 변경을 요청할 때") {
            then("404를 반환한다") {
                cleanDatabase()
                val registeredUser = registerUser("update-missing-group")

                assertProblem(
                    response = updateGroupName(
                        accessToken = registeredUser.accessToken,
                        groupId = Long.MAX_VALUE,
                        name = "찾을 수 없는 그룹",
                    ),
                    status = HttpStatus.NOT_FOUND,
                    detail = "그룹을 찾을 수 없습니다.",
                )
            }
        }
    }

    given("유효하지 않은 변경 그룹명이 있으면") {
        `when`("공백, 누락 또는 255자를 초과한 이름으로 그룹명 변경을 요청할 때") {
            then("400을 반환하고 기존 그룹명을 유지한다") {
                cleanDatabase()
                val registeredUser = registerUser("invalid-update-group-name")
                val group = saveGroup(
                    name = "유지할 그룹명",
                    code = "INVALD",
                )
                saveMember(group, registeredUser.user)
                val groupId = requireNotNull(group.id)

                listOf(
                    updateGroupName(registeredUser.accessToken, groupId, "   "),
                    updateGroupName(registeredUser.accessToken, groupId, "가".repeat(256)),
                    updateGroupNameWithContent(registeredUser.accessToken, groupId, "{}"),
                ).forEach { response ->
                    assertProblem(
                        response = response,
                        status = HttpStatus.BAD_REQUEST,
                        detail = "요청 값이 올바르지 않습니다.",
                    )
                }
                groupRepository.findById(groupId).orElseThrow().name shouldBe "유지할 그룹명"
            }
        }
    }

    given("그룹명 변경 요청에 인증 정보가 없으면") {
        `when`("그룹명 변경을 요청할 때") {
            then("401을 반환한다") {
                cleanDatabase()
                val group = saveGroup(
                    name = "인증 필요 그룹",
                    code = "AUTH01",
                )

                assertProblem(
                    response = updateGroupName(
                        accessToken = null,
                        groupId = requireNotNull(group.id),
                        name = "변경할 수 없는 그룹",
                    ),
                    status = HttpStatus.UNAUTHORIZED,
                    detail = "인증 정보가 올바르지 않습니다.",
                )
                groupRepository.findById(requireNotNull(group.id)).orElseThrow().name shouldBe
                    "인증 필요 그룹"
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
        `when`("그룹 생성과 참여를 요청할 때") {
            then("404를 반환하고 그룹과 멤버십을 추가로 저장하지 않는다") {
                cleanDatabase()
                val groupOwner = registerUser("stale-token-group-owner")
                val group = saveGroup(
                    name = "기존 그룹",
                    code = "STALE1",
                )
                saveMember(group, groupOwner.user)
                val withdrawnUser = registerUser("withdrawn-group-member")
                withdraw(withdrawnUser.accessToken).status shouldBe HttpStatus.OK.value()

                listOf(
                    createGroup(withdrawnUser.accessToken, "만들 수 없는 그룹"),
                    joinGroup(withdrawnUser.accessToken, "STALE1"),
                ).forEach { response ->
                    assertProblem(
                        response = response,
                        status = HttpStatus.NOT_FOUND,
                        detail = "사용자를 찾을 수 없습니다.",
                    )
                }
                groupRepository.count() shouldBe 1L
                groupMemberRepository.count() shouldBe 1L
            }
        }
    }

    given("같은 사용자의 그룹 참여와 회원 탈퇴 요청이 동시에 도착하면") {
        `when`("두 요청을 함께 처리할 때") {
            then("회원 탈퇴를 완료하고 멤버십을 남기지 않는다") {
                cleanDatabase()
                val groupOwner = registerUser("concurrent-withdraw-owner")
                val targetUser = registerUser("concurrent-withdraw-target")
                val targetUserId = targetUser.user.id!!
                val group = saveGroup(
                    name = "동시 탈퇴 그룹",
                    code = "RACE02",
                )
                saveMember(group, groupOwner.user)
                val start = CountDownLatch(1)
                val executor = Executors.newFixedThreadPool(2)

                try {
                    val joinFuture = executor.submit<MockHttpServletResponse> {
                        start.await()
                        joinGroup(targetUser.accessToken, "RACE02")
                    }
                    val withdrawFuture = executor.submit<MockHttpServletResponse> {
                        start.await()
                        withdraw(targetUser.accessToken)
                    }
                    start.countDown()

                    val joinResponse = joinFuture.get(5, TimeUnit.SECONDS)
                    val withdrawResponse = withdrawFuture.get(5, TimeUnit.SECONDS)

                    withdrawResponse.status shouldBe HttpStatus.OK.value()
                    (joinResponse.status in setOf(
                        HttpStatus.OK.value(),
                        HttpStatus.NOT_FOUND.value(),
                    )) shouldBe true
                    if (joinResponse.status == HttpStatus.NOT_FOUND.value()) {
                        assertProblem(
                            response = joinResponse,
                            status = HttpStatus.NOT_FOUND,
                            detail = "사용자를 찾을 수 없습니다.",
                        )
                    }
                    userRepository.existsById(targetUserId) shouldBe false
                    groupMemberRepository.findByGroupIdAndUserId(
                        groupId = group.id!!,
                        userId = targetUserId,
                    ) shouldBe null
                } finally {
                    executor.shutdownNow()
                }
            }
        }
    }

    given("초대 코드가 가리키는 그룹에 가입 중인 멤버와 탈퇴한 멤버가 있으면") {
        `when`("각 멤버가 초대 코드로 그룹 정보를 조회할 때") {
            then("현재 가입 인원과 사용자의 참여 여부를 반환한다") {
                cleanDatabase()
                val joinedMember = registerUser("invitation-info-joined")
                val leftMember = registerUser("invitation-info-left")
                val anotherJoinedUser = userRepository.saveAndFlush(
                    User(_nickname = "다른 가입 멤버"),
                )
                val group = saveGroup(
                    name = "우리 가족",
                    code = "INFO01",
                )
                saveMember(group, joinedMember.user)
                saveMember(group, anotherJoinedUser)
                saveMember(
                    group = group,
                    user = leftMember.user,
                    deletedAt = Instant.parse("2030-01-01T00:00:00Z"),
                )

                val joinedMemberResponse = invitationInfo(
                    accessToken = joinedMember.accessToken,
                    code = "INFO01",
                )
                val leftMemberResponse = invitationInfo(
                    accessToken = leftMember.accessToken,
                    code = "INFO01",
                )

                joinedMemberResponse.status shouldBe HttpStatus.OK.value()
                joinedMemberResponse.contentType shouldBe MediaType.APPLICATION_JSON_VALUE
                val joinedMemberBody = objectMapper.readTree(joinedMemberResponse.contentAsString)
                joinedMemberBody.propertyNames().toSet() shouldBe
                    setOf("groupId", "groupName", "totalMemberCount", "participated")
                joinedMemberBody["groupId"].longValue() shouldBe requireNotNull(group.id)
                joinedMemberBody["groupName"].stringValue() shouldBe "우리 가족"
                joinedMemberBody["totalMemberCount"].longValue() shouldBe 2L
                joinedMemberBody["participated"].booleanValue() shouldBe true

                leftMemberResponse.status shouldBe HttpStatus.OK.value()
                leftMemberResponse.contentType shouldBe MediaType.APPLICATION_JSON_VALUE
                val leftMemberBody = objectMapper.readTree(leftMemberResponse.contentAsString)
                leftMemberBody["totalMemberCount"].longValue() shouldBe 2L
                leftMemberBody["participated"].booleanValue() shouldBe false
            }
        }
    }

    given("형식이 잘못됐거나 존재하지 않는 초대 코드가 있으면") {
        `when`("그룹 정보 조회와 참여를 요청할 때") {
            then("404를 반환한다") {
                cleanDatabase()
                val registeredUser = registerUser("invalid-invitation-info")

                listOf("abc", "NONE00").forEach { code ->
                    listOf(
                        invitationInfo(
                            accessToken = registeredUser.accessToken,
                            code = code,
                        ),
                        joinGroup(
                            accessToken = registeredUser.accessToken,
                            code = code,
                        ),
                    ).forEach { response ->
                        assertProblem(
                            response = response,
                            status = HttpStatus.NOT_FOUND,
                            detail = "유효하지 않은 초대 코드입니다.",
                        )
                    }
                }
                groupMemberRepository.count() shouldBe 0L
            }
        }
    }

    given("아직 그룹에 참여하지 않은 사용자가 있으면") {
        `when`("초대 코드로 그룹 참여를 요청할 때") {
            then("멤버십을 저장하고 그룹 ID와 초대 코드를 반환한다") {
                cleanDatabase()
                val registeredUser = registerUser("new-invitation-member")
                val group = saveGroup(
                    name = "새로운 그룹",
                    code = "JOIN01",
                )

                val response = joinGroup(
                    accessToken = registeredUser.accessToken,
                    code = "JOIN01",
                )

                response.status shouldBe HttpStatus.OK.value()
                response.contentType shouldBe MediaType.APPLICATION_JSON_VALUE
                val body = objectMapper.readTree(response.contentAsString)
                body.propertyNames().toSet() shouldBe setOf("groupId", "code")
                body["groupId"].longValue() shouldBe requireNotNull(group.id)
                body["code"].stringValue() shouldBe "JOIN01"

                val membership = groupMemberRepository.findByGroupIdAndUserId(
                    groupId = requireNotNull(group.id),
                    userId = requireNotNull(registeredUser.user.id),
                )
                membership shouldNotBe null
                requireNotNull(membership).isJoined() shouldBe true
            }
        }
    }

    given("이미 그룹에 가입한 사용자가 있으면") {
        `when`("같은 초대 코드로 다시 참여를 요청할 때") {
            then("409를 반환하고 멤버십을 중복 저장하지 않는다") {
                cleanDatabase()
                val registeredUser = registerUser("already-joined-member")
                val group = saveGroup(
                    name = "참여 중인 그룹",
                    code = "JOIN02",
                )
                val savedMember = saveMember(group, registeredUser.user)

                assertProblem(
                    response = joinGroup(
                        accessToken = registeredUser.accessToken,
                        code = "JOIN02",
                    ),
                    status = HttpStatus.CONFLICT,
                    detail = "이미 그룹에 가입되어 있습니다.",
                )

                groupMemberRepository.count() shouldBe 1L
                groupMemberRepository.findByGroupIdAndUserId(
                    groupId = requireNotNull(group.id),
                    userId = requireNotNull(registeredUser.user.id),
                )?.id shouldBe savedMember.id
            }
        }
    }

    given("그룹에서 탈퇴한 사용자의 멤버십이 남아 있으면") {
        `when`("같은 초대 코드로 다시 참여를 요청할 때") {
            then("기존 멤버십을 제거하고 새 멤버십을 만든다") {
                cleanDatabase()
                val registeredUser = registerUser("new-membership-after-leave")
                val group = saveGroup(
                    name = "신규 가입 그룹",
                    code = "JOIN03",
                )
                val leftMember = saveMember(
                    group = group,
                    user = registeredUser.user,
                    deletedAt = Instant.parse("2030-01-01T00:00:00Z"),
                )

                val response = joinGroup(
                    accessToken = registeredUser.accessToken,
                    code = "JOIN03",
                )

                response.status shouldBe HttpStatus.OK.value()
                val body = objectMapper.readTree(response.contentAsString)
                body["groupId"].longValue() shouldBe requireNotNull(group.id)
                body["code"].stringValue() shouldBe "JOIN03"
                groupMemberRepository.count() shouldBe 1L
                val newMember = groupMemberRepository.findByGroupIdAndUserId(
                    groupId = requireNotNull(group.id),
                    userId = requireNotNull(registeredUser.user.id),
                )
                newMember shouldNotBe null
                requireNotNull(newMember).id shouldNotBe leftMember.id
                newMember.isJoined() shouldBe true
                groupMemberRepository.existsById(requireNotNull(leftMember.id)) shouldBe false
            }
        }
    }

    given("가입 인원이 8명인 그룹이 있으면") {
        `when`("새 사용자가 초대 코드로 참여를 요청할 때") {
            then("409를 반환하고 새 멤버십을 저장하지 않는다") {
                cleanDatabase()
                val registeredUser = registerUser("full-group-member")
                val group = saveGroup(
                    name = "정원이 찬 그룹",
                    code = "FULL08",
                )
                repeat(8) { index ->
                    val member = userRepository.saveAndFlush(
                        User(_nickname = "멤버$index"),
                    )
                    saveMember(group, member)
                }

                assertProblem(
                    response = joinGroup(
                        accessToken = registeredUser.accessToken,
                        code = "FULL08",
                    ),
                    status = HttpStatus.CONFLICT,
                    detail = "그룹의 최대 인원을 초과했습니다.",
                )

                groupMemberRepository.countJoinedByGroupId(requireNotNull(group.id)) shouldBe 8L
                groupMemberRepository.findByGroupIdAndUserId(
                    groupId = requireNotNull(group.id),
                    userId = requireNotNull(registeredUser.user.id),
                ) shouldBe null
            }
        }
    }

    given("같은 사용자의 그룹 참여 요청이 동시에 도착하면") {
        `when`("같은 초대 코드로 두 요청을 함께 처리할 때") {
            then("한 요청만 가입시키고 다른 요청에는 409를 반환한다") {
                cleanDatabase()
                val registeredUser = registerUser("concurrent-joining-member")
                val group = saveGroup(
                    name = "동시 참여 그룹",
                    code = "RACE01",
                )
                val start = CountDownLatch(1)
                val executor = Executors.newFixedThreadPool(2)

                try {
                    val responses = List(2) {
                        executor.submit<MockHttpServletResponse> {
                            start.await()
                            joinGroup(
                                accessToken = registeredUser.accessToken,
                                code = "RACE01",
                            )
                        }
                    }.also {
                        start.countDown()
                    }.map { future ->
                        future.get(5, TimeUnit.SECONDS)
                    }

                    responses.map(MockHttpServletResponse::getStatus).sorted() shouldBe
                        listOf(HttpStatus.OK.value(), HttpStatus.CONFLICT.value())
                    responses.first { it.status == HttpStatus.CONFLICT.value() }.let { response ->
                        assertProblem(
                            response = response,
                            status = HttpStatus.CONFLICT,
                            detail = "이미 그룹에 가입되어 있습니다.",
                        )
                    }
                    groupMemberRepository.count() shouldBe 1L
                    groupMemberRepository.countJoinedByGroupId(requireNotNull(group.id)) shouldBe 1L
                } finally {
                    executor.shutdownNow()
                }
            }
        }
    }

    given("인증 정보 없이 초대 코드를 사용하면") {
        `when`("그룹 정보 조회와 참여를 요청할 때") {
            then("모두 401을 반환한다") {
                cleanDatabase()

                listOf(
                    invitationInfo(accessToken = null, code = "AUTH01"),
                    joinGroup(accessToken = null, code = "AUTH01"),
                ).forEach { response ->
                    assertProblem(
                        response = response,
                        status = HttpStatus.UNAUTHORIZED,
                        detail = "인증 정보가 올바르지 않습니다.",
                    )
                }
                groupMemberRepository.count() shouldBe 0L
            }
        }
    }
})

private data class RegisteredUserFixture(
    val user: User,
    val accessToken: String,
)
