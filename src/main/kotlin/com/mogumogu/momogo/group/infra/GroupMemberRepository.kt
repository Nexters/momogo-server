package com.mogumogu.momogo.group.infra

import com.mogumogu.momogo.group.domain.GroupMember
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface GroupMemberRepository : JpaRepository<GroupMember, Long> {
    @Query(
        """
        SELECT gm
        FROM GroupMember gm
        WHERE gm._group._id = :groupId
          AND gm._user._id = :userId
        """,
    )
    fun findByGroupIdAndUserId(
        @Param("groupId")
        groupId: Long,
        @Param("userId")
        userId: Long,
    ): GroupMember?

    @Query(
        """
        SELECT COUNT(gm)
        FROM GroupMember gm
        WHERE gm._group._id = :groupId
          AND gm._deletedAt IS NULL
        """,
    )
    fun countActiveByGroupId(
        @Param("groupId")
        groupId: Long,
    ): Long
}
