package com.mogumogu.momogo.global.logging

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HexFormat

/**
 * provider token이나 refresh token 해시처럼 로그에 원문을 남길 수 없는 값을 구분하기 위한 지문을 만든다.
 * 같은 값은 항상 같은 지문이 되므로 여러 요청에 걸쳐 같은 값이 쓰였는지 대조할 수 있다.
 */
object LogFingerprint {

    fun of(value: String): String =
        MessageDigest
            .getInstance(SHA_256)
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .let(HEX_FORMAT::formatHex)
            .take(FINGERPRINT_LENGTH)

    private const val SHA_256 = "SHA-256"
    private const val FINGERPRINT_LENGTH = 8
    private val HEX_FORMAT: HexFormat = HexFormat.of()
}
