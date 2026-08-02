package com.mogumogu.momogo.global.storage.r2

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI

@ConfigurationProperties(prefix = "momogo.storage.r2")
class R2Properties(
    val endpoint: URI,
    val bucket: String,
    val accessKeyId: String,
    val secretAccessKey: String,
) {
    init {
        require(endpoint.scheme.equals("https", ignoreCase = true)) {
            "R2 endpoint는 HTTPS 주소여야 합니다."
        }
        require(bucket.isNotBlank()) { "R2 bucket은 비어 있을 수 없습니다." }
        require(accessKeyId.isNotBlank()) { "R2 access key ID는 비어 있을 수 없습니다." }
        require(secretAccessKey.isNotBlank()) { "R2 secret access key는 비어 있을 수 없습니다." }
    }
}
