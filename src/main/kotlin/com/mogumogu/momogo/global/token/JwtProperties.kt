package com.mogumogu.momogo.global.token

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * JWT 서명 비밀값은 `JWT_SECRET_BASE64` 환경변수로만 주입한다.
 *
 * 비밀값은 최소 32바이트의 난수를 표준 Base64로 인코딩한 문자열이어야 한다.
 */
@ConfigurationProperties(prefix = "momogo.security.jwt")
data class JwtProperties(
    val issuer: String,
    val secretBase64: String,
)
