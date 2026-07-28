package com.mogumogu.momogo.group.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.security.SecureRandom

@Embeddable
class InviteCode(
    @field:Column(name = "invite_code", nullable = false, length = LENGTH)
    private var _value: String,
) {

    val value: String
        get() = _value

    init {
        require(INVITE_CODE_PATTERN.matches(_value)) {
            "초대 코드는 영문 대문자와 숫자로 구성된 6자리여야 합니다."
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is InviteCode && _value == other._value)

    override fun hashCode(): Int = _value.hashCode()

    companion object {
        private const val LENGTH = 6
        private const val CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        private val INVITE_CODE_PATTERN = Regex("^[A-Z0-9]{$LENGTH}$")
        private val SECURE_RANDOM = SecureRandom()

        fun generate(): InviteCode =
            InviteCode(
                _value = buildString(LENGTH) {
                    repeat(LENGTH) {
                        append(CHARACTERS[SECURE_RANDOM.nextInt(CHARACTERS.length)])
                    }
                },
            )
    }
}
