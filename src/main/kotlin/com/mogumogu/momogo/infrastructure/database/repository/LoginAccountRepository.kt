package com.mogumogu.momogo.infrastructure.database.repository

import com.mogumogu.momogo.infrastructure.database.entity.LoginAccountEntity
import org.springframework.data.jpa.repository.JpaRepository

interface LoginAccountRepository : JpaRepository<LoginAccountEntity, Long> {
    fun findAllByUserId(userId: Long): List<LoginAccountEntity>
}
