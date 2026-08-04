package com.mogumogu.momogo.photo.infra

import com.mogumogu.momogo.global.storage.r2.R2Properties
import com.mogumogu.momogo.photo.application.PhotoObjectMetadata
import com.mogumogu.momogo.photo.application.PhotoObjectMetadataReader
import com.mogumogu.momogo.photo.domain.PhotoObjectKey
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception

@Component
class R2PhotoObjectMetadataReader(
    private val client: S3Client,
    private val properties: R2Properties,
) : PhotoObjectMetadataReader {

    override fun find(objectKey: PhotoObjectKey): PhotoObjectMetadata? {
        val response = try {
            client.headObject(
                HeadObjectRequest
                    .builder()
                    .bucket(properties.bucket)
                    .key(objectKey.value)
                    .build(),
            )
        } catch (exception: S3Exception) {
            if (exception.statusCode() == 404) {
                return null
            }
            throw exception
        }

        return PhotoObjectMetadata(
            sizeBytes = response.contentLength() ?: 0L,
            contentTypeValue = response.contentType(),
        )
    }
}
