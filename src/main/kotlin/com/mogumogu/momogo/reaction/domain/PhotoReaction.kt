package com.mogumogu.momogo.reaction.domain

import com.mogumogu.momogo.global.entity.BaseEntity
import com.mogumogu.momogo.photo.domain.PhotoGroup
import com.mogumogu.momogo.user.domain.User
import jakarta.persistence.*

@Entity
@Table(
    name = "photo_reaction",
    indexes = [
        Index(
            name = "idx_photo_reaction_photo_group_created_at",
            columnList = "photo_group_id, created_at, id",
        ),
    ],
)
class PhotoReaction(
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    @field:Column(name = "id")
    private var _id: Long? = null,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(
        name = "photo_group_id",
        nullable = false,
        updatable = false,
        foreignKey = ForeignKey(name = "fk_photo_reaction_photo_group"),
    )
    private var _photoGroup: PhotoGroup,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(
        name = "user_id",
        nullable = false,
        updatable = false,
        foreignKey = ForeignKey(name = "fk_photo_reaction_user"),
    )
    private var _user: User,

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

    @field:Column(
        name = "comment",
        nullable = false,
        updatable = false,
        length = ReactionComment.CONTENT_MAX_LENGTH,
    )
    private var _comment: String,
) : BaseEntity() {

    val id: Long?
        get() = _id

    val photoGroup: PhotoGroup
        get() = _photoGroup

    val user: User
        get() = _user

    val concept: ReactionConcept
        get() = _concept

    val emoji: Emoji
        get() = _emoji

    val comment: String
        get() = _comment

    init {
        // 문구는 reaction_comment에 등록된 값을 그대로 복사해 두므로 같은 규칙으로 검증한다.
        ReactionComment.validateContent(_comment)
        check(_photoGroup.isActive()) { "그룹에서 내린 사진에는 리액션을 남길 수 없습니다." }
    }
}
