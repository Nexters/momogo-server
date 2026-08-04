package com.mogumogu.momogo.photo.domain

import com.mogumogu.momogo.global.entity.BaseEntity
import com.mogumogu.momogo.user.domain.User
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

@Entity
@Table(
    name = "photo",
    indexes = [
        Index(
            name = "idx_photo_user_id",
            columnList = "user_id",
        ),
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_photo_object_key",
            columnNames = ["object_key"],
        ),
    ],
)
class Photo(
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    @field:Column(name = "id")
    private var _id: Long? = null,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = true)
    @field:JoinColumn(
        name = "user_id",
        nullable = true,
        foreignKey = ForeignKey(name = "fk_photo_user"),
    )
    private var _uploader: User?,

    @field:Column(name = "object_key", nullable = false, updatable = false, length = OBJECT_KEY_MAX_LENGTH)
    private var _objectKey: String,

    @field:Column(name = "size_bytes", nullable = false, updatable = false)
    private var _sizeBytes: Long,

    @field:Column(
        name = "content_type",
        nullable = false,
        updatable = false,
        length = PhotoContentType.MAX_VALUE_LENGTH,
    )
    private var _contentType: String,
) : BaseEntity() {

    val id: Long?
        get() = _id

    val uploader: User?
        get() = _uploader

    val objectKey: String
        get() = _objectKey

    val sizeBytes: Long
        get() = _sizeBytes

    val contentType: String
        get() = _contentType

    init {
        requireNotNull(_uploader) { "사진 업로더는 비어 있을 수 없습니다." }
        require(_objectKey.isNotBlank()) { "오브젝트 키는 비어 있을 수 없습니다." }
        require(_objectKey.length <= OBJECT_KEY_MAX_LENGTH) {
            "오브젝트 키는 ${OBJECT_KEY_MAX_LENGTH}자를 초과할 수 없습니다."
        }
        require(_sizeBytes > 0) { "사진 크기는 0보다 커야 합니다." }
        val contentType = requireNotNull(PhotoContentType.from(_contentType)) {
            "이미지 콘텐츠 타입이 올바르지 않습니다."
        }
        require(contentType.extension == _objectKey.substringAfterLast('.', "")) {
            "콘텐츠 타입이 오브젝트 키 확장자와 일치하지 않습니다."
        }
        _contentType = contentType.value
    }

    private companion object {
        const val OBJECT_KEY_MAX_LENGTH = 512
    }
}
