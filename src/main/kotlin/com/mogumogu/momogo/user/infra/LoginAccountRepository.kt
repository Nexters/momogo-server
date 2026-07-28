package com.mogumogu.momogo.user.infra

import com.mogumogu.momogo.user.domain.LoginAccount
import com.mogumogu.momogo.user.domain.LoginProvider
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface LoginAccountRepository : JpaRepository<LoginAccount, Long> {

    @Query(
        """
        SELECT loginAccount
        FROM LoginAccount loginAccount
        WHERE loginAccount._provider = :provider
          AND loginAccount._providerId = :providerId
        """,
    )
    fun findByProviderAndProviderId(
        @Param("provider")
        provider: LoginProvider,
        @Param("providerId")
        providerId: String,
    ): LoginAccount?

    @Query(
        """
        SELECT CASE WHEN COUNT(loginAccount) > 0 THEN true ELSE false END
        FROM LoginAccount loginAccount
        WHERE loginAccount._provider = :provider
          AND loginAccount._providerId = :providerId
        """,
    )
    fun existsByProviderAndProviderId(
        @Param("provider")
        provider: LoginProvider,
        @Param("providerId")
        providerId: String,
    ): Boolean

    @Modifying(flushAutomatically = true)
    @Query(
        """
        DELETE FROM LoginAccount loginAccount
        WHERE loginAccount._user._id = :userId
        """,
    )
    fun deleteAllByUser_Id(
        @Param("userId")
        userId: Long
    ): Int
}
