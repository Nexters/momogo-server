package com.mogumogu.momogo.group.infra

import com.mogumogu.momogo.group.domain.Group
import com.mogumogu.momogo.group.domain.InviteCode
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface GroupRepository : JpaRepository<Group, Long> {
    @Query("SELECT g FROM Group g WHERE g._inviteCode = :inviteCode")
    fun findByInviteCode(
        @Param("inviteCode")
        inviteCode: InviteCode,
    ): Group?

    @Query(
        """
        SELECT g
        FROM Group g
        WHERE g._inviteCode = :inviteCode
          AND g._deletedAt IS NULL
        """,
    )
    fun findActiveByInviteCode(
        @Param("inviteCode")
        inviteCode: InviteCode,
    ): Group?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        SELECT g
        FROM Group g
        WHERE g._inviteCode = :inviteCode
          AND g._deletedAt IS NULL
        """,
    )
    fun findActiveByInviteCodeForUpdate(
        @Param("inviteCode")
        inviteCode: InviteCode,
    ): Group?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        SELECT g
        FROM Group g
        WHERE g._id = :groupId
          AND g._deletedAt IS NULL
        """,
    )
    fun findActiveByIdForUpdate(
        @Param("groupId")
        groupId: Long,
    ): Group?
}
