package com.mogumogu.momogo.photo.application

import com.mogumogu.momogo.photo.domain.PhotoObjectKey

interface PhotoObjectMetadataReader {
    fun find(objectKey: PhotoObjectKey): PhotoObjectMetadata?
}

data class PhotoObjectMetadata(
    val sizeBytes: Long,
    val contentTypeValue: String?,
)
