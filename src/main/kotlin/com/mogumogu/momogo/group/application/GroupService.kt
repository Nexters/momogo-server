package com.mogumogu.momogo.group.application

import com.mogumogu.momogo.global.error.ApiException
import com.mogumogu.momogo.global.error.ErrorCode
import com.mogumogu.momogo.group.domain.Group
import com.mogumogu.momogo.group.domain.GroupMember
import com.mogumogu.momogo.group.domain.InviteCode
import com.mogumogu.momogo.group.infra.GroupMemberRepository
import com.mogumogu.momogo.group.infra.GroupRepository
import com.mogumogu.momogo.photo.infra.PhotoGroupRepository
import com.mogumogu.momogo.user.infra.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate

@Service
class GroupService(
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val photoGroupRepository: PhotoGroupRepository,
    private val userRepository: UserRepository,
    private val inviteCodeGenerator: InviteCodeGenerator,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun getJoinedGroups(userId: Long): GetJoinedGroupsResult {
        if (!userRepository.existsById(userId)) {
            throw ApiException.NotFound(ErrorCode.USER_NOT_FOUND)
        }

        val memberships = groupMemberRepository.findAllJoinedWithActiveGroupByUserId(userId)
        val date = LocalDate.now(clock)
        if (memberships.isEmpty()) {
            return GetJoinedGroupsResult(
                date = date,
                groups = emptyList(),
            )
        }

        val groupIds = memberships.map { membership ->
            checkNotNull(membership.group.id) { "저장된 그룹 ID가 없습니다." }
        }
        val memberCountByGroupId = groupMemberRepository.countJoinedByGroupIds(groupIds)
            .associate { count -> count.groupId to count.totalMemberCount }
        val startAt = date.atStartOfDay(clock.zone).toInstant()
        val endAt = date.plusDays(1).atStartOfDay(clock.zone).toInstant()
        val photoUploaderCountByGroupId = photoGroupRepository.countTodayPhotoUploadersByGroupIds(
            groupIds = groupIds,
            startAt = startAt,
            endAt = endAt,
        ).associate { count -> count.groupId to count.uploaderCount }

        return GetJoinedGroupsResult(
            date = date,
            groups = memberships.map { membership ->
                val group = membership.group
                val groupId = checkNotNull(group.id) { "저장된 그룹 ID가 없습니다." }
                JoinedGroupResult(
                    groupId = groupId,
                    groupName = group.name,
                    totalMemberCount = checkNotNull(memberCountByGroupId[groupId]) {
                        "가입 중인 그룹의 멤버 수가 없습니다."
                    },
                    todayPhotoUploaderCount = photoUploaderCountByGroupId[groupId] ?: 0L,
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
}

data class GetJoinedGroupsResult(
    val date: LocalDate,
    val groups: List<JoinedGroupResult>,
)

data class JoinedGroupResult(
    val groupId: Long,
    val groupName: String,
    val totalMemberCount: Long,
    val todayPhotoUploaderCount: Long,
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
