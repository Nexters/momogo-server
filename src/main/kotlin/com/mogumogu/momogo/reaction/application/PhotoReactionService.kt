package com.mogumogu.momogo.reaction.application

import com.mogumogu.momogo.global.error.ApiException
import com.mogumogu.momogo.global.error.ErrorCode
import com.mogumogu.momogo.group.infra.GroupMemberRepository
import com.mogumogu.momogo.photo.infra.PhotoGroupRepository
import com.mogumogu.momogo.reaction.domain.Emoji
import com.mogumogu.momogo.reaction.domain.PhotoReaction
import com.mogumogu.momogo.reaction.domain.ReactionConcept
import com.mogumogu.momogo.reaction.infra.PhotoReactionRepository
import com.mogumogu.momogo.user.infra.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class PhotoReactionService(
    private val photoReactionRepository: PhotoReactionRepository,
    private val photoGroupRepository: PhotoGroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val userRepository: UserRepository,
    private val clock: Clock,
) {

    @Transactional
    fun create(command: CreatePhotoReactionCommand): CreatePhotoReactionResult {
        ensureJoinedGroup(
            userId = command.userId,
            groupId = command.groupId,
        )

        val photoGroup = photoGroupRepository.findActiveByPhotoIdAndGroupId(
            photoId = command.photoId,
            groupId = command.groupId,
        ) ?: throw ApiException.NotFound(ErrorCode.PHOTO_NOT_FOUND)
        val reactor = userRepository.findById(command.userId)
            .orElseThrow { ApiException.NotFound(ErrorCode.USER_NOT_FOUND) }

        val photoReaction = try {
            PhotoReaction(
                _photoGroup = photoGroup,
                _user = reactor,
                _concept = command.concept,
                _emoji = command.emoji,
                _comment = command.comment,
            )
        } catch (_: IllegalArgumentException) {
            throw ApiException.BadRequest(ErrorCode.INVALID_REQUEST)
        }
        photoReactionRepository.save(photoReaction)

        return CreatePhotoReactionResult(
            reactionId = checkNotNull(photoReaction.id) { "저장된 리액션 ID가 없습니다." },
            photoId = command.photoId,
            groupId = command.groupId,
            concept = photoReaction.concept,
            emoji = photoReaction.emoji,
            comment = photoReaction.comment,
            createdAt = LocalDateTime.ofInstant(photoReaction.createdAt, clock.zone),
        )
    }

    @Transactional(readOnly = true)
    fun getReactions(command: GetPhotoReactionsCommand): PhotoReactionsResult {
        ensureJoinedGroup(
            userId = command.userId,
            groupId = command.groupId,
        )
        if (
            photoGroupRepository.findActiveByPhotoIdAndGroupId(
                photoId = command.photoId,
                groupId = command.groupId,
            ) == null
        ) {
            throw ApiException.NotFound(ErrorCode.PHOTO_NOT_FOUND)
        }

        val reactions = photoReactionRepository.findAllByPhotoIdAndGroupId(
            photoId = command.photoId,
            groupId = command.groupId,
        )

        return PhotoReactionsResult(
            photoId = command.photoId,
            groupId = command.groupId,
            reactions = reactions.map { reaction ->
                PhotoReactionResult(
                    reactionId = reaction.reactionId,
                    userId = reaction.userId,
                    nickname = reaction.nickname,
                    concept = reaction.concept,
                    emoji = reaction.emoji,
                    comment = reaction.comment,
                    createdAt = LocalDateTime.ofInstant(reaction.createdAt, clock.zone),
                    mine = reaction.userId == command.userId,
                )
            },
        )
    }

    @Transactional
    fun delete(command: DeletePhotoReactionCommand) {
        val photoReaction = photoReactionRepository.findByIdAndPhotoIdAndGroupId(
            reactionId = command.reactionId,
            photoId = command.photoId,
            groupId = command.groupId,
        ) ?: throw ApiException.NotFound(ErrorCode.REACTION_NOT_FOUND)
        if (!photoReaction.isOwnedBy(command.userId)) {
            throw ApiException.Forbidden(ErrorCode.FORBIDDEN)
        }

        photoReactionRepository.delete(photoReaction)
    }

    // 그룹 멤버가 아닌 사용자에게 사진의 존재 여부를 알려주지 않도록 멤버십을 먼저 확인한다.
    private fun ensureJoinedGroup(
        userId: Long,
        groupId: Long,
    ) {
        if (
            groupMemberRepository.findJoinedGroupIdsByUserIdAndGroupIds(
                userId = userId,
                groupIds = listOf(groupId),
            ).isEmpty()
        ) {
            throw ApiException.Forbidden(ErrorCode.NOT_GROUP_MEMBER)
        }
    }
}

data class DeletePhotoReactionCommand(
    val userId: Long,
    val photoId: Long,
    val groupId: Long,
    val reactionId: Long,
)

data class GetPhotoReactionsCommand(
    val userId: Long,
    val photoId: Long,
    val groupId: Long,
)

data class PhotoReactionsResult(
    val photoId: Long,
    val groupId: Long,
    val reactions: List<PhotoReactionResult>,
)

data class PhotoReactionResult(
    val reactionId: Long,
    val userId: Long,
    val nickname: String,
    val concept: ReactionConcept,
    val emoji: Emoji,
    val comment: String,
    val createdAt: LocalDateTime,
    val mine: Boolean,
)

data class CreatePhotoReactionCommand(
    val userId: Long,
    val photoId: Long,
    val groupId: Long,
    val concept: ReactionConcept,
    val emoji: Emoji,
    val comment: String,
)

data class CreatePhotoReactionResult(
    val reactionId: Long,
    val photoId: Long,
    val groupId: Long,
    val concept: ReactionConcept,
    val emoji: Emoji,
    val comment: String,
    val createdAt: LocalDateTime,
)
