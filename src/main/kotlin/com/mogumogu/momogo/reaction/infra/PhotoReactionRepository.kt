package com.mogumogu.momogo.reaction.infra

import com.mogumogu.momogo.reaction.domain.PhotoReaction
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PhotoReactionRepository : JpaRepository<PhotoReaction, Long> {
    // 소유자 확인에 사용자 식별자가 필요하다. JOIN FETCH가 없으면 프록시 초기화 쿼리가 한 번 더 나간다.
    // 그룹에서 내린 사진의 리액션도 삭제할 수 있어야 하므로 photo_group 활성 여부는 보지 않는다.
    @Query(
        """
        SELECT photoReaction
        FROM PhotoReaction photoReaction
        JOIN FETCH photoReaction._user
        WHERE photoReaction._id = :reactionId
          AND photoReaction._photoGroup._photo._id = :photoId
        """,
    )
    fun findByIdAndPhotoId(
        @Param("reactionId")
        reactionId: Long,
        @Param("photoId")
        photoId: Long,
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
