package com.mogumogu.momogo.user.domain

import com.mogumogu.momogo.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "login_account")
class LoginAccount(
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    @field:Column(name = "id")
    private var _id: Long? = null,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(
        name = "user_id",
        nullable = false,
        updatable = false,
        foreignKey = ForeignKey(name = "fk_login_account_user"),
    )
    private var _user: User,

    @field:Enumerated(EnumType.STRING)
    @field:Column(
        name = "provider",
        nullable = false,
        updatable = false,
        length = 255,
        columnDefinition = "VARCHAR(255)",
    )
    private var _provider: LoginProvider,

    @field:Column(name = "provider_id", nullable = false, updatable = false, length = 255)
    private var _providerId: String,
) : BaseEntity() {

    val id: Long?
        get() = _id

    val user: User
        get() = _user

    val provider: LoginProvider
        get() = _provider

    val providerId: String
        get() = _providerId

    init {
        validateProviderId(_providerId)
    }

    private companion object {
        fun validateProviderId(providerId: String) {
            require(providerId.isNotBlank()) { "로그인 제공자 회원 ID는 비어 있을 수 없습니다." }
            require(providerId.length <= 255) { "로그인 제공자 회원 ID는 255자를 초과할 수 없습니다." }
        }
    }
}
