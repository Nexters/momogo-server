package com.mogumogu.momogo.photo.domain

import java.util.Locale

class PhotoContentType private constructor(
    val value: String,
    val extension: String,
) {

    override fun equals(other: Any?): Boolean = this === other || (other is PhotoContentType && value == other.value)

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value

    companion object {
        const val MAX_VALUE_LENGTH = 133

        private val IMAGE_CONTENT_TYPE_PATTERN = Regex(
            "^image/([a-z0-9][a-z0-9!#$&^_.+-]{0,126})$",
        )
        private val UNSAFE_EXTENSION_CHARACTERS = Regex("[^a-z0-9]+")

        fun from(value: String): PhotoContentType? {
            val normalizedValue = value.lowercase(Locale.ROOT)
            val match = IMAGE_CONTENT_TYPE_PATTERN.matchEntire(normalizedValue) ?: return null
            val subtype = match.groupValues[1]

            return PhotoContentType(
                value = normalizedValue,
                extension = subtype.toSafeExtension(),
            )
        }

        private fun String.toSafeExtension(): String =
            when (this) {
                "jpeg", "pjpeg" -> "jpg"
                "svg+xml" -> "svg"
                "vnd.microsoft.icon", "x-icon" -> "ico"
                else ->
                    substringBefore('+')
                        .replace(UNSAFE_EXTENSION_CHARACTERS, "-")
                        .trim('-')
            }
    }
}
