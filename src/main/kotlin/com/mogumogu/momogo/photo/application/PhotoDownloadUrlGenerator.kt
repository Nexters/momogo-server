package com.mogumogu.momogo.photo.application

import com.mogumogu.momogo.photo.domain.PhotoObjectKey
import java.time.LocalDateTime

interface PhotoDownloadUrlGenerator {
    fun generate(objectKey: PhotoObjectKey): GeneratedPhotoDownloadUrl
}

data class GeneratedPhotoDownloadUrl(
    val downloadUrl: String,
    val expiresAt: LocalDateTime,
)
