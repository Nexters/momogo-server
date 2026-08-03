package com.mogumogu.momogo.photo.application

import com.mogumogu.momogo.global.config.ApplicationPhase
import com.mogumogu.momogo.global.error.ApiException
import com.mogumogu.momogo.global.error.ErrorCode
import com.mogumogu.momogo.photo.domain.PhotoContentType
import com.mogumogu.momogo.photo.domain.PhotoObjectKey
import com.mogumogu.momogo.user.infra.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Service
class PhotoUploadUrlService(
    private val userRepository: UserRepository,
    private val uploadUrlGenerator: PhotoUploadUrlGenerator,
    private val applicationPhase: ApplicationPhase,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun issue(
        userId: Long,
        contentTypeValue: String,
    ): PhotoUploadUrlResult {
        if (!userRepository.existsById(userId)) {
            throw ApiException.NotFound(ErrorCode.USER_NOT_FOUND)
        }

        val contentType = PhotoContentType.from(contentTypeValue)
            ?: throw ApiException.BadRequest(ErrorCode.INVALID_CONTENT_TYPE)
        val objectKey = PhotoObjectKey.generate(
            phase = applicationPhase.value,
            userId = userId,
            uploadDate = LocalDate.now(clock),
            objectId = UUID.randomUUID(),
            contentType = contentType,
        )
        val generated = uploadUrlGenerator.generate(
            objectKey = objectKey,
            contentType = contentType,
        )

        return PhotoUploadUrlResult(
            uploadUrl = generated.uploadUrl,
            objectKey = objectKey.value,
            contentType = contentType.value,
            expiresAt = generated.expiresAt,
        )
    }
}

data class PhotoUploadUrlResult(
    val uploadUrl: String,
    val objectKey: String,
    val contentType: String,
    val expiresAt: LocalDateTime,
)
