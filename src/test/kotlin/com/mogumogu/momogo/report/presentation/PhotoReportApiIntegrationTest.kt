package com.mogumogu.momogo.report.presentation

import com.mogumogu.momogo.global.error.ErrorCode
import com.mogumogu.momogo.group.infra.GroupRepository
import com.mogumogu.momogo.photo.domain.Photo
import com.mogumogu.momogo.photo.domain.PhotoGroup
import com.mogumogu.momogo.photo.infra.PhotoGroupRepository
import com.mogumogu.momogo.photo.infra.PhotoRepository
import com.mogumogu.momogo.report.application.PhotoReportNotification
import com.mogumogu.momogo.report.application.PhotoReportNotificationException
import com.mogumogu.momogo.report.application.PhotoReportNotifier
import com.mogumogu.momogo.user.infra.UserRepository
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.engine.concurrency.TestExecutionMode
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PhotoReportApiTestConfiguration::class)
@ApplyExtension(SpringExtension::class)
class PhotoReportApiIntegrationTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val userRepository: UserRepository,
    private val groupRepository: GroupRepository,
    private val photoRepository: PhotoRepository,
    private val photoGroupRepository: PhotoGroupRepository,
    private val photoReportNotifier: RecordingPhotoReportNotifier,
) : BehaviorSpec({
    testExecutionMode = TestExecutionMode.Sequential

    beforeTest {
        photoReportNotifier.reset()
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

    fun register(): PhotoReportUserFixture {
        val response = performJson(
            post("/api/v1/user/register"),
            json(
                mapOf(
                    "provider" to "GUEST",
                    "providerToken" to "photo-report-${UUID.randomUUID()}",
                    "nickname" to "모모",
                ),
            ),
        )
        response.status shouldBe HttpStatus.OK.value()
        val body = objectMapper.readTree(response.contentAsString)

        return PhotoReportUserFixture(
            userId = body["userId"].longValue(),
            accessToken = body["accessToken"].stringValue(),
        )
    }

    fun createGroup(
        accessToken: String,
        name: String = "신고 그룹",
    ): Long {
        val response = performJson(
            post("/api/v1/groups").header("Authorization", "Bearer $accessToken"),
            json(mapOf("name" to name)),
        )
        response.status shouldBe HttpStatus.OK.value()

        return objectMapper.readTree(response.contentAsString)["groupId"].longValue()
    }

    fun uploadPhoto(
        uploaderId: Long,
        groupId: Long,
    ): PhotoGroup {
        val uploader = userRepository.findById(uploaderId).orElseThrow()
        val group = groupRepository.findById(groupId).orElseThrow()
        val photo = photoRepository.saveAndFlush(
            Photo(
                _uploader = uploader,
                _objectKey = "reports/${UUID.randomUUID()}.jpg",
                _sizeBytes = 1_024L,
                _contentType = "image/jpeg",
            ),
        )

        return photoGroupRepository.saveAndFlush(
            PhotoGroup(
                _photo = photo,
                _group = group,
            ),
        )
    }

    fun report(
        accessToken: String,
        groupId: Long,
        photoId: Long,
        requestBody: String,
    ): MockHttpServletResponse =
        performJson(
            post("/api/v1/groups/$groupId/photos/$photoId/reports")
                .header("Authorization", "Bearer $accessToken"),
            requestBody,
        )

    fun reportReason(
        accessToken: String,
        groupId: Long,
        photoId: Long,
        reason: String,
    ): MockHttpServletResponse =
        report(
            accessToken = accessToken,
            groupId = groupId,
            photoId = photoId,
            requestBody = json(mapOf("reason" to reason)),
        )

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

    given("현재 그룹 멤버에게 활성 사진이 있으면") {
        `when`("앞뒤 공백이 있는 사유로 사진을 신고할 때") {
            then("사유를 정규화해 한 번 알리고 빈 객체를 반환한다") {
                val reporter = register()
                val groupId = createGroup(reporter.accessToken)
                val photoId = requireNotNull(uploadPhoto(reporter.userId, groupId).photo.id)

                val response = reportReason(
                    accessToken = reporter.accessToken,
                    groupId = groupId,
                    photoId = photoId,
                    reason = "  부적절한 사진입니다.  ",
                )

                response.status shouldBe HttpStatus.OK.value()
                response.contentType shouldBe MediaType.APPLICATION_JSON_VALUE
                response.contentAsString shouldBe "{}"
                photoReportNotifier.notifications shouldBe listOf(
                    PhotoReportNotification(
                        phase = "test",
                        reporterId = reporter.userId,
                        groupId = groupId,
                        photoId = photoId,
                        reason = "부적절한 사진입니다.",
                    ),
                )
            }
        }
    }

    given("그룹 멤버가 아닌 사용자가 있으면") {
        `when`("존재하는 사진과 존재하지 않는 사진을 각각 신고할 때") {
            then("사진 존재 여부와 관계없이 403으로 거절하고 알리지 않는다") {
                val owner = register()
                val outsider = register()
                val groupId = createGroup(owner.accessToken)
                val photoId = requireNotNull(uploadPhoto(owner.userId, groupId).photo.id)

                listOf(photoId, Long.MAX_VALUE).forEach { targetPhotoId ->
                    assertProblem(
                        response = reportReason(
                            accessToken = outsider.accessToken,
                            groupId = groupId,
                            photoId = targetPhotoId,
                            reason = "신고 사유",
                        ),
                        status = HttpStatus.FORBIDDEN,
                        errorCode = ErrorCode.NOT_GROUP_MEMBER,
                    )
                }
                photoReportNotifier.notifications shouldBe emptyList()
            }
        }
    }

    given("현재 멤버가 신고할 수 없는 사진을 지정하면") {
        `when`("다른 그룹에만 속한 사진을 신고할 때") {
            then("404로 거절하고 알리지 않는다") {
                val reporter = register()
                val photoGroupId = createGroup(reporter.accessToken, "사진 원본 그룹")
                val reportGroupId = createGroup(reporter.accessToken, "신고 대상 그룹")
                val photoId = requireNotNull(uploadPhoto(reporter.userId, photoGroupId).photo.id)

                assertProblem(
                    response = reportReason(
                        accessToken = reporter.accessToken,
                        groupId = reportGroupId,
                        photoId = photoId,
                        reason = "신고 사유",
                    ),
                    status = HttpStatus.NOT_FOUND,
                    errorCode = ErrorCode.PHOTO_NOT_FOUND,
                )
                photoReportNotifier.notifications shouldBe emptyList()
            }
        }

        `when`("그룹에서 내린 사진을 신고할 때") {
            then("404로 거절하고 알리지 않는다") {
                val reporter = register()
                val groupId = createGroup(reporter.accessToken)
                val photoGroup = uploadPhoto(reporter.userId, groupId)
                val photoId = requireNotNull(photoGroup.photo.id)
                photoGroup.unlink(Instant.parse("2030-01-01T00:00:00Z"))
                photoGroupRepository.saveAndFlush(photoGroup)

                assertProblem(
                    response = reportReason(
                        accessToken = reporter.accessToken,
                        groupId = groupId,
                        photoId = photoId,
                        reason = "신고 사유",
                    ),
                    status = HttpStatus.NOT_FOUND,
                    errorCode = ErrorCode.PHOTO_NOT_FOUND,
                )
                photoReportNotifier.notifications shouldBe emptyList()
            }
        }

        `when`("존재하지 않는 사진을 신고할 때") {
            then("404로 거절하고 알리지 않는다") {
                val reporter = register()
                val groupId = createGroup(reporter.accessToken)

                assertProblem(
                    response = reportReason(
                        accessToken = reporter.accessToken,
                        groupId = groupId,
                        photoId = Long.MAX_VALUE,
                        reason = "신고 사유",
                    ),
                    status = HttpStatus.NOT_FOUND,
                    errorCode = ErrorCode.PHOTO_NOT_FOUND,
                )
                photoReportNotifier.notifications shouldBe emptyList()
            }
        }
    }

    given("groupId 또는 photoId가 양수가 아니면") {
        `when`("신고를 요청할 때") {
            then("400으로 거절하고 알리지 않는다") {
                val reporter = register()

                listOf(0L, -1L).forEach { invalidGroupId ->
                    assertProblem(
                        response = reportReason(
                            accessToken = reporter.accessToken,
                            groupId = invalidGroupId,
                            photoId = 1L,
                            reason = "신고 사유",
                        ),
                        status = HttpStatus.BAD_REQUEST,
                        errorCode = ErrorCode.INVALID_REQUEST,
                    )
                }
                listOf(0L, -1L).forEach { invalidPhotoId ->
                    assertProblem(
                        response = reportReason(
                            accessToken = reporter.accessToken,
                            groupId = 1L,
                            photoId = invalidPhotoId,
                            reason = "신고 사유",
                        ),
                        status = HttpStatus.BAD_REQUEST,
                        errorCode = ErrorCode.INVALID_REQUEST,
                    )
                }
                photoReportNotifier.notifications shouldBe emptyList()
            }
        }
    }

    given("신고 사유가 올바르지 않으면") {
        `when`("누락, null, 공백 또는 501자로 신고할 때") {
            then("각 요청을 400으로 거절하고 알리지 않는다") {
                val reporter = register()
                val groupId = createGroup(reporter.accessToken)
                val photoId = requireNotNull(uploadPhoto(reporter.userId, groupId).photo.id)
                val invalidRequestBodies = listOf(
                    json(emptyMap<String, Any>()),
                    json(mapOf<String, String?>("reason" to null)),
                    json(mapOf("reason" to "   ")),
                    json(mapOf("reason" to "가".repeat(501))),
                )

                invalidRequestBodies.forEach { requestBody ->
                    assertProblem(
                        response = report(
                            accessToken = reporter.accessToken,
                            groupId = groupId,
                            photoId = photoId,
                            requestBody = requestBody,
                        ),
                        status = HttpStatus.BAD_REQUEST,
                        errorCode = ErrorCode.INVALID_REQUEST,
                    )
                }
                photoReportNotifier.notifications shouldBe emptyList()
            }
        }
    }

    given("신고 알림기가 전송 실패를 알리면") {
        `when`("유효한 신고를 요청할 때") {
            then("500 PHOTO_REPORT_NOTIFICATION_FAILED를 반환한다") {
                val reporter = register()
                val groupId = createGroup(reporter.accessToken)
                val photoId = requireNotNull(uploadPhoto(reporter.userId, groupId).photo.id)
                photoReportNotifier.failWithNotificationException = true

                assertProblem(
                    response = reportReason(
                        accessToken = reporter.accessToken,
                        groupId = groupId,
                        photoId = photoId,
                        reason = "전송 실패 확인",
                    ),
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    errorCode = ErrorCode.PHOTO_REPORT_NOTIFICATION_FAILED,
                )
                photoReportNotifier.notifications shouldBe listOf(
                    PhotoReportNotification(
                        phase = "test",
                        reporterId = reporter.userId,
                        groupId = groupId,
                        photoId = photoId,
                        reason = "전송 실패 확인",
                    ),
                )
            }
        }
    }
})

@TestConfiguration(proxyBeanMethods = false)
class PhotoReportApiTestConfiguration {
    @Bean
    @Primary
    fun recordingPhotoReportNotifier(): RecordingPhotoReportNotifier =
        RecordingPhotoReportNotifier()
}

class RecordingPhotoReportNotifier : PhotoReportNotifier {
    private val recordedNotifications = mutableListOf<PhotoReportNotification>()

    var failWithNotificationException: Boolean = false

    val notifications: List<PhotoReportNotification>
        get() = recordedNotifications.toList()

    override fun notify(notification: PhotoReportNotification) {
        recordedNotifications += notification
        if (failWithNotificationException) {
            throw PhotoReportNotificationException()
        }
    }

    fun reset() {
        recordedNotifications.clear()
        failWithNotificationException = false
    }
}

private data class PhotoReportUserFixture(
    val userId: Long,
    val accessToken: String,
)
