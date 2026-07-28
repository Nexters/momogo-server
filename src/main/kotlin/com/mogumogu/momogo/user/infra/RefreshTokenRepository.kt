package com.mogumogu.momogo.user.infra

import com.mogumogu.momogo.user.domain.RefreshToken
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {

    @Query(
        """
        SELECT refreshToken
        FROM RefreshToken refreshToken
        WHERE refreshToken._tokenHash = :tokenHash
        """,
    )
    fun findByTokenHash(
        @Param("tokenHash")
        tokenHash: String
    ): RefreshToken?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        SELECT refreshToken
        FROM RefreshToken refreshToken
        WHERE refreshToken._tokenHash = :tokenHash
        """,
    )
    fun findByTokenHashForUpdate(
        @Param("tokenHash")
        tokenHash: String
    ): RefreshToken?

    @Modifying(flushAutomatically = true)
    @Query(
        """
        DELETE FROM RefreshToken refreshToken
        WHERE refreshToken._user._id = :userId
        """,
    )
    fun deleteAllByUser_Id(
        @Param("userId")
        userId: Long
    ): Int
}
