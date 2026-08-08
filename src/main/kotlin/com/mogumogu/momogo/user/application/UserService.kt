package com.mogumogu.momogo.user.application

import com.mogumogu.momogo.global.error.ApiException
import com.mogumogu.momogo.global.error.ErrorCode
import com.mogumogu.momogo.group.infra.GroupMemberRepository
import com.mogumogu.momogo.group.infra.GroupRepository
import com.mogumogu.momogo.photo.infra.PhotoRepository
import com.mogumogu.momogo.reaction.infra.PhotoReactionRepository
import com.mogumogu.momogo.user.domain.User
import com.mogumogu.momogo.user.infra.LoginAccountRepository
import com.mogumogu.momogo.user.infra.RefreshTokenRepository
import com.mogumogu.momogo.user.infra.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class UserService(
    private val userRepository: UserRepository,
    private val loginAccountRepository: LoginAccountRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val groupRepository: GroupRepository,
    private val photoRepository: PhotoRepository,
    private val photoReactionRepository: PhotoReactionRepository,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun getUser(userId: Long): GetUserResult {
        val user = userRepository.findById(userId)
            .orElseThrow { ApiException.NotFound(ErrorCode.USER_NOT_FOUND) }

        return GetUserResult(
            userId = checkNotNull(user.id) { "저장된 사용자 ID가 없습니다." },
            nickname = user.nickname,
        )
    }

    @Transactional
    fun updateNickname(command: UpdateNicknameCommand): UpdateNicknameResult {
        val user = findUser(command.userId)

        try {
            user.updateNickname(command.nickname)
        } catch (_: IllegalArgumentException) {
            throw ApiException.BadRequest(ErrorCode.INVALID_REQUEST)
        }

        return UpdateNicknameResult(
            userId = checkNotNull(user.id) { "저장된 사용자 ID가 없습니다." },
            nickname = user.nickname,
        )
    }

    @Transactional
    fun withdraw(userId: Long) {
        refreshTokenRepository.findAllByUserIdForUpdate(userId)
        val user = findUser(userId)
        val deletedAt = clock.instant()

        groupMemberRepository.findJoinedGroupIdsByUserId(userId)
            .distinct()
            .sorted()
            .forEach { groupId ->
                val group = groupRepository.findActiveByIdForUpdate(groupId) ?: return@forEach
                if (groupMemberRepository.countJoinedByGroupId(groupId) == 1L) {
                    group.delete(deletedAt)
                }
            }

        photoReactionRepository.deleteAllByUserId(userId)
        photoReactionRepository.flush()
        groupMemberRepository.deleteAllByUserId(userId)
        groupMemberRepository.flush()
        refreshTokenRepository.deleteAllByUser_Id(userId)
        refreshTokenRepository.flush()
        loginAccountRepository.deleteAllByUser_Id(userId)
        loginAccountRepository.flush()
        photoRepository.clearUploaderByUserId(userId)
        userRepository.delete(user)
        userRepository.flush()
    }

    private fun findUser(userId: Long): User =
        userRepository.findByIdForUpdate(userId)
            ?: throw ApiException.NotFound(ErrorCode.USER_NOT_FOUND)
}

data class GetUserResult(
    val userId: Long,
    val nickname: String,
)

data class UpdateNicknameCommand(
    val userId: Long,
    val nickname: String,
)

data class UpdateNicknameResult(
    val userId: Long,
    val nickname: String,
)
