package com.mogumogu.momogo.user.application

import com.mogumogu.momogo.global.error.ApiException
import com.mogumogu.momogo.global.error.ErrorCode
import com.mogumogu.momogo.user.domain.User
import com.mogumogu.momogo.user.infra.LoginAccountRepository
import com.mogumogu.momogo.user.infra.RefreshTokenRepository
import com.mogumogu.momogo.user.infra.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val userRepository: UserRepository,
    private val loginAccountRepository: LoginAccountRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
) {

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
        val user = findUser(userId)

        refreshTokenRepository.deleteAllByUser_Id(userId)
        refreshTokenRepository.flush()
        loginAccountRepository.deleteAllByUser_Id(userId)
        loginAccountRepository.flush()
        userRepository.delete(user)
        userRepository.flush()
    }

    private fun findUser(userId: Long): User =
        userRepository.findById(userId)
            .orElseThrow { ApiException.NotFound(ErrorCode.USER_NOT_FOUND) }
}

data class UpdateNicknameCommand(
    val userId: Long,
    val nickname: String,
)

data class UpdateNicknameResult(
    val userId: Long,
    val nickname: String,
)
