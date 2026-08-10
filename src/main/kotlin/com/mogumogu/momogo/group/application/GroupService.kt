package com.mogumogu.momogo.group.application

import com.mogumogu.momogo.global.error.ApiException
import com.mogumogu.momogo.global.error.ErrorCode
import com.mogumogu.momogo.global.time.DailyTimeRangeFactory
import com.mogumogu.momogo.group.domain.Group
import com.mogumogu.momogo.group.domain.GroupMember
import com.mogumogu.momogo.group.domain.InviteCode
import com.mogumogu.momogo.group.infra.GroupMemberRepository
import com.mogumogu.momogo.group.infra.GroupRepository
import com.mogumogu.momogo.photo.application.PhotoDownloadUrlGenerator
import com.mogumogu.momogo.photo.domain.PhotoObjectKey
import com.mogumogu.momogo.photo.infra.PhotoGroupRepository
import com.mogumogu.momogo.reaction.domain.Emoji
import com.mogumogu.momogo.reaction.domain.ReactionConcept
import com.mogumogu.momogo.reaction.infra.PhotoReactionRepository
import com.mogumogu.momogo.user.infra.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class GroupService(
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val photoGroupRepository: PhotoGroupRepository,
    private val photoReactionRepository: PhotoReactionRepository,
    private val userRepository: UserRepository,
    private val inviteCodeGenerator: InviteCodeGenerator,
    private val photoDownloadUrlGenerator: PhotoDownloadUrlGenerator,
    private val dailyTimeRangeFactory: DailyTimeRangeFactory,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun getJoinedGroups(userId: Long): GetJoinedGroupsResult {
        if (!userRepository.existsById(userId)) {
            throw ApiException.NotFound(ErrorCode.USER_NOT_FOUND)
        }

        val memberships = groupMemberRepository.findAllJoinedWithActiveGroupByUserId(userId)
        val timeRange = dailyTimeRangeFactory.today()
        if (memberships.isEmpty()) {
            return GetJoinedGroupsResult(
                date = timeRange.date,
                groups = emptyList(),
            )
        }

        val groupIds = memberships.map { membership ->
            checkNotNull(membership.group.id) { "저장된 그룹 ID가 없습니다." }
        }
        val memberCountByGroupId = groupMemberRepository.countJoinedByGroupIds(groupIds)
            .associate { count -> count.groupId to count.totalMemberCount }
        val photoUploaderCountByGroupId = photoGroupRepository
            .countPhotoUploadersByGroupIdsAndCreatedAtRange(
                groupIds = groupIds,
                startAt = timeRange.startAt,
                endAt = timeRange.endAt,
            ).associate { count -> count.groupId to count.uploaderCount }
        val latestUploadAtByGroupId = photoGroupRepository
            .findLatestUploadsByGroupIdsExcludingUserId(
                groupIds = groupIds,
                userId = userId,
            ).associate { upload ->
                upload.groupId to LocalDateTime.ofInstant(upload.latestUploadAt, clock.zone)
            }

        return GetJoinedGroupsResult(
            date = timeRange.date,
            groups = memberships.mapNotNull { membership ->
                val group = membership.group
                val groupId = checkNotNull(group.id) { "저장된 그룹 ID가 없습니다." }
                val totalMemberCount = memberCountByGroupId[groupId]
                    ?: return@mapNotNull null
                JoinedGroupResult(
                    groupId = groupId,
                    groupName = group.name,
                    createdAt = LocalDateTime.ofInstant(group.createdAt, clock.zone),
                    totalMemberCount = totalMemberCount,
                    todayPhotoUploaderCount = photoUploaderCountByGroupId[groupId] ?: 0L,
                    latestUploadAt = latestUploadAtByGroupId[groupId],
                )
            },
        )
    }

    @Transactional(readOnly = true)
    fun getGroup(
        userId: Long,
        groupId: Long,
        date: LocalDate?,
    ): GetGroupResult {
        val membership = groupMemberRepository.findJoinedWithActiveGroupByUserIdAndGroupId(
            userId = userId,
            groupId = groupId,
        ) ?: throwGroupAccessException(groupId)
        val group = membership.group
        val timeRange = try {
            date?.let(dailyTimeRangeFactory::create) ?: dailyTimeRangeFactory.today()
        } catch (_: java.time.DateTimeException) {
            throw ApiException.BadRequest(ErrorCode.INVALID_REQUEST)
        }
        val members = groupMemberRepository.findJoinedMemberViewsByGroupId(
            groupId = groupId,
            requestUserId = userId,
        )
        val photoViews = photoGroupRepository.findActiveMemberPhotosByGroupIdAndCreatedAtRange(
            groupId = groupId,
            startAt = timeRange.startAt,
            endAt = timeRange.endAt,
        )
        val photosByUploaderId = buildMap {
            photoViews.forEach { photo -> putIfAbsent(photo.uploaderId, photo) }
        }
        val latestReactionByPhotoGroupId = if (photoViews.isEmpty()) {
            emptyMap()
        } else {
            photoReactionRepository.findLatestByPhotoGroupIds(
                photoViews.map { photo -> photo.photoGroupId },
            ).associateBy { reaction -> reaction.photoGroupId }
        }

        return GetGroupResult(
            groupId = groupId,
            groupName = group.name,
            createdAt = LocalDateTime.ofInstant(group.createdAt, clock.zone),
            date = timeRange.date,
            members = members.map { member ->
                val photo = photosByUploaderId[member.userId]
                GroupMemberResult(
                    userId = member.userId,
                    nickname = member.nickname,
                    mine = member.userId == userId,
                    photo = photo?.let {
                        val generatedUrl = photoDownloadUrlGenerator.generate(
                            PhotoObjectKey.parse(it.objectKey),
                        )
                        val latestReaction = latestReactionByPhotoGroupId[it.photoGroupId]
                        GroupPhotoResult(
                            photoId = it.photoId,
                            downloadUrl = generatedUrl.downloadUrl,
                            contentType = it.contentType,
                            createdAt = LocalDateTime.ofInstant(it.createdAt, clock.zone),
                            expiresAt = generatedUrl.expiresAt,
                            latestReaction = latestReaction?.let { reaction ->
                                LatestPhotoReactionResult(
                                    reactionId = reaction.reactionId,
                                    userId = reaction.userId,
                                    nickname = reaction.nickname,
                                    concept = reaction.concept,
                                    emoji = reaction.emoji,
                                    comment = reaction.comment,
                                    createdAt = LocalDateTime.ofInstant(reaction.createdAt, clock.zone),
                                    mine = reaction.userId == userId,
                                )
                            },
                        )
                    },
                )
            },
        )
    }

    @Transactional
    fun create(command: CreateGroupCommand): CreateGroupResult {
        val user = findUserForUpdate(command.userId)
        val group = groupRepository.save(createGroup(command.name))

        groupMemberRepository.save(
            GroupMember(
                _group = group,
                _user = user,
            ),
        )

        return CreateGroupResult(
            groupId = group.id!!,
            name = group.name,
            inviteCode = group.inviteCode.value,
        )
    }

    @Transactional
    fun update(command: UpdateGroupCommand): UpdateGroupResult {
        val group = groupRepository.findActiveByIdForUpdate(command.groupId)
            ?: throw ApiException.NotFound(ErrorCode.GROUP_NOT_FOUND)
        val membership = groupMemberRepository.findByGroupIdAndUserId(
            groupId = command.groupId,
            userId = command.userId,
        )

        if (membership?.isJoined() != true) {
            throw ApiException.Forbidden(ErrorCode.NOT_GROUP_MEMBER)
        }

        try {
            group.updateName(command.name)
        } catch (_: IllegalArgumentException) {
            throw ApiException.BadRequest(ErrorCode.INVALID_REQUEST)
        }

        return UpdateGroupResult(
            groupId = checkNotNull(group.id) { "저장된 그룹 ID가 없습니다." },
            name = group.name,
        )
    }

    @Transactional(readOnly = true)
    fun getInvitation(command: GetGroupInvitationCommand): GroupInvitationResult {
        val group = findGroupByInviteCode(command.code)
        val groupId = group.id!!
        val membership = groupMemberRepository.findByGroupIdAndUserId(
            groupId = groupId,
            userId = command.userId,
        )

        return GroupInvitationResult(
            groupId = groupId,
            groupName = group.name,
            totalMemberCount = groupMemberRepository.countJoinedByGroupId(groupId),
            participated = membership?.isJoined() == true,
        )
    }

    @Transactional
    fun join(command: JoinGroupCommand): JoinGroupResult {
        val user = findUserForUpdate(command.userId)
        val inviteCode = parseInviteCode(command.code)
        val group = groupRepository.findActiveByInviteCodeForUpdate(inviteCode)
            ?: throw ApiException.NotFound(ErrorCode.INVALID_INVITATION_CODE)
        val groupId = group.id!!
        val membership = groupMemberRepository.findByGroupIdAndUserId(
            groupId = groupId,
            userId = command.userId,
        )

        try {
            membership?.ensureCanJoin()
        } catch (_: IllegalStateException) {
            throw ApiException.Conflict(ErrorCode.ALREADY_JOINED)
        }

        ensureGroupCanJoin(
            group = group,
            joinedMemberCount = groupMemberRepository.countJoinedByGroupId(groupId),
        )

        if (membership != null) {
            groupMemberRepository.delete(membership)
            groupMemberRepository.flush()
        }
        groupMemberRepository.save(
            GroupMember(
                _group = group,
                _user = user,
            ),
        )

        return JoinGroupResult(
            groupId = groupId,
            code = group.inviteCode.value,
        )
    }

    @Transactional
    fun leave(command: LeaveGroupCommand) {
        val group = groupRepository.findActiveByIdForUpdate(command.groupId)
            ?: throw ApiException.NotFound(ErrorCode.GROUP_NOT_FOUND)
        val membership = groupMemberRepository.findJoinedByGroupIdAndUserIdForUpdate(
            groupId = command.groupId,
            userId = command.userId,
        ) ?: throw ApiException.NotFound(ErrorCode.MEMBER_NOT_FOUND)
        val joinedMemberCount = groupMemberRepository.countJoinedByGroupId(command.groupId)
        val leftAt = clock.instant()

        membership.leave(leftAt)
        if (joinedMemberCount == 1L) {
            group.delete(leftAt)
        }
    }

    private fun createGroup(name: String): Group =
        try {
            Group(
                _name = name,
                _inviteCode = inviteCodeGenerator.generate(),
            )
        } catch (_: IllegalArgumentException) {
            throw ApiException.BadRequest(ErrorCode.INVALID_REQUEST)
        }

    private fun findGroupByInviteCode(code: String): Group =
        groupRepository.findActiveByInviteCode(parseInviteCode(code))
            ?: throw ApiException.NotFound(ErrorCode.INVALID_INVITATION_CODE)

    private fun parseInviteCode(code: String): InviteCode =
        try {
            InviteCode(_value = code)
        } catch (_: IllegalArgumentException) {
            throw ApiException.NotFound(ErrorCode.INVALID_INVITATION_CODE)
        }

    private fun findUserForUpdate(userId: Long) =
        userRepository.findByIdForUpdate(userId)
            ?: throw ApiException.NotFound(ErrorCode.USER_NOT_FOUND)

    private fun ensureGroupCanJoin(
        group: Group,
        joinedMemberCount: Long,
    ) {
        try {
            group.ensureCanJoin(joinedMemberCount)
        } catch (_: IllegalStateException) {
            throw ApiException.Conflict(ErrorCode.GROUP_FULL)
        }
    }

    private fun throwGroupAccessException(groupId: Long): Nothing {
        if (groupRepository.existsActiveById(groupId)) {
            throw ApiException.Forbidden(ErrorCode.NOT_GROUP_MEMBER)
        }
        throw ApiException.NotFound(ErrorCode.GROUP_NOT_FOUND)
    }
}

