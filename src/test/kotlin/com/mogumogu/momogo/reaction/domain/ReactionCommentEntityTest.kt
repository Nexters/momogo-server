package com.mogumogu.momogo.reaction.domain

import com.mogumogu.momogo.global.config.JpaConfig
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

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaConfig::class)
@ApplyExtension(SpringExtension::class)
class ReactionCommentEntityTest(
    private val entityManager: EntityManager,
) : BehaviorSpec({
    testExecutionMode = TestExecutionMode.Sequential

    given("리액션 문구가 있으면") {
        `when`("JPA 엔티티로 저장할 때") {
            then("컨셉, 이모지, 문구와 감사 시각을 다시 조회할 수 있다") {
                val reactionComment = createReactionComment(content = "맛있겠다")

                entityManager.persist(reactionComment)
                entityManager.flush()

                val reactionCommentId = requireNotNull(reactionComment.id)
                entityManager.clear()

                val saved = entityManager.find(ReactionComment::class.java, reactionCommentId)

                saved.concept shouldBe ReactionConcept.YOUNG_CREATOR_CREW
                saved.emoji shouldBe Emoji.DELICIOUS
                saved.content shouldBe "맛있겠다"
                saved.updatedAt shouldBeGreaterThanOrEqualTo saved.createdAt
            }
        }
    }

    given("이미 저장된 리액션 문구가 있으면") {
        `when`("같은 컨셉과 이모지로 같은 문구를 저장할 때") {
            then("복합 고유 제약으로 저장을 거부한다") {
                entityManager.persist(createReactionComment(content = "헐!"))
                entityManager.flush()

                shouldThrow<ConstraintViolationException> {
                    entityManager.persist(createReactionComment(content = "헐!"))
                    entityManager.flush()
                }
            }
        }

        `when`("같은 컨셉과 이모지로 다른 문구를 저장할 때") {
            then("저장을 허용한다") {
                entityManager.persist(createReactionComment(content = "이건 못 참지"))
                entityManager.persist(createReactionComment(content = "군침이 싹 도네"))
                entityManager.flush()
            }
        }
    }

    given("리액션 문구 엔티티의 영속 필드가 캡슐화되어 있으면") {
        then("공개 또는 보호된 JPA 기본 생성자가 있고 공개 setter는 없다") {
            val entityClass = ReactionComment::class.java

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

private fun createReactionComment(content: String): ReactionComment =
    ReactionComment(
        _concept = ReactionConcept.YOUNG_CREATOR_CREW,
        _emoji = Emoji.DELICIOUS,
        _content = content,
    )
