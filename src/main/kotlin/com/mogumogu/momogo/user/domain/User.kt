package com.mogumogu.momogo.user.domain

import com.mogumogu.momogo.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "\"user\"")
class User(
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    @field:Column(name = "id")
    private var _id: Long? = null,

    @field:Column(name = "nickname", nullable = false, length = 255)
    private var _nickname: String,
) : BaseEntity() {

    val id: Long?
        get() = _id

    val nickname: String
        get() = _nickname

    init {
        validateNickname(_nickname)
    }

    fun updateNickname(nickname: String) {
        validateNickname(nickname)
        _nickname = nickname
    }

    private companion object {
        fun validateNickname(nickname: String) {
            require(nickname.isNotBlank()) { "닉네임은 비어 있을 수 없습니다." }
            require(nickname.length <= 255) { "닉네임은 255자를 초과할 수 없습니다." }
        }
    }
}
