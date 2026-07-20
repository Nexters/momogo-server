package com.mogumogu.momogo.infrastructure.database.entity

import com.mogumogu.momogo.domain.user.User
import com.mogumogu.momogo.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "\"user\"")
class UserEntity(
    @field:Column(name = "nickname", nullable = false, length = 12)
    var nickname: String,
) : BaseEntity() {

    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    @field:Column(name = "id", nullable = false, updatable = false)
    var id: Long? = null
        protected set

    fun toDomain(): User =
        User(
            id = id,
            nickname = nickname,
        )

    companion object {
        fun fromDomain(user: User): UserEntity =
            UserEntity(nickname = user.nickname)
    }
}
