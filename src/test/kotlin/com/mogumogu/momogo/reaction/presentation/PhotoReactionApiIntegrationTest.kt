package com.mogumogu.momogo.reaction.presentation

import com.mogumogu.momogo.global.error.ErrorCode
import com.mogumogu.momogo.group.infra.GroupRepository
import com.mogumogu.momogo.photo.domain.Photo
import com.mogumogu.momogo.photo.domain.PhotoGroup
import com.mogumogu.momogo.photo.infra.PhotoGroupRepository
import com.mogumogu.momogo.photo.infra.PhotoRepository
import com.mogumogu.momogo.reaction.domain.Emoji
import com.mogumogu.momogo.reaction.domain.ReactionConcept
import com.mogumogu.momogo.reaction.infra.PhotoReactionRepository
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
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ApplyExtension(SpringExtension::class)
class PhotoReactionApiIntegrationTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val userRepository: UserRepository,
    private val groupRepository: GroupRepository,
    private val photoRepository: PhotoRepository,
    private val photoGroupRepository: PhotoGroupRepository,
    private val photoReactionRepository: PhotoReactionRepository,
    private val transactionTemplate: TransactionTemplate,
) : BehaviorSpec({
    testExecutionMode = TestExecutionMode.Sequential

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

    fun register(): ReactionUserFixture {
        val response = performJson(
            post("/api/v1/user/register"),
            json(
                mapOf(
                    "provider" to "GUEST",
                    "providerToken" to "reaction-${UUID.randomUUID()}",
                    "nickname" to "모모",
                ),
            ),
        )
        response.status shouldBe HttpStatus.OK.value()
        val body = objectMapper.readTree(response.contentAsString)

        return ReactionUserFixture(
            userId = body["userId"].longValue(),
            accessToken = body["accessToken"].stringValue(),
        )
    }

    fun createGroup(accessToken: String): Long {
        val response = performJson(
            post("/api/v1/groups").header("Authorization", "Bearer $accessToken"),
            json(mapOf("name" to "리액션 그룹")),
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
                _objectKey = "photos/${UUID.randomUUID()}.jpg",
                _sizeBytes = 1_024L,
                _contentType = "image/jpeg",
            ),
        )

        return photoGroupRepository.saveAndFlush(PhotoGroup(_photo = photo, _group = group))
    }

    fun react(
        accessToken: String,
        photoId: Long,
        groupId: Long,
        emoji: String = "DELICIOUS",
        comment: String = "야르~",
    ): MockHttpServletResponse =
        performJson(
            post("/api/v1/photos/$photoId/reactions")
                .header("Authorization", "Bearer $accessToken"),
            json(
                mapOf(
                    "groupId" to groupId,
                    "concept" to "YOUNG_CREATOR_CREW",
                    "emoji" to emoji,
                    "comment" to comment,
                ),
            ),
        )

    fun reactionIdOf(response: MockHttpServletResponse): Long {
        response.status shouldBe HttpStatus.OK.value()

        return objectMapper.readTree(response.contentAsString)["reactionId"].longValue()
    }

    fun deleteReaction(
        accessToken: String,
        photoId: Long,
        reactionId: Long,
    ): MockHttpServletResponse =
        mockMvc.perform(
            delete("/api/v1/photos/$photoId/reactions/$reactionId")
                .header("Authorization", "Bearer $accessToken"),
        ).andReturn().response

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

    given("그룹에 올라온 사진이 있으면") {
        `when`("그룹 멤버가 리액션을 남길 때") {
            then("리액션을 저장하고 등록 정보를 반환한다") {
                val user = register()
                val groupId = createGroup(user.accessToken)
                val photoGroup = uploadPhoto(user.userId, groupId)
                val photoId = requireNotNull(photoGroup.photo.id)

                val response = react(user.accessToken, photoId, groupId)

                response.status shouldBe HttpStatus.OK.value()
                response.contentType shouldBe MediaType.APPLICATION_JSON_VALUE
                val body = objectMapper.readTree(response.contentAsString)
                body.propertyNames().toSet() shouldBe setOf(
                    "reactionId",
                    "photoId",
                    "groupId",
                    "concept",
                    "emoji",
                    "comment",
                    "createdAt",
                )
                body["photoId"].longValue() shouldBe photoId
                body["groupId"].longValue() shouldBe groupId
                body["concept"].stringValue() shouldBe "YOUNG_CREATOR_CREW"
                body["emoji"].stringValue() shouldBe "DELICIOUS"
                body["comment"].stringValue() shouldBe "야르~"

                transactionTemplate.executeWithoutResult {
                    val saved = photoReactionRepository
                        .findById(body["reactionId"].longValue())
                        .orElseThrow()
                    saved.user.id shouldBe user.userId
                    saved.photoGroup.id shouldBe photoGroup.id
                    saved.concept shouldBe ReactionConcept.YOUNG_CREATOR_CREW
                    saved.emoji shouldBe Emoji.DELICIOUS
                    saved.comment shouldBe "야르~"
                }
            }
        }

        `when`("같은 사용자가 같은 사진에 여러 번 리액션할 때") {
            then("모두 저장한다") {
                val user = register()
                val groupId = createGroup(user.accessToken)
                val photoGroup = uploadPhoto(user.userId, groupId)
                val photoId = requireNotNull(photoGroup.photo.id)
                val countBefore = photoReactionRepository.count()

                react(user.accessToken, photoId, groupId, comment = "야르~").status shouldBe
                    HttpStatus.OK.value()
                react(user.accessToken, photoId, groupId, comment = "야르~").status shouldBe
                    HttpStatus.OK.value()
                react(user.accessToken, photoId, groupId, emoji = "HOT", comment = "매워보여")
                    .status shouldBe HttpStatus.OK.value()

                photoReactionRepository.count() shouldBe countBefore + 3
            }
        }

        `when`("그룹 멤버가 아닌 사용자가 리액션을 남길 때") {
            then("403으로 거절한다") {
                val owner = register()
                val outsider = register()
                val groupId = createGroup(owner.accessToken)
                val photoGroup = uploadPhoto(owner.userId, groupId)
                val photoId = requireNotNull(photoGroup.photo.id)

                assertProblem(
                    response = react(outsider.accessToken, photoId, groupId),
                    status = HttpStatus.FORBIDDEN,
                    errorCode = ErrorCode.NOT_GROUP_MEMBER,
                )
            }
        }

        `when`("이모지 값이 올바르지 않을 때") {
            then("400으로 거절한다") {
                val user = register()
                val groupId = createGroup(user.accessToken)
                val photoGroup = uploadPhoto(user.userId, groupId)
                val photoId = requireNotNull(photoGroup.photo.id)

                assertProblem(
                    response = react(user.accessToken, photoId, groupId, emoji = "UNKNOWN"),
                    status = HttpStatus.BAD_REQUEST,
                    errorCode = ErrorCode.INVALID_REQUEST,
                )
            }
        }
    }

    given("그룹에 없는 사진이면") {
        `when`("리액션을 남길 때") {
            then("404로 거절한다") {
                val user = register()
                val groupId = createGroup(user.accessToken)

                assertProblem(
                    response = react(user.accessToken, photoId = 999_999L, groupId = groupId),
                    status = HttpStatus.NOT_FOUND,
                    errorCode = ErrorCode.PHOTO_NOT_FOUND,
                )
            }
        }
    }

    given("그룹에서 내린 사진이면") {
        `when`("리액션을 남길 때") {
            then("404로 거절한다") {
                val user = register()
                val groupId = createGroup(user.accessToken)
                val photoGroup = uploadPhoto(user.userId, groupId)
                val photoId = requireNotNull(photoGroup.photo.id)
                photoGroup.unlink(Instant.parse("2030-01-01T00:00:00Z"))
                photoGroupRepository.saveAndFlush(photoGroup)

                assertProblem(
                    response = react(user.accessToken, photoId, groupId),
                    status = HttpStatus.NOT_FOUND,
                    errorCode = ErrorCode.PHOTO_NOT_FOUND,
                )
            }
        }

        `when`("이미 남긴 리액션을 삭제할 때") {
            then("삭제를 허용한다") {
                val user = register()
                val groupId = createGroup(user.accessToken)
                val photoGroup = uploadPhoto(user.userId, groupId)
                val photoId = requireNotNull(photoGroup.photo.id)
                val reactionId = reactionIdOf(react(user.accessToken, photoId, groupId))
                photoGroup.unlink(Instant.parse("2030-01-01T00:00:00Z"))
                photoGroupRepository.saveAndFlush(photoGroup)

                val response = deleteReaction(user.accessToken, photoId, reactionId)

                response.status shouldBe HttpStatus.OK.value()
                photoReactionRepository.existsById(reactionId) shouldBe false
            }
        }
    }

    given("내가 남긴 리액션이 있으면") {
        `when`("리액션을 삭제할 때") {
            then("리액션을 지우고 빈 객체를 반환한다") {
                val user = register()
                val groupId = createGroup(user.accessToken)
                val photoGroup = uploadPhoto(user.userId, groupId)
                val photoId = requireNotNull(photoGroup.photo.id)
                val reactionId = reactionIdOf(react(user.accessToken, photoId, groupId))

                val response = deleteReaction(user.accessToken, photoId, reactionId)

                response.status shouldBe HttpStatus.OK.value()
                response.contentType shouldBe MediaType.APPLICATION_JSON_VALUE
                response.contentAsString shouldBe "{}"
                photoReactionRepository.existsById(reactionId) shouldBe false
            }
        }

        `when`("다른 사용자가 그 리액션을 삭제할 때") {
            then("403으로 거절하고 리액션을 남겨 둔다") {
                val owner = register()
                val other = register()
                val groupId = createGroup(owner.accessToken)
                val photoGroup = uploadPhoto(owner.userId, groupId)
                val photoId = requireNotNull(photoGroup.photo.id)
                val reactionId = reactionIdOf(react(owner.accessToken, photoId, groupId))

                assertProblem(
                    response = deleteReaction(other.accessToken, photoId, reactionId),
                    status = HttpStatus.FORBIDDEN,
                    errorCode = ErrorCode.FORBIDDEN,
                )
                photoReactionRepository.existsById(reactionId) shouldBe true
            }
        }

        `when`("다른 사진의 경로로 삭제를 요청할 때") {
            then("404로 거절한다") {
                val user = register()
                val groupId = createGroup(user.accessToken)
                val photoGroup = uploadPhoto(user.userId, groupId)
                val otherPhotoGroup = uploadPhoto(user.userId, groupId)
                val photoId = requireNotNull(photoGroup.photo.id)
                val otherPhotoId = requireNotNull(otherPhotoGroup.photo.id)
                val reactionId = reactionIdOf(react(user.accessToken, photoId, groupId))

                assertProblem(
                    response = deleteReaction(user.accessToken, otherPhotoId, reactionId),
                    status = HttpStatus.NOT_FOUND,
                    errorCode = ErrorCode.REACTION_NOT_FOUND,
                )
                photoReactionRepository.existsById(reactionId) shouldBe true
            }
        }
    }

    given("없는 리액션이면") {
        `when`("삭제를 요청할 때") {
            then("404로 거절한다") {
                val user = register()
                val groupId = createGroup(user.accessToken)
                val photoGroup = uploadPhoto(user.userId, groupId)
                val photoId = requireNotNull(photoGroup.photo.id)

                assertProblem(
                    response = deleteReaction(user.accessToken, photoId, reactionId = 999_999L),
                    status = HttpStatus.NOT_FOUND,
                    errorCode = ErrorCode.REACTION_NOT_FOUND,
                )
            }
        }
    }
})

private data class ReactionUserFixture(
    val userId: Long,
    val accessToken: String,
)
