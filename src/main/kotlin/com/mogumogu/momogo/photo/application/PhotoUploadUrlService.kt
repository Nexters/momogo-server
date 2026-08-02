package com.mogumogu.momogo.photo.application

import com.mogumogu.momogo.global.error.ApiException
import com.mogumogu.momogo.global.error.ErrorCode
import com.mogumogu.momogo.photo.domain.PhotoContentType
import com.mogumogu.momogo.photo.domain.PhotoObjectKey
import com.mogumogu.momogo.user.infra.UserRepository
import org.springframework.core.env.Environment
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
    environment: Environment,
    private val clock: Clock,
) {

    private val phase = environment.activeProfiles.first()

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
            phase = phase,
            userId = userId,
            uploadDate = LocalDate.now(clock),
            objectId = UUID.randomUUID(),
            contentType = contentType,
        )
        val generated = uploadUrlGenerator.generate(
            objectKey = objectKey,
            contentTypeValue = contentTypeValue,
        )

        return PhotoUploadUrlResult(
            uploadUrl = generated.uploadUrl,
            objectKey = objectKey.value,
            expiresAt = generated.expiresAt,
        )
    }
}

data class PhotoUploadUrlResult(
    val uploadUrl: String,
    val objectKey: String,
    val expiresAt: LocalDateTime,
)
