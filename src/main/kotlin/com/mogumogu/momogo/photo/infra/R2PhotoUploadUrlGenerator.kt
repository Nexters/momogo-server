package com.mogumogu.momogo.photo.infra

import com.mogumogu.momogo.global.storage.r2.R2Properties
import com.mogumogu.momogo.photo.application.GeneratedPhotoUploadUrl
import com.mogumogu.momogo.photo.application.PhotoUploadUrlGenerator
import com.mogumogu.momogo.photo.domain.PhotoContentType
import com.mogumogu.momogo.photo.domain.PhotoObjectKey
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime

@Component
class R2PhotoUploadUrlGenerator(
    private val presigner: S3Presigner,
    private val properties: R2Properties,
    private val clock: Clock,
) : PhotoUploadUrlGenerator {

    override fun generate(
        objectKey: PhotoObjectKey,
        contentType: PhotoContentType,
    ): GeneratedPhotoUploadUrl {
        val presignedRequest = presigner.presignPutObject(
            PutObjectPresignRequest
                .builder()
                .signatureDuration(UPLOAD_URL_DURATION)
                .putObjectRequest(
                    PutObjectRequest
                        .builder()
                        .bucket(properties.bucket)
                        .key(objectKey.value)
                        .contentType(contentType.value)
                        .build(),
                ).build(),
        )

        return GeneratedPhotoUploadUrl(
            uploadUrl = presignedRequest.url().toString(),
            expiresAt = LocalDateTime.ofInstant(presignedRequest.expiration(), clock.zone),
        )
    }

    private companion object {
        val UPLOAD_URL_DURATION: Duration = Duration.ofMinutes(15)
    }
}