data class GetJoinedGroupsResult(
    val date: LocalDate,
    val groups: List<JoinedGroupResult>,
)

data class JoinedGroupResult(
    val groupId: Long,
    val groupName: String,
    val createdAt: LocalDateTime,
    val totalMemberCount: Long,
    val todayPhotoUploaderCount: Long,
    val latestUploadAt: LocalDateTime?,
)

data class GetGroupResult(
    val groupId: Long,
    val groupName: String,
    val createdAt: LocalDateTime,
    val date: LocalDate,
    val members: List<GroupMemberResult>,
)

data class GroupMemberResult(
    val userId: Long,
    val nickname: String,
    val mine: Boolean,
    val photo: GroupPhotoResult?,
)

data class GroupPhotoResult(
    val photoId: Long,
    val downloadUrl: String,
    val contentType: String,
    val createdAt: LocalDateTime,
    val expiresAt: LocalDateTime,
    val latestReaction: LatestPhotoReactionResult?,
)

data class LatestPhotoReactionResult(
    val reactionId: Long,
    val userId: Long,
    val nickname: String,
    val concept: ReactionConcept,
    val emoji: Emoji,
    val comment: String,
    val createdAt: LocalDateTime,
    val mine: Boolean,
)

data class CreateGroupCommand(
    val userId: Long,
    val name: String,
)

data class CreateGroupResult(
    val groupId: Long,
    val name: String,
    val inviteCode: String,
)

data class UpdateGroupCommand(
    val userId: Long,
    val groupId: Long,
    val name: String,
)

data class UpdateGroupResult(
    val groupId: Long,
    val name: String,
)

data class GetGroupInvitationCommand(
    val userId: Long,
    val code: String,
)

data class GroupInvitationResult(
    val groupId: Long,
    val groupName: String,
    val totalMemberCount: Long,
    val participated: Boolean,
)

data class JoinGroupCommand(
    val userId: Long,
    val code: String,
)

data class JoinGroupResult(
    val groupId: Long,
    val code: String,
)

data class LeaveGroupCommand(
    val userId: Long,
    val groupId: Long,
)
