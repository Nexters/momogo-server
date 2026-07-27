package com.mogumogu.momogo.global.entity

import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.MappedSuperclass
import org.hibernate.annotations.ColumnDefault
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseEntity(
    @field:CreatedDate
    @field:ColumnDefault("CURRENT_TIMESTAMP")
    @field:Column(name = "created_at", nullable = false, updatable = false)
    private var _createdAt: Instant? = null,

    @field:LastModifiedDate
    @field:ColumnDefault("CURRENT_TIMESTAMP")
    @field:Column(name = "updated_at", nullable = false)
    private var _updatedAt: Instant? = null,
) {

    val createdAt: Instant
        get() = checkNotNull(_createdAt) { "생성 시각이 아직 설정되지 않았습니다." }

    val updatedAt: Instant
        get() = checkNotNull(_updatedAt) { "수정 시각이 아직 설정되지 않았습니다." }
}
