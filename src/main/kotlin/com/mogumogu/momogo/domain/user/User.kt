package com.mogumogu.momogo.domain.user

class User(
    val id: Long? = null,
    var nickname: String,
) {
    init {
        validateNickname(nickname)
    }

    fun changeNickname(nickname: String) {
        validateNickname(nickname)
        this.nickname = nickname
    }

    private fun validateNickname(nickname: String) {
        require(nickname.length in MIN_NICKNAME_LENGTH..MAX_NICKNAME_LENGTH) {
            "닉네임은 ${MIN_NICKNAME_LENGTH}자 이상 ${MAX_NICKNAME_LENGTH}자 이하여야 합니다."
        }
    }

    private companion object {
        const val MIN_NICKNAME_LENGTH = 1
        const val MAX_NICKNAME_LENGTH = 12
    }
}
