package com.mogumogu.momogo.photo.application

import com.mogumogu.momogo.global.error.ApiException
import com.mogumogu.momogo.global.error.ErrorCode
import com.mogumogu.momogo.global.time.DailyTimeRangeFactory
import com.mogumogu.momogo.photo.domain.PhotoObjectKey
import com.mogumogu.momogo.photo.infra.PhotoRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class PhotoQueryService(
    private val photoRepository: PhotoRepository,
    private val downloadUrlGenerator: PhotoDownloadUrlGenerator,
    private val dailyTimeRangeFactory: DailyTimeRangeFactory,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun getMyPhotos(
        userId: Long,
        date: LocalDate,
    ): MyPhotosResult {
        val timeRange = try {
            dailyTimeRangeFactory.create(date)
        } catch (_: DateTimeException) {
            throw ApiException.BadRequest(ErrorCode.INVALID_REQUEST)
        }
        val photos = photoRepository.findAllUploadedByUserIdAndCreatedAtRange(
            userId = userId,
            startAt = timeRange.startAt,
            endAt = timeRange.endAt,
        )

        return MyPhotosResult(
            date = date,
            photos = photos.map { photo ->
                val generated = downloadUrlGenerator.generate(PhotoObjectKey.parse(photo.objectKey))
                PhotoResult(
                    photoId = photo.photoId,
                    downloadUrl = generated.downloadUrl,
                    contentType = photo.contentType,
                    createdAt = LocalDateTime.ofInstant(photo.createdAt, clock.zone),
                    expiresAt = generated.expiresAt,
                )
            },
        )
    }
}

data class MyPhotosResult(
    val date: LocalDate,
    val photos: List<PhotoResult>,
)

data class PhotoResult(
    val photoId: Long,
    val downloadUrl: String,
    val contentType: String,
    val createdAt: LocalDateTime,
    val expiresAt: LocalDateTime,
)
