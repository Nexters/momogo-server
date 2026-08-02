package com.mogumogu.momogo.photo.application

import com.mogumogu.momogo.photo.domain.PhotoContentType
import com.mogumogu.momogo.photo.domain.PhotoObjectKey
import java.time.LocalDateTime

interface PhotoUploadUrlGenerator {
    fun generate(
        objectKey: PhotoObjectKey,
        contentType: PhotoContentType,
    ): GeneratedPhotoUploadUrl
}

data class GeneratedPhotoUploadUrl(
    val uploadUrl: String,
    val expiresAt: LocalDateTime,
)
