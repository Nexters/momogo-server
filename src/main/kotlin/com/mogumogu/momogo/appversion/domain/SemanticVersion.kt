package com.mogumogu.momogo.appversion.domain

class SemanticVersion private constructor(
    private val major: Long,
    private val minor: Long,
    private val patch: Long,
) : Comparable<SemanticVersion> {

    override fun compareTo(other: SemanticVersion): Int =
        compareValuesBy(
            this,
            other,
            SemanticVersion::major,
            SemanticVersion::minor,
            SemanticVersion::patch,
        )

    companion object {
        private val SEMANTIC_VERSION_PATTERN =
            Regex("""^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$""")

        fun parseOrNull(value: String): SemanticVersion? {
            val match = SEMANTIC_VERSION_PATTERN.matchEntire(value) ?: return null

            val major = match.groupValues[1].toLongOrNull() ?: return null
            val minor = match.groupValues[2].toLongOrNull() ?: return null
            val patch = match.groupValues[3].toLongOrNull() ?: return null

            return SemanticVersion(major, minor, patch)
        }
    }
}
