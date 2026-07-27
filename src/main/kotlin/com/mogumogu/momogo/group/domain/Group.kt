package com.mogumogu.momogo.group.domain

import com.mogumogu.momogo.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "\"group\"",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_group_invite_code",
            columnNames = ["invite_code"],
        ),
    ],
)
class Group(
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    @field:Column(name = "id")
    private var _id: Long? = null,

    @field:Column(name = "name", nullable = false, length = 255)
    private var _name: String,

    @field:Column(name = "invite_code", nullable = false, length = 255)
    private var _inviteCode: String,
) : BaseEntity() {

    val id: Long?
        get() = _id

    val name: String
        get() = _name

    val inviteCode: String
        get() = _inviteCode

    init {
        validateName(_name)
        validateInviteCode(_inviteCode)
    }

    fun updateName(name: String) {
        validateName(name)
        _name = name
    }

    fun regenerateInviteCode(inviteCode: String) {
        validateInviteCode(inviteCode)
        _inviteCode = inviteCode
    }

    private companion object {
        fun validateName(name: String) {
            require(name.isNotBlank()) { "그룹명은 비어 있을 수 없습니다." }
            require(name.length <= 255) { "그룹명은 255자를 초과할 수 없습니다." }
        }

        fun validateInviteCode(inviteCode: String) {
            require(inviteCode.isNotBlank()) { "초대 코드는 비어 있을 수 없습니다." }
            require(inviteCode.length <= 255) { "초대 코드는 255자를 초과할 수 없습니다." }
        }
    }
}
