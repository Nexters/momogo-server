package com.mogumogu.momogo.reaction.domain

import com.mogumogu.momogo.global.entity.BaseEntity
import jakarta.persistence.*

@Entity
@Table(
    name = "reaction_comment",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_reaction_comment_concept_emoji_content",
            columnNames = ["concept", "emoji", "content"],
        ),
    ],
)
class ReactionComment(
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    @field:Column(name = "id")
    private var _id: Long? = null,

    @field:Enumerated(EnumType.STRING)
    @field:Column(
        name = "concept",
        nullable = false,
        updatable = false,
        length = 255,
        columnDefinition = "VARCHAR(255)",
    )
    private var _concept: ReactionConcept,

    @field:Enumerated(EnumType.STRING)
    @field:Column(
        name = "emoji",
        nullable = false,
        updatable = false,
        length = 255,
        columnDefinition = "VARCHAR(255)",
    )
    private var _emoji: Emoji,

    @field:Column(name = "content", nullable = false, length = CONTENT_MAX_LENGTH)
    private var _content: String,
) : BaseEntity() {

    val id: Long?
        get() = _id

    val concept: ReactionConcept
        get() = _concept

    val emoji: Emoji
        get() = _emoji

    val content: String
        get() = _content

    init {
        validateContent(_content)
    }

    private companion object {
        const val CONTENT_MAX_LENGTH = 30

        fun validateContent(content: String) {
            require(content.isNotBlank()) { "리액션 문구는 비어 있을 수 없습니다." }
            require(content.length <= CONTENT_MAX_LENGTH) {
                "리액션 문구는 ${CONTENT_MAX_LENGTH}자를 초과할 수 없습니다."
            }
        }
    }
}
