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
 * 그룹 목록은 그룹별 멤버와 오늘 사진 활동을 함께 내려준다.
 * DTO 일괄 조회가 유지되어 그룹이나 멤버 수만큼 추가 쿼리가 발생하지 않는지 고정한다.
 */
@SpringBootTest(properties = ["spring.jpa.properties.hibernate.generate_statistics=true"])
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ApplyExtension(SpringExtension::class)
class JoinedGroupsQueryCountTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val userRepository: UserRepository,
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val photoRepository: PhotoRepository,
    private val photoGroupRepository: PhotoGroupRepository,
    private val entityManagerFactory: EntityManagerFactory,
    private val clock: Clock,
) : BehaviorSpec({
    testExecutionMode = TestExecutionMode.Sequential

    given("여러 그룹에 그룹원과 오늘 사진이 여러 개 있으면") {
        `when`("내가 참여한 그룹을 조회할 때") {
            then("그룹과 멤버 수에 관계없이 쿼리 수가 고정된다") {
                val registerResponse = mockMvc.perform(
                    post("/api/v1/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            objectMapper.writeValueAsString(
                                mapOf(
                                    "provider" to "GUEST",
                                    "providerToken" to "joined-groups-count-${UUID.randomUUID()}",
                                    "nickname" to "모모",
                                ),
                            ),
                        ),
                ).andReturn().response
                registerResponse.status shouldBe HttpStatus.OK.value()
                val registerBody = objectMapper.readTree(registerResponse.contentAsString)
                val viewerId = registerBody["userId"].longValue()
                val accessToken = registerBody["accessToken"].stringValue()
                val viewer = userRepository.findById(viewerId).orElseThrow()
                val today = LocalDate.now(clock)

                fun uploadPhoto(
                    uploader: User,
                    group: com.mogumogu.momogo.group.domain.Group,
                ) {
                    val objectKey = PhotoObjectKey.generate(
                        phase = "test",
                        userId = requireNotNull(uploader.id),
                        uploadDate = today,
                        objectId = UUID.randomUUID(),
                        contentType = requireNotNull(PhotoContentType.from("image/jpeg")),
                    )
                    val photo = photoRepository.saveAndFlush(
                        Photo(
                            _uploader = uploader,
                            _objectKey = objectKey.value,
                            _sizeBytes = 1_024L,
                            _contentType = "image/jpeg",
                        ),
                    )
                    photoGroupRepository.saveAndFlush(
                        PhotoGroup(
                            _photo = photo,
                            _group = group,
                        ),
                    )
                }

                repeat(3) { groupIndex ->
                    val groupResponse = mockMvc.perform(
                        post("/api/v1/groups")
                            .header("Authorization", "Bearer $accessToken")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                objectMapper.writeValueAsString(
                                    mapOf("name" to "목록 쿼리 수 그룹$groupIndex"),
                                ),
                            ),
                    ).andReturn().response
                    groupResponse.status shouldBe HttpStatus.OK.value()
                    val groupId = objectMapper.readTree(groupResponse.contentAsString)["groupId"]
                        .longValue()
                    val group = groupRepository.findById(groupId).orElseThrow()

                    repeat(5) { memberIndex ->
                        val member = userRepository.saveAndFlush(
                            User(_nickname = "멤버$groupIndex-$memberIndex"),
                        )
                        groupMemberRepository.saveAndFlush(
                            GroupMember(
                                _group = group,
                                _user = member,
                            ),
                        )
                        uploadPhoto(uploader = member, group = group)
                    }
                    uploadPhoto(uploader = viewer, group = group)
                }

                val statistics = entityManagerFactory.unwrap(SessionFactory::class.java).statistics
                statistics.isStatisticsEnabled = true
                statistics.clear()

                val response = mockMvc.perform(
                    get("/api/v1/groups")
                        .header("Authorization", "Bearer $accessToken"),
                ).andReturn().response

                response.status shouldBe HttpStatus.OK.value()
                val body = objectMapper.readTree(response.contentAsString)
                body["groups"].size() shouldBe 3
                (0 until body["groups"].size()).all { index ->
                    val group = body["groups"][index]
                    group["members"].size() == 6 &&
                        group["todayPhotoUploaderCount"].longValue() == 6L &&
                        group["todayPhotoUploaded"].booleanValue()
                } shouldBe true
                // 사용자 존재 확인 / 그룹 멤버십 및 그룹 / 그룹원 / 오늘 사진 활동 / 최신 업로드
                statistics.prepareStatementCount shouldBe 5
            }
        }
    }
})
