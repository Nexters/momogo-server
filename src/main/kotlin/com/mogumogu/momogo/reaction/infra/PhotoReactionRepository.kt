package com.mogumogu.momogo.reaction.infra

import com.mogumogu.momogo.reaction.domain.PhotoReaction
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PhotoReactionRepository : JpaRepository<PhotoReaction, Long> {
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
