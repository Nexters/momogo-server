package com.mogumogu.momogo.group.domain

import com.mogumogu.momogo.global.entity.BaseEntity
import jakarta.persistence.*

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

    @field:Embedded
    private var _inviteCode: InviteCode,
) : BaseEntity() {

    val id: Long?
        get() = _id

    val name: String
        get() = _name

    val inviteCode: InviteCode
        get() = _inviteCode

    init {
        validateName(_name)
    }

    fun updateName(name: String) {
        validateName(name)
        _name = name
    }

    fun regenerateInviteCode(inviteCode: InviteCode) {
        _inviteCode = inviteCode
    }

    fun ensureCanJoin(activeMemberCount: Long) {
        require(activeMemberCount >= 0) { "활성 멤버 수는 0명 이상이어야 합니다." }
        check(activeMemberCount < MAX_MEMBER_COUNT) {
            "그룹은 최대 ${MAX_MEMBER_COUNT}명까지 가입할 수 있습니다."
        }
    }

    private companion object {
        const val MAX_MEMBER_COUNT = 8L

        fun validateName(name: String) {
            require(name.isNotBlank()) { "그룹명은 비어 있을 수 없습니다." }
            require(name.length <= 255) { "그룹명은 255자를 초과할 수 없습니다." }
        }
    }
}
