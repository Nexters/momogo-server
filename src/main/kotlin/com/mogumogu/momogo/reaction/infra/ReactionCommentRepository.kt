package com.mogumogu.momogo.reaction.infra

import com.mogumogu.momogo.reaction.domain.ReactionComment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ReactionCommentRepository : JpaRepository<ReactionComment, Long> {
    @Query(
        """
        SELECT reactionComment
        FROM ReactionComment reactionComment
        ORDER BY reactionComment._concept, reactionComment._emoji, reactionComment._id
        """,
    )
    fun findAllOrdered(): List<ReactionComment>
}
