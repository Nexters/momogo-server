package com.mogumogu.momogo.reaction.infra

import com.mogumogu.momogo.reaction.domain.Emoji
import com.mogumogu.momogo.reaction.domain.PhotoReaction
import com.mogumogu.momogo.reaction.domain.ReactionConcept
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface PhotoReactionRepository : JpaRepository<PhotoReaction, Long> {
    // 목록에 작성자 닉네임이 필요하다. 엔티티를 그대로 조회하면 _user가 LAZY라 리액션 수만큼 쿼리가 나가므로
    // DTO 프로젝션으로 필요한 컬럼만 뽑는다.
    @Query(
        """
        SELECT new com.mogumogu.momogo.reaction.infra.PhotoReactionView(
            photoReaction._id,
            reactor._id,
            reactor._nickname,
            photoReaction._concept,
            photoReaction._emoji,
            photoReaction._comment,
            photoReaction._createdAt
        )
        FROM PhotoReaction photoReaction
        JOIN photoReaction._user reactor
        WHERE photoReaction._photoGroup._photo._id = :photoId
          AND photoReaction._photoGroup._group._id = :groupId
        ORDER BY photoReaction._createdAt, photoReaction._id
        """,
    )
    fun findAllByPhotoIdAndGroupId(
        @Param("photoId")
        photoId: Long,
        @Param("groupId")
        groupId: Long,
    ): List<PhotoReactionView>

    // 소유자 확인에 사용자 식별자가 필요하다. JOIN FETCH가 없으면 프록시 초기화 쿼리가 한 번 더 나간다.
    // 그룹에서 내린 사진의 리액션도 삭제할 수 있어야 하므로 photo_group 활성 여부는 보지 않는다.
    @Query(
        """
        SELECT photoReaction
        FROM PhotoReaction photoReaction
        JOIN FETCH photoReaction._user
        WHERE photoReaction._id = :reactionId
          AND photoReaction._photoGroup._photo._id = :photoId
          AND photoReaction._photoGroup._group._id = :groupId
        """,
    )
    fun findByIdAndPhotoIdAndGroupId(
        @Param("reactionId")
        reactionId: Long,
        @Param("photoId")
        photoId: Long,
        @Param("groupId")
        groupId: Long,
    ): PhotoReaction?

    @Modifying(flushAutomatically = true)
    @Query(
        """
        DELETE FROM PhotoReaction photoReaction
        WHERE photoReaction._user._id = :userId
        """,
    )
    fun deleteAllByUserId(
        @Param("userId")
        userId: Long,
    ): Int
}

data class PhotoReactionView(
    val reactionId: Long,
    val userId: Long,
    val nickname: String,
    val concept: ReactionConcept,
    val emoji: Emoji,
    val comment: String,
    val createdAt: Instant,
)
