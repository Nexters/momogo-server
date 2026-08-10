package com.mogumogu.momogo.reaction.presentation

import com.mogumogu.momogo.group.infra.GroupRepository
import com.mogumogu.momogo.photo.domain.Photo
import com.mogumogu.momogo.photo.domain.PhotoGroup
import com.mogumogu.momogo.photo.infra.PhotoGroupRepository
import com.mogumogu.momogo.photo.infra.PhotoRepository
import com.mogumogu.momogo.user.infra.UserRepository
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.engine.concurrency.TestExecutionMode
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import jakarta.persistence.EntityManagerFactory
import org.hibernate.SessionFactory
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/**
 * 리액션 조회는 작성자 닉네임을 함께 내려주므로 엔티티를 그대로 조회하면 리액션 수만큼 쿼리가 늘어난다.
 * DTO 프로젝션이 유지되는지 쿼리 수로 고정한다.
 */
@SpringBootTest(properties = ["spring.jpa.properties.hibernate.generate_statistics=true"])
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ApplyExtension(SpringExtension::class)
class PhotoReactionQueryCountTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val userRepository: UserRepository,
    private val groupRepository: GroupRepository,
    private val photoRepository: PhotoRepository,
    private val photoGroupRepository: PhotoGroupRepository,
    private val entityManagerFactory: EntityManagerFactory,
) : BehaviorSpec({
    testExecutionMode = TestExecutionMode.Sequential

    fun json(value: Any): String = objectMapper.writeValueAsString(value)

    fun performJson(
        request: MockHttpServletRequestBuilder,
        content: String,
    ): MockHttpServletResponse =
        mockMvc.perform(
            request.contentType(MediaType.APPLICATION_JSON).content(content),
        ).andReturn().response

    given("리액션이 여러 개 달린 사진이 있으면") {
        `when`("리액션을 조회할 때") {
            then("리액션 수와 상관없이 쿼리 수가 고정된다") {
                val registerResponse = performJson(
                    post("/api/v1/user/register"),
                    json(
                        mapOf(
                            "provider" to "GUEST",
                            "providerToken" to "reaction-count-${UUID.randomUUID()}",
                            "nickname" to "모모",
                        ),
                    ),
                )
                val registerBody = objectMapper.readTree(registerResponse.contentAsString)
                val userId = registerBody["userId"].longValue()
                val accessToken = registerBody["accessToken"].stringValue()

                val groupResponse = performJson(
                    post("/api/v1/groups").header("Authorization", "Bearer $accessToken"),
                    json(mapOf("name" to "쿼리 수 그룹")),
                )
                val groupId = objectMapper.readTree(groupResponse.contentAsString)["groupId"].longValue()

                val photo = photoRepository.saveAndFlush(
                    Photo(
                        _uploader = userRepository.findById(userId).orElseThrow(),
                        _objectKey = "photos/${UUID.randomUUID()}.jpg",
                        _sizeBytes = 1_024L,
                        _contentType = "image/jpeg",
                    ),
                )
                photoGroupRepository.saveAndFlush(
                    PhotoGroup(
                        _photo = photo,
                        _group = groupRepository.findById(groupId).orElseThrow(),
                    ),
                )
                val photoId = requireNotNull(photo.id)

                repeat(5) {
                    performJson(
                        post("/api/v1/groups/$groupId/photos/$photoId/reactions")
                            .header("Authorization", "Bearer $accessToken"),
                        json(
                            mapOf(
                                "concept" to "YOUNG_CREATOR_CREW",
                                "emoji" to "DELICIOUS",
                                "comment" to "야르~",
                            ),
                        ),
                    ).status shouldBe HttpStatus.OK.value()
                }

                val statistics = entityManagerFactory.unwrap(SessionFactory::class.java).statistics
                statistics.isStatisticsEnabled = true
                statistics.clear()

                val response = mockMvc.perform(
                    get("/api/v1/groups/$groupId/photos/$photoId/reactions")
                        .header("Authorization", "Bearer $accessToken"),
                ).andReturn().response

                response.status shouldBe HttpStatus.OK.value()
                objectMapper.readTree(response.contentAsString)["reactions"].size() shouldBe 5
                // 그룹 멤버십 확인 / photo_group 확인 / 리액션 목록
                statistics.prepareStatementCount shouldBe 3
            }
        }
    }
})
