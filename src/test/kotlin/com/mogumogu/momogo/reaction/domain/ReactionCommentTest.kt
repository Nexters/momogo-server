package com.mogumogu.momogo.reaction.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class ReactionCommentTest : BehaviorSpec({

    given("유효한 리액션 문구 정보가 있으면") {
        `when`("리액션 문구를 생성할 때") {
            then("문구 정보를 읽기 전용으로 조회할 수 있다") {
                val reactionComment = ReactionComment(
                    _id = 1L,
                    _concept = ReactionConcept.YOUNG_CREATOR_CREW,
                    _emoji = Emoji.DELICIOUS,
                    _content = "맛있겠다",
                )

                reactionComment.id shouldBe 1L
                reactionComment.concept shouldBe ReactionConcept.YOUNG_CREATOR_CREW
                reactionComment.emoji shouldBe Emoji.DELICIOUS
                reactionComment.content shouldBe "맛있겠다"
            }
        }

        `when`("허용되는 경계값으로 리액션 문구를 생성할 때") {
            then("문구 30자를 허용한다") {
                val reactionComment = createReactionComment(_content = "맛".repeat(30))

                reactionComment.content.length shouldBe 30
            }
        }
    }

    given("유효하지 않은 리액션 문구 정보가 있으면") {
        `when`("문구가 비어 있을 때") {
            then("리액션 문구 생성을 거부한다") {
                listOf("", " ", "\t").forEach { content ->
                    shouldThrow<IllegalArgumentException> {
                        createReactionComment(_content = content)
                    }
                }
            }
        }

        `when`("문구가 30자를 초과할 때") {
            then("리액션 문구 생성을 거부한다") {
                shouldThrow<IllegalArgumentException> {
                    createReactionComment(_content = "맛".repeat(31))
                }
            }
        }
    }
})

private fun createReactionComment(
    _concept: ReactionConcept = ReactionConcept.YOUNG_CREATOR_CREW,
    _emoji: Emoji = Emoji.DELICIOUS,
    _content: String = "맛있겠다",
): ReactionComment =
    ReactionComment(
        _concept = _concept,
        _emoji = _emoji,
        _content = _content,
    )
