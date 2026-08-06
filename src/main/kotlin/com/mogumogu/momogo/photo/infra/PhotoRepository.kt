package com.mogumogu.momogo.photo.infra

import com.mogumogu.momogo.photo.domain.Photo
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface PhotoRepository : JpaRepository<Photo, Long> {
    @Query(
        """
        SELECT new com.mogumogu.momogo.photo.infra.UploadedPhoto(
            photo._id,
            photo._objectKey,
            photo._contentType,
            photo._createdAt
        )
        FROM Photo photo
        WHERE photo._uploader._id = :userId
          AND photo._createdAt >= :startAt
          AND photo._createdAt < :endAt
        ORDER BY photo._createdAt DESC, photo._id DESC
        """,
    )
    fun findAllUploadedByUserIdAndCreatedAtRange(
        @Param("userId")
        userId: Long,
        @Param("startAt")
        startAt: Instant,
        @Param("endAt")
        endAt: Instant,
    ): List<UploadedPhoto>

    @Modifying(flushAutomatically = true)
    @Query(
        """
        UPDATE Photo photo
        SET photo._uploader = NULL
        WHERE photo._uploader._id = :userId
        """,
    )
    fun clearUploaderByUserId(
        @Param("userId")
        userId: Long,
    ): Int
}

data class UploadedPhoto(
    val photoId: Long,
    val objectKey: String,
    val contentType: String,
    val createdAt: Instant,
)
