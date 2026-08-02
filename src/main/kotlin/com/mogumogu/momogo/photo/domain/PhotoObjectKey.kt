package com.mogumogu.momogo.photo.domain

import java.time.DateTimeException
import java.time.LocalDate
import java.util.UUID

class PhotoObjectKey private constructor(
    val value: String,
    val phase: String,
    val userId: Long,
    val uploadDate: LocalDate,
    val objectId: UUID,
    val extension: String,
) {

    fun belongsTo(
        phase: String,
        userId: Long,
    ): Boolean = this.phase == phase && this.userId == userId

    override fun equals(other: Any?): Boolean = this === other || (other is PhotoObjectKey && value == other.value)

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value

    companion object {
        private val OBJECT_KEY_PATTERN = Regex(
            "^([a-z]+)/users/([1-9]\\d*)/(\\d{4}-\\d{2}-\\d{2})/" +
                "([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\\." +
                "([a-z0-9][a-z0-9-]{0,126})$",
        )

        fun generate(
            phase: String,
            userId: Long,
            uploadDate: LocalDate,
            objectId: UUID,
            contentType: PhotoContentType,
        ): PhotoObjectKey {
            require(userId > 0) { "사용자 ID는 0보다 커야 합니다." }

            return PhotoObjectKey(
                value = "$phase/users/$userId/$uploadDate/$objectId.${contentType.extension}",
                phase = phase,
                userId = userId,
                uploadDate = uploadDate,
                objectId = objectId,
                extension = contentType.extension,
            )
        }

        fun parse(value: String): PhotoObjectKey {
            val match = requireNotNull(OBJECT_KEY_PATTERN.matchEntire(value)) {
                "올바른 사진 오브젝트 키 형식이 아닙니다."
            }
            val (phaseValue, userIdValue, uploadDateValue, objectIdValue, extension) = match.destructured

            val userId = requireNotNull(userIdValue.toLongOrNull()) {
                "올바른 사용자 ID가 아닙니다."
            }
            val uploadDate = try {
                LocalDate.parse(uploadDateValue)
            } catch (_: DateTimeException) {
                throw IllegalArgumentException("올바른 업로드 날짜가 아닙니다.")
            }
            val objectId = UUID.fromString(objectIdValue)

            return PhotoObjectKey(
                value = value,
                phase = phaseValue,
                userId = userId,
                uploadDate = uploadDate,
                objectId = objectId,
                extension = extension,
            )
        }
    }
}
