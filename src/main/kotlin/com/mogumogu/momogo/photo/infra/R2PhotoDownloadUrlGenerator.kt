package com.mogumogu.momogo.photo.infra

import com.mogumogu.momogo.global.storage.r2.R2Properties
import com.mogumogu.momogo.photo.application.GeneratedPhotoDownloadUrl
import com.mogumogu.momogo.photo.application.PhotoDownloadUrlGenerator
import com.mogumogu.momogo.photo.domain.PhotoObjectKey
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime

@Component
class R2PhotoDownloadUrlGenerator(
    private val presigner: S3Presigner,
    private val properties: R2Properties,
    private val clock: Clock,
) : PhotoDownloadUrlGenerator {

    override fun generate(objectKey: PhotoObjectKey): GeneratedPhotoDownloadUrl {
        val presignedRequest = presigner.presignGetObject(
            GetObjectPresignRequest
                .builder()
                .signatureDuration(DOWNLOAD_URL_DURATION)
                .getObjectRequest(
                    GetObjectRequest
                        .builder()
                        .bucket(properties.bucket)
                        .key(objectKey.value)
                        .build(),
                ).build(),
        )

        return GeneratedPhotoDownloadUrl(
            downloadUrl = presignedRequest.url().toString(),
            expiresAt = LocalDateTime.ofInstant(presignedRequest.expiration(), clock.zone),
        )
    }

    private companion object {
        val DOWNLOAD_URL_DURATION: Duration = Duration.ofMinutes(15)
    }
}
