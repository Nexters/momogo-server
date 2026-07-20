package com.mogumogu.momogo.infrastructure.database.entity

import com.mogumogu.momogo.domain.user.LoginAccount
import com.mogumogu.momogo.domain.user.LoginProvider
import com.mogumogu.momogo.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "login_account")
class LoginAccountEntity(
    @field:Column(name = "user_id", nullable = false)
    var userId: Long,
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
            userId = userId,
            provider = provider,
            providerId = providerId,
        )

    companion object {
        fun fromDomain(loginAccount: LoginAccount): LoginAccountEntity =
            LoginAccountEntity(
                userId = loginAccount.userId,
                provider = loginAccount.provider,
                providerId = loginAccount.providerId,
            )
    }
}
