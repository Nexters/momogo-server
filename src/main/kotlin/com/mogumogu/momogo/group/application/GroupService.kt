package com.mogumogu.momogo.group.application

import com.mogumogu.momogo.global.error.ApiException
import com.mogumogu.momogo.global.error.ErrorCode
import com.mogumogu.momogo.group.domain.Group
import com.mogumogu.momogo.group.domain.GroupMember
import com.mogumogu.momogo.group.infra.GroupMemberRepository
import com.mogumogu.momogo.group.infra.GroupRepository
import com.mogumogu.momogo.user.infra.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GroupService(
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val userRepository: UserRepository,
    private val inviteCodeGenerator: InviteCodeGenerator,
) {

    @Transactional
    fun create(command: CreateGroupCommand): CreateGroupResult {
        val user = userRepository.findById(command.userId)
            .orElseThrow { ApiException.NotFound(ErrorCode.USER_NOT_FOUND) }
        val group = groupRepository.save(createGroup(command.name))

        groupMemberRepository.save(
            GroupMember(
                _group = group,
                _user = user,
            ),
        )

        return CreateGroupResult(
            groupId = checkNotNull(group.id) { "저장된 그룹 ID가 없습니다." },
            name = group.name,
            inviteCode = group.inviteCode.value,
        )
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
}

data class CreateGroupCommand(
    val userId: Long,
    val name: String,
)

data class CreateGroupResult(
    val groupId: Long,
    val name: String,
    val inviteCode: String,
)
