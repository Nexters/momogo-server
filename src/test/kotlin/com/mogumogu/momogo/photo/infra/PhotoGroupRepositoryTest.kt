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
import java.time.Instant
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
                photoGroupRepository.findPhotoActivitiesByGroupIdsAndCreatedAtRange(
                    groupIds = listOf(groupId),
                    userId = firstUserId,
                    startAt = startAt,
                    endAt = endAt,
                ) shouldBe listOf(GroupPhotoActivity(groupId, 2L, 1L))
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
                photoGroupRepository.findPhotoActivitiesByGroupIdsAndCreatedAtRange(
                    groupIds = listOf(unlinkedGroupId, linkedGroupId),
                    userId = userId,
                    startAt = startAt,
                    endAt = endAt,
                ) shouldBe listOf(GroupPhotoActivity(linkedGroupId, 1L, 1L))
            }
        }
    }

    given("여러 그룹에 본인과 다른 사용자의 사진이 있으면") {
        `when`("그룹별 다른 사용자의 최신 활성 업로드를 조회할 때") {
            then("현재 활성 멤버의 사진만 단일 SQL로 최신 시각을 집계한다") {
                val requestingUser = User(_nickname = "조회자")
                val otherUploader = User(_nickname = "다른 업로더")
                val formerUploader = User(_nickname = "과거 멤버")
                val anonymousUploader = User(_nickname = "익명 업로더")
                val groupWithAnonymousUpload = Group(
                    _name = "익명 사진 그룹",
                    _inviteCode = InviteCode(_value = "LATEST"),
                )
                val groupWithFormerMemberUpload = Group(
                    _name = "과거 멤버 사진 그룹",
                    _inviteCode = InviteCode(_value = "FORMER"),
                )
                val groupWithoutOtherUploads = Group(
                    _name = "본인 사진 그룹",
                    _inviteCode = InviteCode(_value = "NOOTHR"),
                )
                listOf(
                    requestingUser,
                    otherUploader,
                    formerUploader,
                    anonymousUploader,
                    groupWithAnonymousUpload,
                    groupWithFormerMemberUpload,
                    groupWithoutOtherUploads,
                ).forEach(entityManager::persist)
                listOf(
                    groupWithAnonymousUpload,
                    groupWithFormerMemberUpload,
                    groupWithoutOtherUploads,
                ).forEach { group ->
                    entityManager.persist(GroupMember(_group = group, _user = requestingUser))
                }
                entityManager.persist(
                    GroupMember(
                        _group = groupWithAnonymousUpload,
                        _user = otherUploader,
                    ),
                )
                entityManager.persist(
                    GroupMember(
                        _group = groupWithFormerMemberUpload,
                        _user = formerUploader,
                    ).apply {
                        leave(Instant.parse("2026-08-10T00:00:00Z"))
                    },
                )

                fun savePhotoGroup(
                    uploader: User,
                    group: Group,
                    objectKey: String,
                ): PhotoGroup {
                    val photo = Photo(
                        _uploader = uploader,
                        _objectKey = objectKey,
                        _sizeBytes = 1_024L,
                        _contentType = "image/jpeg",
                    ).also(entityManager::persist)
                    return PhotoGroup(_photo = photo, _group = group).also(entityManager::persist)
                }

                val olderOtherUpload = savePhotoGroup(
                    otherUploader,
                    groupWithAnonymousUpload,
                    "photos/latest-other.jpg",
                )
                val latestAnonymousUpload = savePhotoGroup(
                    anonymousUploader,
                    groupWithAnonymousUpload,
                    "photos/latest-anonymous.jpg",
                )
                val requestingUserUpload = savePhotoGroup(
                    requestingUser,
                    groupWithAnonymousUpload,
                    "photos/latest-requester.jpg",
                )
                val unlinkedOtherUpload = savePhotoGroup(
                    otherUploader,
                    groupWithAnonymousUpload,
                    "photos/latest-unlinked.jpg",
                )
                val formerMemberUpload = savePhotoGroup(
                    formerUploader,
                    groupWithFormerMemberUpload,
                    "photos/latest-former.jpg",
                )
                val onlyRequestingUserUpload = savePhotoGroup(
                    requestingUser,
                    groupWithoutOtherUploads,
                    "photos/latest-requester-only.jpg",
                )
                entityManager.flush()

                unlinkedOtherUpload.unlink(Instant.parse("2026-08-10T15:00:00Z"))
                entityManager.flush()
                val uploadTimes = mapOf(
                    olderOtherUpload to Instant.parse("2026-08-10T10:00:00Z"),
                    formerMemberUpload to Instant.parse("2026-08-10T11:00:00Z"),
                    latestAnonymousUpload to Instant.parse("2026-08-10T12:00:00Z"),
                    requestingUserUpload to Instant.parse("2026-08-10T13:00:00Z"),
                    unlinkedOtherUpload to Instant.parse("2026-08-10T14:00:00Z"),
                    onlyRequestingUserUpload to Instant.parse("2026-08-10T15:00:00Z"),
                )
                uploadTimes.forEach { (photoGroup, createdAt) ->
                    entityManager.createNativeQuery(
                        "UPDATE photo_group SET created_at = :createdAt WHERE id = :id",
                    )
                        .setParameter("createdAt", createdAt)
                        .setParameter("id", requireNotNull(photoGroup.id))
                        .executeUpdate()
                }
                entityManager.createNativeQuery(
                    "UPDATE photo SET user_id = NULL WHERE id = :id",
                )
                    .setParameter("id", requireNotNull(latestAnonymousUpload.photo.id))
                    .executeUpdate()
                entityManager.clear()

                val groupIds = listOf(
                    requireNotNull(groupWithAnonymousUpload.id),
                    requireNotNull(groupWithFormerMemberUpload.id),
                    requireNotNull(groupWithoutOtherUploads.id),
                )
                val statistics = entityManagerFactory.unwrap(SessionFactory::class.java).statistics
                statistics.clear()

                val latestUploadsByGroupId = photoGroupRepository
                    .findLatestUploadsByGroupIdsExcludingUserId(
                        groupIds = groupIds,
                        userId = requireNotNull(requestingUser.id),
                    ).associateBy { upload -> upload.groupId }

                latestUploadsByGroupId.keys shouldBe setOf(
                    requireNotNull(groupWithAnonymousUpload.id),
                )
                latestUploadsByGroupId
                    .getValue(requireNotNull(groupWithAnonymousUpload.id))
                    .latestUploadAt shouldBe uploadTimes.getValue(olderOtherUpload)
                statistics.prepareStatementCount shouldBe 1L
                statistics.entityFetchCount shouldBe 0L
            }
        }
    }
})
