package com.mogumogu.momogo.reaction.domain

import com.mogumogu.momogo.global.config.JpaConfig
import com.mogumogu.momogo.group.domain.Group
import com.mogumogu.momogo.group.domain.InviteCode
import com.mogumogu.momogo.photo.domain.Photo
import com.mogumogu.momogo.photo.domain.PhotoGroup
import com.mogumogu.momogo.user.domain.User
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.engine.concurrency.TestExecutionMode
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.shouldBe
import jakarta.persistence.EntityManager
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
class PhotoReactionEntityTest(
    private val entityManager: EntityManager,
) : BehaviorSpec({
    testExecutionMode = TestExecutionMode.Sequential

    given("그룹에 올라간 사진과 리액션한 사용자가 있으면") {
        `when`("JPA 엔티티로 저장할 때") {
            then("사진 연결, 사용자와 리액션 내용을 다시 조회할 수 있다") {
                val uploader = User(_nickname = "모고")
                val reactor = User(_nickname = "모모")
                val group = Group(
                    _name = "리액션 그룹",
                    _inviteCode = InviteCode(_value = "RCT100"),
                )
                val photo = Photo(
                    _uploader = uploader,
                    _objectKey = "photos/persisted-reaction.jpg",
                    _sizeBytes = 1_024L,
                    _contentType = "image/jpeg",
                )
                entityManager.persist(uploader)
                entityManager.persist(reactor)
                entityManager.persist(group)
                entityManager.persist(photo)
                val photoGroup = PhotoGroup(_photo = photo, _group = group)
                entityManager.persist(photoGroup)

                val photoReaction = PhotoReaction(
                    _photoGroup = photoGroup,
                    _user = reactor,
                    _concept = ReactionConcept.YOUNG_CREATOR_CREW,
                    _emoji = Emoji.DELICIOUS,
                    _comment = "야르~",
                )
                entityManager.persist(photoReaction)
                entityManager.flush()

                val photoGroupId = requireNotNull(photoGroup.id)
                val reactorId = requireNotNull(reactor.id)
                val photoReactionId = requireNotNull(photoReaction.id)
                entityManager.clear()

                val saved = entityManager.find(PhotoReaction::class.java, photoReactionId)

                saved.photoGroup.id shouldBe photoGroupId
                saved.user.id shouldBe reactorId
                saved.concept shouldBe ReactionConcept.YOUNG_CREATOR_CREW
                saved.emoji shouldBe Emoji.DELICIOUS
                saved.comment shouldBe "야르~"
                saved.updatedAt shouldBeGreaterThanOrEqualTo saved.createdAt
            }
        }
    }

    given("리액션을 남긴 뒤 사진을 그룹에서 내리면") {
        `when`("그 리액션을 다시 조회할 때") {
            then("조회할 수 있다") {
                val uploader = User(_nickname = "모고")
                val reactor = User(_nickname = "모모")
                val group = Group(
                    _name = "리액션 그룹",
                    _inviteCode = InviteCode(_value = "RCT200"),
                )
                val photo = Photo(
                    _uploader = uploader,
                    _objectKey = "photos/unlinked-reaction.jpg",
                    _sizeBytes = 1_024L,
                    _contentType = "image/jpeg",
                )
                entityManager.persist(uploader)
                entityManager.persist(reactor)
                entityManager.persist(group)
                entityManager.persist(photo)
                val photoGroup = PhotoGroup(_photo = photo, _group = group)
                entityManager.persist(photoGroup)
                entityManager.persist(
                    PhotoReaction(
                        _photoGroup = photoGroup,
                        _user = reactor,
                        _concept = ReactionConcept.YOUNG_CREATOR_CREW,
                        _emoji = Emoji.DELICIOUS,
                        _comment = "야르~",
                    ),
                )
                entityManager.flush()

                photoGroup.unlink(Instant.parse("2030-01-01T00:00:00Z"))
                entityManager.flush()
                entityManager.clear()

                val photoGroupId = requireNotNull(photoGroup.id)
                val reactions = entityManager
                    .createQuery(
                        "SELECT r FROM PhotoReaction r WHERE r._photoGroup._id = :photoGroupId",
                        PhotoReaction::class.java,
                    )
                    .setParameter("photoGroupId", photoGroupId)
                    .resultList

                reactions.size shouldBe 1
                reactions[0].comment shouldBe "야르~"
                reactions[0].photoGroup.isActive() shouldBe false
            }
        }
    }

    given("리액션 엔티티의 영속 필드가 캡슐화되어 있으면") {
        then("공개 또는 보호된 JPA 기본 생성자가 있고 공개 setter는 없다") {
            val entityClass = PhotoReaction::class.java

            entityClass.declaredConstructors.any { constructor ->
                constructor.parameterCount == 0 &&
                    (Modifier.isPublic(constructor.modifiers) || Modifier.isProtected(constructor.modifiers))
            } shouldBe true
            entityClass.methods.none { method ->
                Modifier.isPublic(method.modifiers) && method.name.startsWith("set")
            } shouldBe true
        }
    }
})
