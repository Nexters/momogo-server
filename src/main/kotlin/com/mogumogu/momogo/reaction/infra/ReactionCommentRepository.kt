package com.mogumogu.momogo.reaction.infra

import com.mogumogu.momogo.reaction.domain.ReactionComment
import org.springframework.data.jpa.repository.JpaRepository

interface ReactionCommentRepository : JpaRepository<ReactionComment, Long>
