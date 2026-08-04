package com.mogumogu.momogo.photo.domain

import com.mogumogu.momogo.global.entity.BaseEntity
import com.mogumogu.momogo.group.domain.Group
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

@Entity
@Table(
    name = "photo_group",
    indexes = [
        Index(
            name = "idx_photo_group_group_created_at_photo",
            columnList = "group_id, created_at, photo_id",
        ),
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_photo_group_photo_group",
            columnNames = ["photo_id", "group_id"],
        ),
    ],
)
class PhotoGroup(
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    @field:Column(name = "id")
    private var _id: Long? = null,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(
        name = "photo_id",
        nullable = false,
        updatable = false,
        foreignKey = ForeignKey(name = "fk_photo_group_photo"),
    )
    private var _photo: Photo,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(
        name = "group_id",
        nullable = false,
        updatable = false,
        foreignKey = ForeignKey(name = "fk_photo_group_group"),
    )
    private var _group: Group,

    @field:Column(name = "deleted_at")
    private var _deletedAt: Instant? = null,
) : BaseEntity() {

    val id: Long?
        get() = _id

    val photo: Photo
        get() = _photo

    val group: Group
        get() = _group

    val deletedAt: Instant?
        get() = _deletedAt

    fun unlink(at: Instant) {
        if (_deletedAt == null) {
            _deletedAt = at
        }
    }

    fun isActive(): Boolean = _deletedAt == null
}
