package com.mogumogu.momogo.group.presentation

import com.mogumogu.momogo.group.domain.GroupMember
import com.mogumogu.momogo.group.infra.GroupMemberRepository
import com.mogumogu.momogo.group.infra.GroupRepository
import com.mogumogu.momogo.photo.domain.Photo
import com.mogumogu.momogo.photo.domain.PhotoContentType
import com.mogumogu.momogo.photo.domain.PhotoGroup
import com.mogumogu.momogo.photo.domain.PhotoObjectKey
import com.mogumogu.momogo.photo.infra.PhotoGroupRepository
import com.mogumogu.momogo.photo.infra.PhotoRepository
import com.mogumogu.momogo.reaction.domain.Emoji
import com.mogumogu.momogo.reaction.domain.PhotoReaction
import com.mogumogu.momogo.reaction.domain.ReactionConcept
import com.mogumogu.momogo.reaction.infra.PhotoReactionRepository
import com.mogumogu.momogo.user.domain.User
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
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/**
 * 그룹 상세는 멤버별 사진과 사진별 최신 리액션을 함께 내려준다.
 * DTO 일괄 조회가 유지되어 멤버나 리액션 수만큼 추가 쿼리가 발생하지 않는지 고정한다.
 */
@SpringBootTest(properties = ["spring.jpa.properties.hibernate.generate_statistics=true"])
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ApplyExtension(SpringExtension::class)
class GroupDetailQueryCountTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val userRepository: UserRepository,
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val photoRepository: PhotoRepository,
    private val photoGroupRepository: PhotoGroupRepository,
    private val photoReactionRepository: PhotoReactionRepository,
    private val entityManagerFactory: EntityManagerFactory,
    private val clock: Clock,
) : BehaviorSpec({
    testExecutionMode = TestExecutionMode.Sequential

    given("여러 그룹원에게 사진과 리액션이 여러 개 있으면") {
        `when`("날짜별 그룹 사진을 조회할 때") {
            then("멤버와 리액션 수에 관계없이 쿼리 수가 고정된다") {
                val registerResponse = mockMvc.perform(
                    post("/api/v1/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            objectMapper.writeValueAsString(
                                mapOf(
                                    "provider" to "GUEST",
                                    "providerToken" to "group-detail-count-${UUID.randomUUID()}",
                                    "nickname" to "모모",
                                ),
                            ),
                        ),
                ).andReturn().response
                registerResponse.status shouldBe HttpStatus.OK.value()
                val registerBody = objectMapper.readTree(registerResponse.contentAsString)
                val viewerId = registerBody["userId"].longValue()
                val accessToken = registerBody["accessToken"].stringValue()

                val groupResponse = mockMvc.perform(
                    post("/api/v1/groups")
                        .header("Authorization", "Bearer $accessToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mapOf("name" to "상세 쿼리 수 그룹"))),
                ).andReturn().response
                groupResponse.status shouldBe HttpStatus.OK.value()
                val groupId = objectMapper.readTree(groupResponse.contentAsString)["groupId"].longValue()
                val group = groupRepository.findById(groupId).orElseThrow()
                val viewer = userRepository.findById(viewerId).orElseThrow()
                val members = buildList {
                    add(viewer)
                    repeat(5) { index ->
                        val member = userRepository.saveAndFlush(User(_nickname = "멤버$index"))
                        groupMemberRepository.saveAndFlush(
                            GroupMember(
                                _group = group,
                                _user = member,
                            ),
                        )
                        add(member)
                    }
                }
                val today = LocalDate.now(clock)
                members.forEach { member ->
                    val objectKey = PhotoObjectKey.generate(
                        phase = "test",
                        userId = requireNotNull(member.id),
                        uploadDate = today,
                        objectId = UUID.randomUUID(),
                        contentType = requireNotNull(PhotoContentType.from("image/jpeg")),
                    )
                    val photo = photoRepository.saveAndFlush(
                        Photo(
                            _uploader = member,
                            _objectKey = objectKey.value,
                            _sizeBytes = 1_024L,
                            _contentType = "image/jpeg",
                        ),
                    )
                    val photoGroup = photoGroupRepository.saveAndFlush(
                        PhotoGroup(
                            _photo = photo,
                            _group = group,
                        ),
                    )
                    repeat(4) {
                        photoReactionRepository.saveAndFlush(
                            PhotoReaction(
                                _photoGroup = photoGroup,
                                _user = viewer,
                                _concept = ReactionConcept.YOUNG_CREATOR_CREW,
                                _emoji = Emoji.DELICIOUS,
                                _comment = "야르~",
                            ),
                        )
                    }
                }

                val statistics = entityManagerFactory.unwrap(SessionFactory::class.java).statistics
                statistics.isStatisticsEnabled = true
                statistics.clear()

                val response = mockMvc.perform(
                    get("/api/v1/groups/{groupId}", groupId)
                        .header("Authorization", "Bearer $accessToken")
                        .queryParam("date", today.toString()),
                ).andReturn().response

                response.status shouldBe HttpStatus.OK.value()
                val body = objectMapper.readTree(response.contentAsString)
                body["members"].size() shouldBe 6
                (0 until body["members"].size()).all { index ->
                    !body["members"][index]["photo"].isNull
                } shouldBe true
                // 그룹 멤버십 및 그룹 / 현재 멤버 / 날짜별 사진 / 사진별 최신 리액션
                statistics.prepareStatementCount shouldBe 4
            }
        }
    }
})
