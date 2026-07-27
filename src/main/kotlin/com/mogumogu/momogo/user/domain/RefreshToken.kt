package com.mogumogu.momogo.user.domain

import com.mogumogu.momogo.global.entity.BaseEntity
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    name = "refresh_token",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_refresh_token_token_hash",
            columnNames = ["token_hash"],
        ),
    ],
)
class RefreshToken(
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    @field:Column(name = "id")
    private var _id: Long? = null,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(
        name = "user_id",
        nullable = false,
        updatable = false,
        foreignKey = ForeignKey(name = "fk_refresh_token_user"),
    )
    private var _user: User,

    @field:Column(
        name = "token_hash",
        nullable = false,
        updatable = false,
        length = 64,
        columnDefinition = "CHAR(64)",
    )
    private var _tokenHash: String,

    @field:Column(name = "expires_at", nullable = false, updatable = false)
    private var _expiresAt: Instant,

    @field:Column(name = "revoked_at")
    private var _revokedAt: Instant? = null,
) : BaseEntity() {

    val id: Long?
        get() = _id

    val user: User
        get() = _user

    val tokenHash: String
        get() = _tokenHash

    val expiresAt: Instant
        get() = _expiresAt

    val revokedAt: Instant?
        get() = _revokedAt

    init {
        validateTokenHash(_tokenHash)
    }

    fun revoke(at: Instant) {
        if (_revokedAt == null) {
            _revokedAt = at
        }
    }

    fun isActive(at: Instant): Boolean =
        _revokedAt == null && _expiresAt.isAfter(at)

    private companion object {
        private val SHA_256_PATTERN = Regex("^[0-9a-fA-F]{64}$")

        fun validateTokenHash(tokenHash: String) {
            require(SHA_256_PATTERN.matches(tokenHash)) {
                "리프레시 토큰 해시는 64자리 16진수여야 합니다."
            }
        }
    }
}
