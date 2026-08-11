package com.mogumogu.momogo.photo.presentation

import com.mogumogu.momogo.APPLICATION_TIME_ZONE_ID
import com.mogumogu.momogo.global.error.ErrorCode
import com.mogumogu.momogo.photo.application.PhotoObjectMetadata
import com.mogumogu.momogo.photo.application.PhotoObjectMetadataReader
import com.mogumogu.momogo.photo.domain.PhotoContentType
import com.mogumogu.momogo.photo.domain.PhotoObjectKey
import com.mogumogu.momogo.photo.infra.PhotoGroupRepository
import com.mogumogu.momogo.photo.infra.PhotoRepository
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.engine.concurrency.TestExecutionMode
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.data.auditing.DateTimeProvider
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.convention.TestBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PhotoApiTestConfiguration::class)
@ApplyExtension(SpringExtension::class)
class PhotoApiIntegrationTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val objectMetadataReader: StubPhotoObjectMetadataReader,
    private val photoRepository: PhotoRepository,
    private val photoGroupRepository: PhotoGroupRepository,
    private val jdbcTemplate: JdbcTemplate,
    private val clock: Clock,
) : BehaviorSpec({
    testExecutionMode = TestExecutionMode.Sequential

    beforeTest {
        objectMetadataReader.clear()
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

    fun register(providerToken: String): PhotoRegisteredUserFixture {
        val response = performJson(
            post("/api/v1/user/register"),
            json(
                mapOf(
                    "provider" to "GUEST",
                    "providerToken" to providerToken,
                    "nickname" to "모모",
                ),
            ),
        )
        response.status shouldBe HttpStatus.OK.value()
        val body = objectMapper.readTree(response.contentAsString)
        return PhotoRegisteredUserFixture(
            userId = body["userId"].longValue(),
            accessToken = body["accessToken"].stringValue(),
        )
    }

    fun createGroupFixture(
        accessToken: String,
        name: String,
    ): PhotoGroupFixture {
        val response = performJson(
            post("/api/v1/groups")
                .header("Authorization", "Bearer $accessToken"),
            json(mapOf("name" to name)),
        )
        response.status shouldBe HttpStatus.OK.value()
        val body = objectMapper.readTree(response.contentAsString)
        return PhotoGroupFixture(
            groupId = body["groupId"].longValue(),
            inviteCode = body["inviteCode"].stringValue(),
        )
    }

    fun createGroup(
        accessToken: String,
        name: String,
    ): Long = createGroupFixture(accessToken, name).groupId

    fun joinGroup(
        accessToken: String,
        inviteCode: String,
    ) {
        val response = performJson(
            post("/api/v1/groups/invitations")
                .header("Authorization", "Bearer $accessToken"),
            json(mapOf("code" to inviteCode)),
        )
        response.status shouldBe HttpStatus.OK.value()
    }

    fun createPhotoWithContent(
        accessToken: String?,
        content: String,
    ): MockHttpServletResponse {
        val request = post("/api/v1/photos")
        if (accessToken != null) {
            request.header("Authorization", "Bearer $accessToken")
        }
        return performJson(request, content)
    }

    fun createPhoto(
        accessToken: String?,
        objectKey: String,
        groupIds: List<Long>,
    ): MockHttpServletResponse =
        createPhotoWithContent(
            accessToken = accessToken,
            content = json(
                mapOf(
                    "objectKey" to objectKey,
                    "groupIds" to groupIds,
                ),
            ),
        )

    fun unlinkPhoto(
        accessToken: String?,
        groupId: Long,
        photoId: Long,
    ): MockHttpServletResponse {
        val request = delete("/api/v1/groups/{groupId}/photos/{photoId}", groupId, photoId)
        if (accessToken != null) {
            request.header("Authorization", "Bearer $accessToken")
        }
        return mockMvc.perform(request).andReturn().response
    }

    fun withdraw(accessToken: String): MockHttpServletResponse =
        mockMvc.perform(
            delete("/api/v1/user")
                .header("Authorization", "Bearer $accessToken"),
        ).andReturn().response

    fun objectKey(
        userId: Long,
        phase: String = "test",
        contentTypeValue: String = "image/webp",
    ): PhotoObjectKey =
        PhotoObjectKey.generate(
            phase = phase,
            userId = userId,
            uploadDate = LocalDate.now(clock),
            objectId = UUID.randomUUID(),
            contentType = requireNotNull(PhotoContentType.from(contentTypeValue)),
        )

    fun putObject(
        objectKey: PhotoObjectKey,
        sizeBytes: Long = 1_024L,
        contentTypeValue: String? = "image/webp",
    ) {
        objectMetadataReader.put(
            objectKey = objectKey,
            metadata = PhotoObjectMetadata(
                sizeBytes = sizeBytes,
                contentTypeValue = contentTypeValue,
            ),
        )
    }

    fun assertProblem(
        response: MockHttpServletResponse,
        status: HttpStatus,
        errorCode: ErrorCode,
    ) {
        response.status shouldBe status.value()
        response.contentType shouldBe MediaType.APPLICATION_PROBLEM_JSON_VALUE
        val body = objectMapper.readTree(response.contentAsString)
        body["status"].intValue() shouldBe status.value()
        body["detail"].stringValue() shouldBe errorCode.message
        body["code"].stringValue() shouldBe errorCode.name
    }

    given("인증된 사용자가 R2에 올린 사진과 참여 중인 두 그룹을 선택하면") {
        `when`("사진을 등록할 때") {
            then("사진 하나를 만들고 선택한 두 그룹에 연결한다") {
                val user = register("photo-create-multiple-groups")
                val firstGroupId = createGroup(user.accessToken, "첫 번째 그룹")
                val secondGroupId = createGroup(user.accessToken, "두 번째 그룹")
                val objectKey = objectKey(user.userId)
                putObject(objectKey, contentTypeValue = "IMAGE/WEBP")
                val photoCountBefore = photoRepository.count()
                val photoGroupCountBefore = photoGroupRepository.count()
                val issuedAtEarliest = LocalDateTime.now(clock)

                val response = createPhoto(
                    accessToken = user.accessToken,
                    objectKey = objectKey.value,
                    groupIds = listOf(secondGroupId, firstGroupId),
                )

                val issuedAtLatest = LocalDateTime.now(clock)
                response.status shouldBe HttpStatus.OK.value()
                response.contentType shouldBe MediaType.APPLICATION_JSON_VALUE
                val body = objectMapper.readTree(response.contentAsString)
                body.propertyNames().toSet() shouldBe setOf("photoId", "objectKey", "createdAt")
                body["objectKey"].stringValue() shouldBe objectKey.value
                val createdAt = LocalDateTime.parse(body["createdAt"].stringValue())
                createdAt.isBefore(issuedAtEarliest) shouldBe false
                createdAt.isAfter(issuedAtLatest) shouldBe false
                photoRepository.count() shouldBe photoCountBefore + 1
                photoGroupRepository.count() shouldBe photoGroupCountBefore + 2
                val savedPhoto = photoRepository.findById(body["photoId"].longValue()).orElseThrow()
                savedPhoto.objectKey shouldBe objectKey.value
                savedPhoto.sizeBytes shouldBe 1_024L
                savedPhoto.contentType shouldBe "image/webp"
                LocalDateTime.ofInstant(savedPhoto.createdAt, clock.zone) shouldBe createdAt
            }
        }
    }

    given("사용자가 A와 B 그룹에 한 사진을 함께 등록했으면") {
        `when`("같은 날 A 또는 B에 다른 사진을 등록할 때") {
            then("두 그룹 모두 오늘 추가 업로드를 거부한다") {
                val user = register("photo-create-ab-daily-limit")
                val firstGroupId = createGroup(user.accessToken, "A 그룹")
                val secondGroupId = createGroup(user.accessToken, "B 그룹")
                val firstObjectKey = objectKey(user.userId)
                putObject(firstObjectKey)
                createPhoto(
                    user.accessToken,
                    firstObjectKey.value,
                    listOf(firstGroupId, secondGroupId),
                ).status shouldBe HttpStatus.OK.value()
                val photoCountAfterFirstUpload = photoRepository.count()

                listOf(firstGroupId, secondGroupId).forEach { groupId ->
                    val nextObjectKey = objectKey(user.userId)
                    putObject(nextObjectKey)
                    assertProblem(
                        response = createPhoto(
                            user.accessToken,
                            nextObjectKey.value,
                            listOf(groupId),
                        ),
                        status = HttpStatus.CONFLICT,
                        errorCode = ErrorCode.DAILY_GROUP_UPLOAD_LIMIT_EXCEEDED,
                    )
                }
                photoRepository.count() shouldBe photoCountAfterFirstUpload
            }
        }
    }

    given("사용자가 아직 사진을 올리지 않은 A와 B 그룹에 참여 중이면") {
        `when`("A와 B에 각각 다른 사진을 등록할 때") {
            then("같은 날 사진 두 장을 등록할 수 있다") {
                val user = register("photo-create-separate-groups")
                val firstGroupId = createGroup(user.accessToken, "각각 A 그룹")
                val secondGroupId = createGroup(user.accessToken, "각각 B 그룹")
                val photoCountBefore = photoRepository.count()

                listOf(firstGroupId, secondGroupId).forEach { groupId ->
                    val nextObjectKey = objectKey(user.userId)
                    putObject(nextObjectKey)
                    createPhoto(
                        user.accessToken,
                        nextObjectKey.value,
                        listOf(groupId),
                    ).status shouldBe HttpStatus.OK.value()
                }

                photoRepository.count() shouldBe photoCountBefore + 2
            }
        }
    }

    given("사용자가 오늘 A 그룹에 사진을 등록했으면") {
        `when`("새 사진을 A와 B 그룹에 함께 등록할 때") {
            then("전체 요청을 거부하고 B 그룹의 업로드 기회는 유지한다") {
                val user = register("photo-create-atomic-limit")
                val firstGroupId = createGroup(user.accessToken, "원자성 A 그룹")
                val secondGroupId = createGroup(user.accessToken, "원자성 B 그룹")
                val firstObjectKey = objectKey(user.userId)
                putObject(firstObjectKey)
                createPhoto(
                    user.accessToken,
                    firstObjectKey.value,
                    listOf(firstGroupId),
                ).status shouldBe HttpStatus.OK.value()
                val photoCountAfterFirstUpload = photoRepository.count()

                val rejectedObjectKey = objectKey(user.userId)
                putObject(rejectedObjectKey)
                assertProblem(
                    response = createPhoto(
                        user.accessToken,
                        rejectedObjectKey.value,
                        listOf(firstGroupId, secondGroupId),
                    ),
                    status = HttpStatus.CONFLICT,
                    errorCode = ErrorCode.DAILY_GROUP_UPLOAD_LIMIT_EXCEEDED,
                )
                photoRepository.count() shouldBe photoCountAfterFirstUpload

                val secondGroupObjectKey = objectKey(user.userId)
                putObject(secondGroupObjectKey)
                createPhoto(
                    user.accessToken,
                    secondGroupObjectKey.value,
                    listOf(secondGroupId),
                ).status shouldBe HttpStatus.OK.value()
            }
        }
    }

    given("사용자의 이전 날짜 그룹 업로드가 있으면") {
        `when`("오늘 같은 그룹에 새 사진을 등록할 때") {
            then("날짜가 다르므로 등록을 허용한다") {
                val user = register("photo-create-next-day")
                val groupId = createGroup(user.accessToken, "날짜 기준 그룹")
                val firstObjectKey = objectKey(user.userId)
                putObject(firstObjectKey)
                val firstResponse = createPhoto(
                    user.accessToken,
                    firstObjectKey.value,
                    listOf(groupId),
                )
                firstResponse.status shouldBe HttpStatus.OK.value()
                val firstPhotoId = objectMapper.readTree(firstResponse.contentAsString)["photoId"].longValue()
                jdbcTemplate.update(
                    "UPDATE photo_group SET created_at = ? WHERE photo_id = ? AND group_id = ?",
                    LocalDate.now(clock)
                        .minusDays(1)
                        .atTime(12, 0)
                        .atZone(clock.zone)
                        .toInstant(),
                    firstPhotoId,
                    groupId,
                )

                val secondObjectKey = objectKey(user.userId)
                putObject(secondObjectKey)

                createPhoto(
                    user.accessToken,
                    secondObjectKey.value,
                    listOf(groupId),
                ).status shouldBe HttpStatus.OK.value()
            }
        }
    }

    given("R2 사진 오브젝트가 없거나 유효한 이미지가 아니면") {
        `when`("사진을 등록할 때") {
            then("OBJECT_NOT_UPLOADED 오류를 반환한다") {
                val user = register("photo-create-invalid-object")
                val groupId = createGroup(user.accessToken, "오브젝트 검증 그룹")
                val missingObjectKey = objectKey(user.userId)
                val emptyObjectKey = objectKey(user.userId)
                putObject(emptyObjectKey, sizeBytes = 0L)
                val nonImageObjectKey = objectKey(user.userId)
                putObject(nonImageObjectKey, contentTypeValue = "application/pdf")
                val missingContentTypeObjectKey = objectKey(user.userId)
                putObject(missingContentTypeObjectKey, contentTypeValue = null)
                val mismatchedObjectKey = objectKey(user.userId, contentTypeValue = "image/png")
                putObject(mismatchedObjectKey, contentTypeValue = "image/webp")

                listOf(
                    missingObjectKey,
                    emptyObjectKey,
                    nonImageObjectKey,
                    missingContentTypeObjectKey,
                    mismatchedObjectKey,
                ).forEach { invalidObjectKey ->
                    assertProblem(
                        response = createPhoto(
                            user.accessToken,
                            invalidObjectKey.value,
                            listOf(groupId),
                        ),
                        status = HttpStatus.UNPROCESSABLE_CONTENT,
                        errorCode = ErrorCode.OBJECT_NOT_UPLOADED,
                    )
                }
            }
        }
    }

    given("오브젝트 키가 서버 발급 형식이나 현재 사용자 환경과 맞지 않으면") {
        `when`("사진을 등록할 때") {
            then("INVALID_OBJECT_KEY 오류를 반환한다") {
                val user = register("photo-create-invalid-key")
                val groupId = createGroup(user.accessToken, "키 검증 그룹")
                val otherUserObjectKey = objectKey(user.userId + 1)
                val otherPhaseObjectKey = objectKey(user.userId, phase = "dev")

                listOf(
                    "../invalid-object-key",
                    otherUserObjectKey.value,
                    otherPhaseObjectKey.value,
                ).forEach { invalidObjectKey ->
                    assertProblem(
                        response = createPhoto(
                            user.accessToken,
                            invalidObjectKey,
                            listOf(groupId),
                        ),
                        status = HttpStatus.BAD_REQUEST,
                        errorCode = ErrorCode.INVALID_OBJECT_KEY,
                    )
                }
            }
        }
    }

    given("요청한 그룹 중 현재 사용자가 참여하지 않은 그룹이 있으면") {
        `when`("사진을 등록할 때") {
            then("전체 요청을 NOT_GROUP_MEMBER 오류로 거부한다") {
                val user = register("photo-create-not-member")
                val otherUser = register("photo-create-other-group-owner")
                val joinedGroupId = createGroup(user.accessToken, "참여 그룹")
                val otherGroupId = createGroup(otherUser.accessToken, "미참여 그룹")
                val objectKey = objectKey(user.userId)
                putObject(objectKey)
                val photoCountBefore = photoRepository.count()

                assertProblem(
                    response = createPhoto(
                        user.accessToken,
                        objectKey.value,
                        listOf(joinedGroupId, otherGroupId),
                    ),
                    status = HttpStatus.FORBIDDEN,
                    errorCode = ErrorCode.NOT_GROUP_MEMBER,
                )
                photoRepository.count() shouldBe photoCountBefore
            }
        }
    }

    given("같은 objectKey로 사진을 이미 등록했으면") {
        `when`("다른 그룹에 다시 등록할 때") {
            then("PHOTO_ALREADY_REGISTERED 오류를 반환한다") {
                val user = register("photo-create-duplicate-object-key")
                val firstGroupId = createGroup(user.accessToken, "중복 키 A 그룹")
                val secondGroupId = createGroup(user.accessToken, "중복 키 B 그룹")
                val objectKey = objectKey(user.userId)
                putObject(objectKey)
                createPhoto(
                    user.accessToken,
                    objectKey.value,
                    listOf(firstGroupId),
                ).status shouldBe HttpStatus.OK.value()

                assertProblem(
                    response = createPhoto(
                        user.accessToken,
                        objectKey.value,
                        listOf(secondGroupId),
                    ),
                    status = HttpStatus.CONFLICT,
                    errorCode = ErrorCode.PHOTO_ALREADY_REGISTERED,
                )
            }
        }
    }

    given("groupIds나 objectKey 요청 값이 올바르지 않으면") {
        `when`("사진을 등록할 때") {
            then("INVALID_REQUEST 오류를 반환한다") {
                val user = register("photo-create-invalid-request")
                val groupId = createGroup(user.accessToken, "요청 검증 그룹")
                val objectKey = objectKey(user.userId)
                listOf(
                    mapOf("objectKey" to objectKey.value, "groupIds" to emptyList<Long>()),
                    mapOf("objectKey" to objectKey.value, "groupIds" to listOf(groupId, groupId)),
                    mapOf("objectKey" to objectKey.value, "groupIds" to listOf(0L)),
                    mapOf("objectKey" to objectKey.value, "groupIds" to listOf<Long?>(null)),
                    mapOf("objectKey" to objectKey.value, "groupIds" to (1L..21L).toList()),
                    mapOf("objectKey" to " ", "groupIds" to listOf(groupId)),
                ).forEach { request ->
                    assertProblem(
                        response = createPhotoWithContent(
                            accessToken = user.accessToken,
                            content = json(request),
                        ),
                        status = HttpStatus.BAD_REQUEST,
                        errorCode = ErrorCode.INVALID_REQUEST,
                    )
                }
            }
        }
    }

    given("인증 정보가 없거나 액세스 토큰의 사용자가 탈퇴했으면") {
        `when`("사진을 등록할 때") {
            then("각각 401과 USER_NOT_FOUND 오류를 반환한다") {
                val user = register("photo-create-auth-errors")
                val groupId = createGroup(user.accessToken, "인증 오류 그룹")
                val objectKey = objectKey(user.userId)
                putObject(objectKey)

                assertProblem(
                    response = createPhoto(null, objectKey.value, listOf(groupId)),
                    status = HttpStatus.UNAUTHORIZED,
                    errorCode = ErrorCode.INVALID_AUTH_CREDENTIALS,
                )

                withdraw(user.accessToken).status shouldBe HttpStatus.OK.value()
                assertProblem(
                    response = createPhoto(user.accessToken, objectKey.value, listOf(groupId)),
                    status = HttpStatus.NOT_FOUND,
                    errorCode = ErrorCode.USER_NOT_FOUND,
                )
            }
        }
    }

    given("같은 사용자가 같은 그룹에 두 사진을 동시에 등록하면") {
        `when`("두 요청이 함께 처리될 때") {
            then("하나만 등록하고 다른 요청은 일일 제한으로 거부한다") {
                val user = register("photo-create-concurrent-limit")
                val groupId = createGroup(user.accessToken, "동시 업로드 그룹")
                val firstObjectKey = objectKey(user.userId)
                val secondObjectKey = objectKey(user.userId)
                putObject(firstObjectKey)
                putObject(secondObjectKey)
                val photoCountBefore = photoRepository.count()
                val photoGroupCountBefore = photoGroupRepository.count()
                val ready = CountDownLatch(2)
                val start = CountDownLatch(1)
                val executor = Executors.newFixedThreadPool(2)

                val futures = listOf(firstObjectKey, secondObjectKey).map { targetObjectKey ->
                    executor.submit<MockHttpServletResponse> {
                        ready.countDown()
                        start.await(5, TimeUnit.SECONDS)
                        createPhoto(
                            user.accessToken,
                            targetObjectKey.value,
                            listOf(groupId),
                        )
                    }
                }
                ready.await(5, TimeUnit.SECONDS) shouldBe true
                start.countDown()
                val responses = futures.map { future -> future.get(10, TimeUnit.SECONDS) }
                executor.shutdownNow()

                responses.map { response -> response.status }.sorted() shouldBe
                    listOf(HttpStatus.OK.value(), HttpStatus.CONFLICT.value())
                val conflictResponse = responses.single { response ->
                    response.status == HttpStatus.CONFLICT.value()
                }
                assertProblem(
                    response = conflictResponse,
                    status = HttpStatus.CONFLICT,
                    errorCode = ErrorCode.DAILY_GROUP_UPLOAD_LIMIT_EXCEEDED,
                )
                photoRepository.count() shouldBe photoCountBefore + 1
                photoGroupRepository.count() shouldBe photoGroupCountBefore + 1
            }
        }
    }

    given("사용자가 한 사진을 A와 B 그룹에 함께 등록했으면") {
        `when`("A 그룹에서 사진을 내릴 때") {
            then("A 연결만 해제하고 A의 오늘 업로드 기회만 다시 사용할 수 있다") {
                val user = register("photo-unlink-one-group")
                val firstGroupId = createGroup(user.accessToken, "내리기 A 그룹")
                val secondGroupId = createGroup(user.accessToken, "내리기 B 그룹")
                val originalObjectKey = objectKey(user.userId)
                putObject(originalObjectKey)
                val createResponse = createPhoto(
                    accessToken = user.accessToken,
                    objectKey = originalObjectKey.value,
                    groupIds = listOf(firstGroupId, secondGroupId),
                )
                createResponse.status shouldBe HttpStatus.OK.value()
                val photoId = objectMapper.readTree(createResponse.contentAsString)["photoId"].longValue()
                val firstPhotoGroup = requireNotNull(
                    photoGroupRepository.findActiveByPhotoIdAndGroupId(photoId, firstGroupId),
                )
                val firstPhotoGroupId = requireNotNull(firstPhotoGroup.id)
                val photoCountBefore = photoRepository.count()
                val photoGroupCountBefore = photoGroupRepository.count()
                val unlinkedAtEarliest = clock.instant()

                val response = unlinkPhoto(user.accessToken, firstGroupId, photoId)

                val unlinkedAtLatest = clock.instant()
                response.status shouldBe HttpStatus.OK.value()
                response.contentType shouldBe MediaType.APPLICATION_JSON_VALUE
                objectMapper.readTree(response.contentAsString).toString() shouldBe "{}"
                photoRepository.count() shouldBe photoCountBefore
                photoGroupRepository.count() shouldBe photoGroupCountBefore
                photoRepository.findById(photoId).orElseThrow().objectKey shouldBe originalObjectKey.value
                photoGroupRepository.findActiveByPhotoIdAndGroupId(photoId, firstGroupId) shouldBe null
                requireNotNull(
                    photoGroupRepository.findActiveByPhotoIdAndGroupId(photoId, secondGroupId),
                )
                val unlinkedAt = photoGroupRepository.findById(firstPhotoGroupId).orElseThrow().deletedAt
                requireNotNull(unlinkedAt)
                unlinkedAt.isBefore(unlinkedAtEarliest) shouldBe false
                unlinkedAt.isAfter(unlinkedAtLatest) shouldBe false

                val firstGroupObjectKey = objectKey(user.userId)
                putObject(firstGroupObjectKey)
                createPhoto(
                    accessToken = user.accessToken,
                    objectKey = firstGroupObjectKey.value,
                    groupIds = listOf(firstGroupId),
                ).status shouldBe HttpStatus.OK.value()

                val secondGroupObjectKey = objectKey(user.userId)
                putObject(secondGroupObjectKey)
                assertProblem(
                    response = createPhoto(
                        accessToken = user.accessToken,
                        objectKey = secondGroupObjectKey.value,
                        groupIds = listOf(secondGroupId),
                    ),
                    status = HttpStatus.CONFLICT,
                    errorCode = ErrorCode.DAILY_GROUP_UPLOAD_LIMIT_EXCEEDED,
                )
            }
        }
    }

    given("그룹에 다른 사용자가 올린 사진이 있으면") {
        `when`("그룹 멤버와 비멤버가 각각 사진을 내리려고 할 때") {
            then("멤버는 FORBIDDEN, 비멤버는 NOT_GROUP_MEMBER 오류를 받는다") {
                val uploader = register("photo-unlink-owner")
                val member = register("photo-unlink-member")
                val nonMember = register("photo-unlink-non-member")
                val group = createGroupFixture(uploader.accessToken, "소유권 확인 그룹")
                joinGroup(member.accessToken, group.inviteCode)
                val uploadedObjectKey = objectKey(uploader.userId)
                putObject(uploadedObjectKey)
                val createResponse = createPhoto(
                    accessToken = uploader.accessToken,
                    objectKey = uploadedObjectKey.value,
                    groupIds = listOf(group.groupId),
                )
                val photoId = objectMapper.readTree(createResponse.contentAsString)["photoId"].longValue()

                assertProblem(
                    response = unlinkPhoto(member.accessToken, group.groupId, photoId),
                    status = HttpStatus.FORBIDDEN,
                    errorCode = ErrorCode.FORBIDDEN,
                )
                assertProblem(
                    response = unlinkPhoto(nonMember.accessToken, group.groupId, photoId),
                    status = HttpStatus.FORBIDDEN,
                    errorCode = ErrorCode.NOT_GROUP_MEMBER,
                )
                requireNotNull(
                    photoGroupRepository.findActiveByPhotoIdAndGroupId(photoId, group.groupId),
                )
            }
        }
    }

    given("사용자가 두 그룹에 참여하고 한 그룹에만 사진을 올렸으면") {
        `when`("다른 그룹 경로로 내리거나 같은 사진을 두 번 내릴 때") {
            then("PHOTO_NOT_FOUND 오류를 반환하고 성공한 요청만 연결을 해제한다") {
                val user = register("photo-unlink-not-found")
                val linkedGroupId = createGroup(user.accessToken, "사진 연결 그룹")
                val otherGroupId = createGroup(user.accessToken, "다른 그룹")
                val uploadedObjectKey = objectKey(user.userId)
                putObject(uploadedObjectKey)
                val createResponse = createPhoto(
                    accessToken = user.accessToken,
                    objectKey = uploadedObjectKey.value,
                    groupIds = listOf(linkedGroupId),
                )
                val photoId = objectMapper.readTree(createResponse.contentAsString)["photoId"].longValue()

                assertProblem(
                    response = unlinkPhoto(user.accessToken, otherGroupId, photoId),
                    status = HttpStatus.NOT_FOUND,
                    errorCode = ErrorCode.PHOTO_NOT_FOUND,
                )
                requireNotNull(
                    photoGroupRepository.findActiveByPhotoIdAndGroupId(photoId, linkedGroupId),
                )

                unlinkPhoto(user.accessToken, linkedGroupId, photoId).status shouldBe HttpStatus.OK.value()
                assertProblem(
                    response = unlinkPhoto(user.accessToken, linkedGroupId, photoId),
                    status = HttpStatus.NOT_FOUND,
                    errorCode = ErrorCode.PHOTO_NOT_FOUND,
                )
            }
        }
    }

    given("그룹 또는 사진 ID가 양수가 아니거나 인증 정보가 없으면") {
        `when`("그룹 사진을 내릴 때") {
            then("잘못된 ID는 INVALID_REQUEST, 미인증 요청은 401 오류를 반환한다") {
                val user = register("photo-unlink-invalid-request")

                listOf(
                    0L to 1L,
                    -1L to 1L,
                    1L to 0L,
                    1L to -1L,
                ).forEach { (groupId, photoId) ->
                    assertProblem(
                        response = unlinkPhoto(user.accessToken, groupId, photoId),
                        status = HttpStatus.BAD_REQUEST,
                        errorCode = ErrorCode.INVALID_REQUEST,
                    )
                }

                assertProblem(
                    response = unlinkPhoto(null, 1L, 1L),
                    status = HttpStatus.UNAUTHORIZED,
                    errorCode = ErrorCode.INVALID_AUTH_CREDENTIALS,
                )
            }
        }
    }

    given("같은 그룹 사진을 두 요청이 동시에 내리면") {
        `when`("두 요청이 함께 처리될 때") {
            then("한 요청만 연결을 해제하고 다른 요청은 PHOTO_NOT_FOUND를 반환한다") {
                val user = register("photo-unlink-concurrent")
                val groupId = createGroup(user.accessToken, "동시 내리기 그룹")
                val uploadedObjectKey = objectKey(user.userId)
                putObject(uploadedObjectKey)
                val createResponse = createPhoto(
                    accessToken = user.accessToken,
                    objectKey = uploadedObjectKey.value,
                    groupIds = listOf(groupId),
                )
                val photoId = objectMapper.readTree(createResponse.contentAsString)["photoId"].longValue()
                val ready = CountDownLatch(2)
                val start = CountDownLatch(1)
                val executor = Executors.newFixedThreadPool(2)

                val futures = (1..2).map {
                    executor.submit<MockHttpServletResponse> {
                        ready.countDown()
                        start.await(5, TimeUnit.SECONDS)
                        unlinkPhoto(user.accessToken, groupId, photoId)
                    }
                }
                ready.await(5, TimeUnit.SECONDS) shouldBe true
                start.countDown()
                val responses = futures.map { future -> future.get(10, TimeUnit.SECONDS) }
                executor.shutdownNow()

                responses.map { response -> response.status }.sorted() shouldBe
                    listOf(HttpStatus.OK.value(), HttpStatus.NOT_FOUND.value()).sorted()
                assertProblem(
                    response = responses.single { response ->
                        response.status == HttpStatus.NOT_FOUND.value()
                    },
                    status = HttpStatus.NOT_FOUND,
                    errorCode = ErrorCode.PHOTO_NOT_FOUND,
                )
                photoGroupRepository.findActiveByPhotoIdAndGroupId(photoId, groupId) shouldBe null
            }
        }
    }
}) {

    @TestBean(
        name = "auditingDateTimeProvider",
        methodName = "fixedAuditingDateTimeProvider",
        enforceOverride = true,
    )
    lateinit var testAuditingDateTimeProvider: DateTimeProvider

    companion object {
        @JvmStatic
        fun fixedAuditingDateTimeProvider(): DateTimeProvider =
            DateTimeProvider { Optional.of(PHOTO_API_TEST_INSTANT) }
    }
}

@TestConfiguration(proxyBeanMethods = false)
class PhotoApiTestConfiguration {
    @Bean
    @Primary
    fun fixedClock(): Clock =
        Clock.fixed(
            PHOTO_API_TEST_INSTANT,
            ZoneId.of(APPLICATION_TIME_ZONE_ID),
        )

    @Bean
    @Primary
    fun stubPhotoObjectMetadataReader(): StubPhotoObjectMetadataReader =
        StubPhotoObjectMetadataReader()
}

class StubPhotoObjectMetadataReader : PhotoObjectMetadataReader {
    private val metadataByObjectKey = ConcurrentHashMap<String, PhotoObjectMetadata>()

    override fun find(objectKey: PhotoObjectKey): PhotoObjectMetadata? =
        metadataByObjectKey[objectKey.value]

    fun put(
        objectKey: PhotoObjectKey,
        metadata: PhotoObjectMetadata,
    ) {
        metadataByObjectKey[objectKey.value] = metadata
    }

    fun clear() {
        metadataByObjectKey.clear()
    }
}

private data class PhotoRegisteredUserFixture(
    val userId: Long,
    val accessToken: String,
)

private data class PhotoGroupFixture(
    val groupId: Long,
    val inviteCode: String,
)

private val PHOTO_API_TEST_INSTANT = Instant.parse("2026-08-11T03:00:00Z")
