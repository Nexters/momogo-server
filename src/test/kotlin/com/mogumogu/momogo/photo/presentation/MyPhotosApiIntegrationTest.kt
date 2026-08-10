package com.mogumogu.momogo.photo.presentation

import com.mogumogu.momogo.global.error.ErrorCode
import com.mogumogu.momogo.photo.domain.Photo
import com.mogumogu.momogo.photo.domain.PhotoContentType
import com.mogumogu.momogo.photo.domain.PhotoObjectKey
import com.mogumogu.momogo.photo.infra.PhotoRepository
import com.mogumogu.momogo.user.domain.User
import com.mogumogu.momogo.user.infra.UserRepository
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.engine.concurrency.TestExecutionMode
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ApplyExtension(SpringExtension::class)
class MyPhotosApiIntegrationTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val userRepository: UserRepository,
    private val photoRepository: PhotoRepository,
    private val jdbcTemplate: JdbcTemplate,
    private val clock: Clock,
) : BehaviorSpec({
    testExecutionMode = TestExecutionMode.Sequential

    fun register(providerToken: String): MyPhotosUserFixture {
        val response = mockMvc.perform(
            post("/api/v1/user/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "provider" to "GUEST",
                            "providerToken" to providerToken,
                            "nickname" to "모모",
                        ),
                    ),
                ),
        ).andReturn().response
        response.status shouldBe HttpStatus.OK.value()
        val body = objectMapper.readTree(response.contentAsString)
        val userId = body["userId"].longValue()

        return MyPhotosUserFixture(
            user = userRepository.findById(userId).orElseThrow(),
            accessToken = body["accessToken"].stringValue(),
        )
    }

    fun savePhoto(
        uploader: User,
        createdAt: LocalDateTime,
    ): Photo {
        val objectKey = PhotoObjectKey.generate(
            phase = "test",
            userId = requireNotNull(uploader.id),
            uploadDate = createdAt.toLocalDate(),
            objectId = UUID.randomUUID(),
            contentType = requireNotNull(PhotoContentType.from("image/webp")),
        )
        val photo = photoRepository.saveAndFlush(
            Photo(
                _uploader = uploader,
                _objectKey = objectKey.value,
                _sizeBytes = 1_024L,
                _contentType = "image/webp",
            ),
        )
        jdbcTemplate.update(
            "UPDATE photo SET created_at = ? WHERE id = ?",
            createdAt.atZone(clock.zone).toInstant(),
            requireNotNull(photo.id),
        )
        return photo
    }

    fun getMyPhotos(
        accessToken: String?,
        dateValue: String?,
    ): MockHttpServletResponse {
        val request = get("/api/v1/photos/me")
        if (accessToken != null) {
            request.header("Authorization", "Bearer $accessToken")
        }
        if (dateValue != null) {
            request.queryParam("date", dateValue)
        }
        return mockMvc.perform(request).andReturn().response
    }

    fun assertProblem(
        response: MockHttpServletResponse,
        status: HttpStatus,
        errorCode: ErrorCode,
    ) {
        response.status shouldBe status.value()
        response.contentType shouldBe MediaType.APPLICATION_PROBLEM_JSON_VALUE
        val body = objectMapper.readTree(response.contentAsString)
        body["code"].stringValue() shouldBe errorCode.name
    }

    given("지정한 날짜에 내가 사진을 여러 장 올렸으면") {
        `when`("내 사진을 조회할 때") {
            then("다른 사용자와 다른 날짜의 사진을 제외하고 내 사진을 최신순으로 반환한다") {
                val viewer = register("my-photos-viewer")
                val otherUser = register("my-photos-other-user")
                val date = LocalDate.now(clock)
                savePhoto(viewer.user, date.minusDays(1).atTime(23, 59))
                val earlierPhoto = savePhoto(viewer.user, date.atTime(12, 0))
                val latestPhoto = savePhoto(viewer.user, date.atTime(13, 0))
                savePhoto(otherUser.user, date.atTime(14, 0))
                val issuedAtEarliest = LocalDateTime.now(clock)

                val response = getMyPhotos(viewer.accessToken, date.toString())

                val issuedAtLatest = LocalDateTime.now(clock)
                response.status shouldBe HttpStatus.OK.value()
                response.contentType shouldBe MediaType.APPLICATION_JSON_VALUE
                val body = objectMapper.readTree(response.contentAsString)
                body.propertyNames().toSet() shouldBe setOf("date", "photos")
                body["date"].stringValue() shouldBe date.toString()
                val photos = body["photos"]
                photos.size() shouldBe 2
                val photoNodes = (0 until photos.size()).map { index -> photos[index] }
                photoNodes.map { photo -> photo["photoId"].longValue() } shouldBe
                    listOf(requireNotNull(latestPhoto.id), requireNotNull(earlierPhoto.id))
                photoNodes.forEach { photo ->
                    photo.propertyNames().toSet() shouldBe
                        setOf("photoId", "downloadUrl", "contentType", "createdAt", "expiresAt")
                    photo["contentType"].stringValue() shouldBe "image/webp"
                    URI(photo["downloadUrl"].stringValue()).path.startsWith("/momogo-test/") shouldBe true
                    val expiresAt = LocalDateTime.parse(photo["expiresAt"].stringValue())
                    expiresAt.isBefore(issuedAtEarliest.plusMinutes(15).minusSeconds(1)) shouldBe false
                    expiresAt.isAfter(issuedAtLatest.plusMinutes(15).plusSeconds(1)) shouldBe false
                }
            }
        }
    }

    given("지정한 날짜에 내가 올린 사진이 없으면") {
        `when`("내 사진을 조회할 때") {
            then("빈 사진 목록을 반환한다") {
                val viewer = register("my-photos-empty")
                val date = LocalDate.now(clock)
                savePhoto(viewer.user, date.minusDays(1).atTime(23, 59))

                val response = getMyPhotos(viewer.accessToken, date.toString())

                response.status shouldBe HttpStatus.OK.value()
                response.contentType shouldBe MediaType.APPLICATION_JSON_VALUE
                val body = objectMapper.readTree(response.contentAsString)
                body["date"].stringValue() shouldBe date.toString()
                body["photos"].isArray shouldBe true
                body["photos"].isEmpty shouldBe true
            }
        }
    }

    given("조회 날짜를 생략하면") {
        `when`("내 사진을 조회할 때") {
            then("오늘 날짜의 사진을 반환한다") {
                val viewer = register("my-photos-default-today")
                val today = LocalDate.now(clock)
                val todayPhoto = savePhoto(viewer.user, today.atTime(12, 0))
                savePhoto(viewer.user, today.minusDays(1).atTime(12, 0))

                val response = getMyPhotos(viewer.accessToken, dateValue = null)

                response.status shouldBe HttpStatus.OK.value()
                val body = objectMapper.readTree(response.contentAsString)
                body["date"].stringValue() shouldBe today.toString()
                body["photos"].size() shouldBe 1
                body["photos"][0]["photoId"].longValue() shouldBe requireNotNull(todayPhoto.id)
            }
        }
    }

    given("날짜 형식이 올바르지 않으면") {
        `when`("내 사진을 조회할 때") {
            then("INVALID_REQUEST 오류를 반환한다") {
                val viewer = register("my-photos-invalid-date")

                listOf("2026-13-40", LocalDate.MAX.toString()).forEach { dateValue ->
                    assertProblem(
                        response = getMyPhotos(viewer.accessToken, dateValue),
                        status = HttpStatus.BAD_REQUEST,
                        errorCode = ErrorCode.INVALID_REQUEST,
                    )
                }
            }
        }
    }

    given("인증 정보가 없으면") {
        `when`("내 사진을 조회할 때") {
            then("401 오류를 반환한다") {
                assertProblem(
                    response = getMyPhotos(null, LocalDate.now(clock).toString()),
                    status = HttpStatus.UNAUTHORIZED,
                    errorCode = ErrorCode.INVALID_AUTH_CREDENTIALS,
                )
            }
        }
    }
})

private data class MyPhotosUserFixture(
    val user: User,
    val accessToken: String,
)
