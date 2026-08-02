package com.mogumogu.momogo.photo.infra

import com.mogumogu.momogo.photo.domain.PhotoGroup
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface PhotoGroupRepository : JpaRepository<PhotoGroup, Long> {
    @Query(
        """
        SELECT new com.mogumogu.momogo.photo.infra.TodayPhotoUploaderCount(
            pg._group._id,
            COUNT(DISTINCT photo._uploader._id)
        )
        FROM PhotoGroup pg
        JOIN pg._photo photo
        WHERE pg._group._id IN :groupIds
          AND pg._deletedAt IS NULL
          AND photo._createdAt >= :startAt
          AND photo._createdAt < :endAt
          AND EXISTS (
              SELECT member._id
              FROM GroupMember member
              WHERE member._group = pg._group
                AND member._user = photo._uploader
                AND member._deletedAt IS NULL
          )
        GROUP BY pg._group._id
        """,
    )
    fun countTodayPhotoUploadersByGroupIds(
        @Param("groupIds")
        groupIds: List<Long>,
        @Param("startAt")
        startAt: Instant,
        @Param("endAt")
        endAt: Instant,
    ): List<TodayPhotoUploaderCount>
}

data class TodayPhotoUploaderCount(
    val groupId: Long,
    val uploaderCount: Long,
)
