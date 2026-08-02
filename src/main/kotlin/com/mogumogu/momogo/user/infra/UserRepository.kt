package com.mogumogu.momogo.user.infra

import com.mogumogu.momogo.user.domain.User
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserRepository : JpaRepository<User, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT targetUser FROM User targetUser WHERE targetUser._id = :userId")
    fun findByIdForUpdate(
        @Param("userId")
        userId: Long,
    ): User?
}
