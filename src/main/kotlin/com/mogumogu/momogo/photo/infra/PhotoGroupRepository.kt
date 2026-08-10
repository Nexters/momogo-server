package com.mogumogu.momogo.photo.infra

import com.mogumogu.momogo.photo.domain.PhotoGroup
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface PhotoGroupRepository : JpaRepository<PhotoGroup, Long> {
    @Query(
        """
        SELECT pg
        FROM PhotoGroup pg
        WHERE pg._photo._id = :photoId
          AND pg._group._id = :groupId
          AND pg._deletedAt IS NULL
        """,
    )
    fun findActiveByPhotoIdAndGroupId(
        @Param("photoId")
        photoId: Long,
        @Param("groupId")
        groupId: Long,
    ): PhotoGroup?

    @Query(
        """
        SELECT new com.mogumogu.momogo.photo.infra.GroupMemberPhotoView(
            pg._id,
            photo._id,
            uploader._id,
            photo._objectKey,
            photo._contentType,
            pg._createdAt
        )
        FROM PhotoGroup pg
        JOIN pg._photo photo
        JOIN photo._uploader uploader
        WHERE pg._group._id = :groupId
          AND pg._deletedAt IS NULL
          AND pg._createdAt >= :startAt
          AND pg._createdAt < :endAt
          AND EXISTS (
              SELECT member._id
              FROM GroupMember member
              WHERE member._group = pg._group
                AND member._user = uploader
                AND member._deletedAt IS NULL
          )
        ORDER BY pg._createdAt DESC, pg._id DESC
        """,
    )
    fun findActiveMemberPhotosByGroupIdAndCreatedAtRange(
        @Param("groupId")
        groupId: Long,
        @Param("startAt")
        startAt: Instant,
        @Param("endAt")
        endAt: Instant,
    ): List<GroupMemberPhotoView>

    @Query(
        """
        SELECT new com.mogumogu.momogo.photo.infra.TodayPhotoUploaderCount(
            pg._group._id,
            COUNT(DISTINCT uploader._id)
        )
        FROM PhotoGroup pg
        JOIN pg._photo photo
        JOIN photo._uploader uploader
        WHERE pg._group._id IN :groupIds
          AND pg._deletedAt IS NULL
          AND pg._createdAt >= :startAt
          AND pg._createdAt < :endAt
          AND EXISTS (
              SELECT member._id
              FROM GroupMember member
              WHERE member._group = pg._group
                AND member._user = uploader
                AND member._deletedAt IS NULL
          )
        GROUP BY pg._group._id
        """,
    )
    fun countPhotoUploadersByGroupIdsAndCreatedAtRange(
        @Param("groupIds")
        groupIds: List<Long>,
        @Param("startAt")
        startAt: Instant,
        @Param("endAt")
        endAt: Instant,
    ): List<TodayPhotoUploaderCount>

    @Query(
        """
        SELECT new com.mogumogu.momogo.photo.infra.LatestGroupUpload(
            pg._group._id,
            MAX(pg._createdAt)
        )
        FROM PhotoGroup pg
        JOIN pg._photo photo
        LEFT JOIN photo._uploader uploader
        WHERE pg._group._id IN :groupIds
          AND pg._deletedAt IS NULL
          AND (uploader._id IS NULL OR uploader._id <> :userId)
        GROUP BY pg._group._id
        """,
    )
    fun findLatestUploadsByGroupIdsExcludingUserId(
        @Param("groupIds")
        groupIds: List<Long>,
        @Param("userId")
        userId: Long,
    ): List<LatestGroupUpload>

    @Query(
        """
        SELECT CASE WHEN COUNT(pg) > 0 THEN true ELSE false END
        FROM PhotoGroup pg
        JOIN pg._photo photo
        JOIN photo._uploader uploader
        WHERE uploader._id = :userId
          AND pg._group._id IN :groupIds
          AND pg._deletedAt IS NULL
          AND pg._createdAt >= :startAt
          AND pg._createdAt < :endAt
        """,
    )
    fun existsUploadByUserIdAndGroupIdsAndCreatedAtRange(
        @Param("userId")
        userId: Long,
        @Param("groupIds")
        groupIds: List<Long>,
        @Param("startAt")
        startAt: Instant,
        @Param("endAt")
        endAt: Instant,
    ): Boolean
}

data class TodayPhotoUploaderCount(
    val groupId: Long,
    val uploaderCount: Long,
)

data class LatestGroupUpload(
    val groupId: Long,
    val latestUploadAt: Instant,
)

data class GroupMemberPhotoView(
    val photoGroupId: Long,
    val photoId: Long,
    val uploaderId: Long,
    val objectKey: String,
    val contentType: String,
    val createdAt: Instant,
)
