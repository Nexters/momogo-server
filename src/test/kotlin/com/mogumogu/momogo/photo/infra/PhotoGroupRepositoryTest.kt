package com.mogumogu.momogo.photo.infra

import com.mogumogu.momogo.APPLICATION_TIME_ZONE_ID
import com.mogumogu.momogo.global.config.JpaConfig
import com.mogumogu.momogo.group.domain.Group
import com.mogumogu.momogo.group.domain.GroupMember
import com.mogumogu.momogo.group.domain.InviteCode
import com.mogumogu.momogo.photo.domain.Photo
import com.mogumogu.momogo.photo.domain.PhotoGroup
import com.mogumogu.momogo.user.domain.User
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.engine.concurrency.TestExecutionMode
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import org.hibernate.SessionFactory
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.ZoneId

@DataJpaTest(properties = ["spring.jpa.properties.hibernate.generate_statistics=true"])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaConfig::class)
@ApplyExtension(SpringExtension::class)
class PhotoGroupRepositoryTest(
    private val entityManager: EntityManager,
    private val entityManagerFactory: EntityManagerFactory,
    private val photoGroupRepository: PhotoGroupRepository,
) : BehaviorSpec({
    testExecutionMode = TestExecutionMode.Sequential

    given("한 그룹에 여러 사용자의 사진이 있으면") {
        `when`("오늘 업로더 수와 사용자의 업로드 여부를 조회할 때") {
            then("각 조회를 단일 SQL로 처리하고 연관 엔티티를 추가 조회하지 않는다") {
                val firstUser = User(_nickname = "첫 업로더")
                val secondUser = User(_nickname = "둘 업로더")
                val group = Group(
                    _name = "사진 그룹",
                    _inviteCode = InviteCode(_value = "NPLUS1"),
                )
                listOf(firstUser, secondUser, group).forEach(entityManager::persist)
                listOf(firstUser, secondUser).forEach { user ->
                    entityManager.persist(GroupMember(_group = group, _user = user))
                }
                val photoGroups = listOf(firstUser, secondUser).mapIndexed { index, user ->
                    val photo = Photo(
                        _uploader = user,
                        _objectKey = "photos/n-plus-one-$index.jpg",
                        _sizeBytes = 1_024L,
                        _contentType = "image/jpeg",
                    )
                    entityManager.persist(photo)
                    PhotoGroup(_photo = photo, _group = group).also(entityManager::persist)
                }
                entityManager.flush()

                val groupId = requireNotNull(group.id)
                val firstUserId = requireNotNull(firstUser.id)
                val zoneId = ZoneId.of(APPLICATION_TIME_ZONE_ID)
                val date = photoGroups.first().createdAt.atZone(zoneId).toLocalDate()
                val startAt = date.atStartOfDay(zoneId).toInstant()
                val endAt = date.plusDays(1).atStartOfDay(zoneId).toInstant()
                val statistics = entityManagerFactory.unwrap(SessionFactory::class.java).statistics
                entityManager.clear()

                statistics.clear()
                photoGroupRepository.countPhotoUploadersByGroupIdsAndCreatedAtRange(
                    groupIds = listOf(groupId),
                    startAt = startAt,
                    endAt = endAt,
                ) shouldBe listOf(TodayPhotoUploaderCount(groupId, 2L))
                statistics.prepareStatementCount shouldBe 1L
                statistics.entityFetchCount shouldBe 0L

                statistics.clear()
                photoGroupRepository.existsUploadByUserIdAndGroupIdsAndCreatedAtRange(
                    userId = firstUserId,
                    groupIds = listOf(groupId),
                    startAt = startAt,
                    endAt = endAt,
                ) shouldBe true
                statistics.prepareStatementCount shouldBe 1L
                statistics.entityFetchCount shouldBe 0L
            }
        }
    }

    given("한 사진을 두 그룹에 올린 뒤 한 그룹에서만 사진을 내렸으면") {
        `when`("그룹별 오늘 업로드 여부와 업로더 수를 조회할 때") {
            then("내린 그룹은 두 조회 모두에서 제외해 같은 날 재등록을 허용한다") {
                val user = User(_nickname = "업로더")
                val unlinkedGroup = Group(
                    _name = "사진을 내린 그룹",
                    _inviteCode = InviteCode(_value = "UNLINK"),
                )
                val linkedGroup = Group(
                    _name = "사진이 남은 그룹",
                    _inviteCode = InviteCode(_value = "LINKED"),
                )
                listOf(user, unlinkedGroup, linkedGroup).forEach(entityManager::persist)
                listOf(unlinkedGroup, linkedGroup).forEach { group ->
                    entityManager.persist(GroupMember(_group = group, _user = user))
                }
                val photo = Photo(
                    _uploader = user,
                    _objectKey = "photos/unlinked-then-reupload.jpg",
                    _sizeBytes = 1_024L,
                    _contentType = "image/jpeg",
                )
                entityManager.persist(photo)
                val unlinkedPhotoGroup = PhotoGroup(_photo = photo, _group = unlinkedGroup)
                    .also(entityManager::persist)
                val linkedPhotoGroup = PhotoGroup(_photo = photo, _group = linkedGroup)
                    .also(entityManager::persist)
                entityManager.flush()
                unlinkedPhotoGroup.unlink(linkedPhotoGroup.createdAt)
                entityManager.flush()

                val userId = requireNotNull(user.id)
                val unlinkedGroupId = requireNotNull(unlinkedGroup.id)
                val linkedGroupId = requireNotNull(linkedGroup.id)
                val zoneId = ZoneId.of(APPLICATION_TIME_ZONE_ID)
                val date = linkedPhotoGroup.createdAt.atZone(zoneId).toLocalDate()
                val startAt = date.atStartOfDay(zoneId).toInstant()
                val endAt = date.plusDays(1).atStartOfDay(zoneId).toInstant()
                entityManager.clear()

                photoGroupRepository.existsUploadByUserIdAndGroupIdsAndCreatedAtRange(
                    userId = userId,
                    groupIds = listOf(unlinkedGroupId),
                    startAt = startAt,
                    endAt = endAt,
                ) shouldBe false
                photoGroupRepository.existsUploadByUserIdAndGroupIdsAndCreatedAtRange(
                    userId = userId,
                    groupIds = listOf(linkedGroupId),
                    startAt = startAt,
                    endAt = endAt,
                ) shouldBe true
                photoGroupRepository.countPhotoUploadersByGroupIdsAndCreatedAtRange(
                    groupIds = listOf(unlinkedGroupId, linkedGroupId),
                    startAt = startAt,
                    endAt = endAt,
                ) shouldBe listOf(TodayPhotoUploaderCount(linkedGroupId, 1L))
            }
        }
    }
})
