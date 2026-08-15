package com.mogumogu.momogo.group.infra

import com.mogumogu.momogo.group.domain.GroupMember
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface GroupMemberRepository : JpaRepository<GroupMember, Long> {
    @Query(
        """
        SELECT gm
        FROM GroupMember gm
        JOIN FETCH gm._group g
        WHERE gm._user._id = :userId
          AND g._id = :groupId
          AND gm._deletedAt IS NULL
          AND g._deletedAt IS NULL
        """,
    )
    fun findJoinedWithActiveGroupByUserIdAndGroupId(
        @Param("userId")
        userId: Long,
        @Param("groupId")
        groupId: Long,
    ): GroupMember?

    @Query(
        """
        SELECT gm
        FROM GroupMember gm
        JOIN FETCH gm._group g
        WHERE gm._user._id = :userId
          AND gm._deletedAt IS NULL
          AND g._deletedAt IS NULL
        ORDER BY gm._id DESC
        """,
    )
    fun findAllJoinedWithActiveGroupByUserId(
        @Param("userId")
        userId: Long,
    ): List<GroupMember>

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        SELECT gm
        FROM GroupMember gm
        WHERE gm._group._id = :groupId
          AND gm._user._id = :userId
          AND gm._deletedAt IS NULL
        """,
    )
    fun findJoinedByGroupIdAndUserIdForUpdate(
        @Param("groupId")
        groupId: Long,
        @Param("userId")
        userId: Long,
    ): GroupMember?

    @Query(
        """
        SELECT gm._group._id
        FROM GroupMember gm
        WHERE gm._user._id = :userId
          AND gm._deletedAt IS NULL
        """,
    )
    fun findJoinedGroupIdsByUserId(
        @Param("userId")
        userId: Long,
    ): List<Long>

    @Query(
        """
        SELECT gm._group._id
        FROM GroupMember gm
        WHERE gm._user._id = :userId
          AND gm._group._id IN :groupIds
          AND gm._deletedAt IS NULL
        """,
    )
    fun findJoinedGroupIdsByUserIdAndGroupIds(
        @Param("userId")
        userId: Long,
        @Param("groupIds")
        groupIds: List<Long>,
    ): List<Long>

    @Query(
        """
        SELECT new com.mogumogu.momogo.group.infra.JoinedGroupMemberView(
            member._group._id,
            member._user._id,
            member._user._nickname
        )
        FROM GroupMember member
        WHERE member._group._id IN :groupIds
          AND member._deletedAt IS NULL
        ORDER BY
            member._group._id,
            CASE WHEN member._user._id = :requestUserId THEN 0 ELSE 1 END,
            member._user._nickname,
            member._user._id
        """,
    )
    fun findJoinedMemberViewsByGroupIds(
        @Param("groupIds")
        groupIds: List<Long>,
        @Param("requestUserId")
        requestUserId: Long,
    ): List<JoinedGroupMemberView>

    @Query(
        """
        SELECT COUNT(gm)
        FROM GroupMember gm
        WHERE gm._group._id = :groupId
          AND gm._deletedAt IS NULL
        """,
    )
    fun countJoinedByGroupId(
        @Param("groupId")
        groupId: Long,
    ): Long

    @Modifying(flushAutomatically = true)
    @Query(
        """
        DELETE FROM GroupMember gm
        WHERE gm._user._id = :userId
        """,
    )
    fun deleteAllByUserId(
        @Param("userId")
        userId: Long,
    ): Int
}

data class JoinedGroupMemberView(
    val groupId: Long,
    val userId: Long,
    val nickname: String,
)
