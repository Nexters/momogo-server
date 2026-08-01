package com.mogumogu.momogo.group.domain

import com.mogumogu.momogo.global.entity.BaseEntity
import com.mogumogu.momogo.user.domain.User
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    name = "group_member",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_group_member_group_user",
            columnNames = ["group_id", "user_id"],
        ),
    ],
)
class GroupMember(
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    @field:Column(name = "id")
    private var _id: Long? = null,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(
        name = "group_id",
        nullable = false,
        updatable = false,
        foreignKey = ForeignKey(name = "fk_group_member_group"),
    )
    private var _group: Group,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(
        name = "user_id",
        nullable = false,
        updatable = false,
        foreignKey = ForeignKey(name = "fk_group_member_user"),
    )
    private var _user: User,

    @field:Column(name = "deleted_at")
    private var _deletedAt: Instant? = null,
) : BaseEntity() {

    val id: Long?
        get() = _id

    val group: Group
        get() = _group

    val user: User
        get() = _user

    val deletedAt: Instant?
        get() = _deletedAt

    fun leave(at: Instant) {
        if (_deletedAt == null) {
            _deletedAt = at
        }
    }

    fun ensureCanBeReplaced() {
        check(!isActive()) { "활성 멤버십은 새 멤버십으로 교체할 수 없습니다." }
    }

    fun isActive(): Boolean = _deletedAt == null
}
