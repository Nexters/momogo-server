package com.mogumogu.momogo.photo.infra

import com.mogumogu.momogo.photo.domain.Photo
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PhotoRepository : JpaRepository<Photo, Long> {
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
