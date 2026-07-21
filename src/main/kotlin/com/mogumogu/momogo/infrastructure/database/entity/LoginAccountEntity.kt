package com.mogumogu.momogo.infrastructure.database.entity

import com.mogumogu.momogo.domain.user.LoginAccount
import com.mogumogu.momogo.domain.user.LoginProvider
import com.mogumogu.momogo.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "login_account")
class LoginAccountEntity(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "user_id", nullable = false)
    var user: UserEntity,
    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "provider", nullable = false, length = 20)
    var provider: LoginProvider,
    @field:Column(name = "provider_id", nullable = false, length = 255)
    var providerId: String,
) : BaseEntity() {

    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    @field:Column(name = "id", nullable = false, updatable = false)
    var id: Long? = null
        protected set

    fun toDomain(): LoginAccount =
        LoginAccount(
            id = id,
            userId = checkNotNull(user.id) {
                "로그인 계정에 연결된 사용자 ID가 없습니다."
            },
            provider = provider,
            providerId = providerId,
        )

    companion object {
        fun fromDomain(
            loginAccount: LoginAccount,
            user: UserEntity,
        ): LoginAccountEntity =
            LoginAccountEntity(
                user = user,
                provider = loginAccount.provider,
                providerId = loginAccount.providerId,
            ).apply {
                id = loginAccount.id
            }
    }
}
