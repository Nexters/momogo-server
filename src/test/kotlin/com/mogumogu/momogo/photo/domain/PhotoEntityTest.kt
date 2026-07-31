package com.mogumogu.momogo.photo.domain

import com.mogumogu.momogo.global.config.JpaConfig
import com.mogumogu.momogo.group.domain.Group
import com.mogumogu.momogo.group.domain.InviteCode
import com.mogumogu.momogo.user.domain.User
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.engine.concurrency.TestExecutionMode
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.shouldBe
import jakarta.persistence.EntityManager
import org.hibernate.exception.ConstraintViolationException
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.lang.reflect.Modifier
import java.time.Instant

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaConfig::class)
@ApplyExtension(SpringExtension::class)
class PhotoEntityTest(
    private val entityManager: EntityManager,
) : BehaviorSpec({
    testExecutionMode = TestExecutionMode.Sequential

    given("업로더의 사진이 그룹에 연결되어 있으면") {
        `when`("JPA 엔티티로 저장할 때") {
            then("사진, 그룹 연결과 감사 시각을 다시 조회할 수 있다") {
                val uploader = User(_nickname = "모고")
                val group = createPersistentGroup(inviteCode = "PHT001")
                val photo = createPersistentPhoto(
                    uploader = uploader,
                    objectKey = "photos/persisted-photo.jpg",
                )
                val photoGroup = PhotoGroup(
                    _photo = photo,
                    _group = group,
                )

                entityManager.persist(uploader)
                entityManager.persist(group)
                entityManager.persist(photo)
                entityManager.persist(photoGroup)
                entityManager.flush()

                val uploaderId = requireNotNull(uploader.id)
                val groupId = requireNotNull(group.id)
                val photoId = requireNotNull(photo.id)
                val photoGroupId = requireNotNull(photoGroup.id)
                entityManager.clear()

                val savedPhoto = entityManager.find(Photo::class.java, photoId)
                val savedPhotoGroup = entityManager.find(PhotoGroup::class.java, photoGroupId)

                savedPhoto.uploader?.id shouldBe uploaderId
                savedPhoto.objectKey shouldBe "photos/persisted-photo.jpg"
                savedPhoto.sizeBytes shouldBe 1_024L
                savedPhoto.contentType shouldBe "image/jpeg"
                savedPhoto.updatedAt shouldBeGreaterThanOrEqualTo savedPhoto.createdAt
                savedPhotoGroup.photo.id shouldBe photoId
                savedPhotoGroup.group.id shouldBe groupId
                savedPhotoGroup.deletedAt shouldBe null
                savedPhotoGroup.updatedAt shouldBeGreaterThanOrEqualTo savedPhotoGroup.createdAt
            }
        }

        `when`("저장된 그룹 연결을 해제할 때") {
            then("해제 시각과 비활성 상태가 데이터베이스에 반영된다") {
                val uploader = User(_nickname = "모고")
                val group = createPersistentGroup(inviteCode = "PHT003")
                val photo = createPersistentPhoto(
                    uploader = uploader,
                    objectKey = "photos/unlinked-photo.jpg",
                )
                val photoGroup = PhotoGroup(
                    _photo = photo,
                    _group = group,
                )
                entityManager.persist(uploader)
                entityManager.persist(group)
                entityManager.persist(photo)
                entityManager.persist(photoGroup)
                entityManager.flush()

                val photoGroupId = requireNotNull(photoGroup.id)
                val deletedAt = Instant.parse("2030-01-01T00:00:00Z")

                photoGroup.unlink(deletedAt)
                entityManager.flush()
                entityManager.clear()

                val savedPhotoGroup = entityManager.find(PhotoGroup::class.java, photoGroupId)

                savedPhotoGroup.deletedAt shouldBe deletedAt
                savedPhotoGroup.isActive() shouldBe false
            }
        }
    }

    given("이미 저장된 object key가 있으면") {
        `when`("같은 object key로 다른 사진을 저장할 때") {
            then("고유 제약으로 저장을 거부한다") {
                val uploader = User(_nickname = "모고")
                entityManager.persist(uploader)
                entityManager.persist(
                    createPersistentPhoto(
                        uploader = uploader,
                        objectKey = "photos/duplicate.jpg",
                    ),
                )
                entityManager.flush()

                shouldThrow<ConstraintViolationException> {
                    entityManager.persist(
                        createPersistentPhoto(
                            uploader = uploader,
                            objectKey = "photos/duplicate.jpg",
                        ),
                    )
                    entityManager.flush()
                }
            }
        }
    }

    given("그룹 연결이 해제된 사진이 있으면") {
        `when`("같은 사진을 같은 그룹에 새 연결로 저장할 때") {
            then("삭제된 연결도 포함하는 복합 고유 제약으로 저장을 거부한다") {
                val uploader = User(_nickname = "모고")
                val group = createPersistentGroup(inviteCode = "PHT002")
                val photo = createPersistentPhoto(
                    uploader = uploader,
                    objectKey = "photos/group-duplicate.jpg",
                )
                val unlinkedPhotoGroup = PhotoGroup(
                    _photo = photo,
                    _group = group,
                )
                entityManager.persist(uploader)
                entityManager.persist(group)
                entityManager.persist(photo)
                entityManager.persist(unlinkedPhotoGroup)
                entityManager.flush()
                unlinkedPhotoGroup.unlink(Instant.parse("2030-01-01T00:00:00Z"))
                entityManager.flush()

                shouldThrow<ConstraintViolationException> {
                    entityManager.persist(
                        PhotoGroup(
                            _photo = photo,
                            _group = group,
                        ),
                    )
                    entityManager.flush()
                }
            }
        }
    }

    given("사진 엔티티의 영속 필드가 캡슐화되어 있으면") {
        then("공개 또는 보호된 JPA 기본 생성자가 있고 공개 setter는 없다") {
            listOf(
                Photo::class.java,
                PhotoGroup::class.java,
            ).forEach { entityClass ->
                entityClass.declaredConstructors.any { constructor ->
                    constructor.parameterCount == 0 &&
                        (Modifier.isPublic(constructor.modifiers) || Modifier.isProtected(constructor.modifiers))
                } shouldBe true
                entityClass.methods.none { method ->
                    Modifier.isPublic(method.modifiers) && method.name.startsWith("set")
                } shouldBe true
            }
        }
    }
})

private fun createPersistentPhoto(
    uploader: User,
    objectKey: String,
): Photo =
    Photo(
        _uploader = uploader,
        _objectKey = objectKey,
        _sizeBytes = 1_024L,
        _contentType = "image/jpeg",
    )

private fun createPersistentGroup(inviteCode: String): Group =
    Group(
        _name = "사진 그룹",
        _inviteCode = InviteCode(_value = inviteCode),
    )
