package com.mogumogu.momogo.user.domain

import com.mogumogu.momogo.global.entity.BaseEntity
import jakarta.persistence.*

@Entity
@Table(name = "\"user\"")
class User(
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    @field:Column(name = "id")
    private var _id: Long? = null,

    @field:Column(name = "nickname", nullable = false, length = 12)
    private var _nickname: String,
) : BaseEntity() {

    val id: Long?
        get() = _id

    val nickname: String
        get() = _nickname

    init {
        _nickname = normalizeNickname(_nickname)
    }

    fun updateNickname(nickname: String) {
        _nickname = normalizeNickname(nickname)
    }

    private companion object {
        fun normalizeNickname(nickname: String): String {
            val normalizedNickname = nickname.trim()
            require(normalizedNickname.isNotEmpty()) { "닉네임은 비어 있을 수 없습니다." }
            require(nickname.length <= 6) { "닉네임은 6자를 초과할 수 없습니다." }
            return normalizedNickname
        }
    }
}
