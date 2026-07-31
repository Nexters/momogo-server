package com.mogumogu.momogo.group.infra

import com.mogumogu.momogo.group.domain.Group
import com.mogumogu.momogo.group.domain.InviteCode
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface GroupRepository : JpaRepository<Group, Long> {
    @Query("SELECT g FROM Group g WHERE g._inviteCode = :inviteCode")
    fun findByInviteCode(
        @Param("inviteCode")
        inviteCode: InviteCode,
    ): Group?
}
