package com.mogumogu.momogo.appversion.application

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI

@ConfigurationProperties(prefix = "momogo.app-version")
data class AppVersionProperties(
    val ios: PlatformVersionProperties,
    val android: PlatformVersionProperties,
)

data class PlatformVersionProperties(
    val latestVersion: String,
    val minSupportedVersion: String,
    val updateUrl: URI,
)
