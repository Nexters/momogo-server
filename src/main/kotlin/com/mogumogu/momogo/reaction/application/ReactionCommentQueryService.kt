package com.mogumogu.momogo.reaction.application

import com.mogumogu.momogo.reaction.domain.Emoji
import com.mogumogu.momogo.reaction.domain.ReactionConcept
import com.mogumogu.momogo.reaction.infra.ReactionCommentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class ReactionCommentQueryService(
    private val reactionCommentRepository: ReactionCommentRepository,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun getAll(): ReactionCommentsResult {
        val reactionComments = reactionCommentRepository.findAllOrdered()
        // ponytail: 문구를 삭제하면 최종 수정 시각이 그대로일 수 있어 클라이언트가 갱신을 놓친다.
        // 삭제를 운영에서 쓰게 되면 revision에 문구 개수를 함께 반영한다.
        val revision = reactionComments
            .maxOfOrNull { reactionComment -> reactionComment.updatedAt }
            ?.let { updatedAt -> LocalDateTime.ofInstant(updatedAt, clock.zone) }

        return ReactionCommentsResult(
            revision = revision,
            comments = reactionComments
                .groupBy { reactionComment -> reactionComment.concept to reactionComment.emoji }
                .map { (conceptAndEmoji, comments) ->
                    val (concept, emoji) = conceptAndEmoji

                    ReactionCommentGroupResult(
                        concept = concept,
                        emoji = emoji,
                        contents = comments.map { comment -> comment.content },
                    )
                },
        )
    }
}

data class ReactionCommentsResult(
    val revision: LocalDateTime?,
    val comments: List<ReactionCommentGroupResult>,
)

data class ReactionCommentGroupResult(
    val concept: ReactionConcept,
    val emoji: Emoji,
    val contents: List<String>,
)
