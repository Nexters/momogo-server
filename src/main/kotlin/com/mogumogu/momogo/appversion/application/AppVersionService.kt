package com.mogumogu.momogo.appversion.application

import com.mogumogu.momogo.appversion.domain.AppPlatform
import com.mogumogu.momogo.appversion.domain.SemanticVersion
import com.mogumogu.momogo.global.error.ApiException
import com.mogumogu.momogo.global.error.ErrorCode
import org.springframework.stereotype.Service

@Service
class AppVersionService(
    properties: AppVersionProperties,
) {

    private val policies = mapOf(
        AppPlatform.IOS to properties.ios.toPolicy(AppPlatform.IOS),
        AppPlatform.ANDROID to properties.android.toPolicy(AppPlatform.ANDROID),
    )

    fun check(
        platformValue: String,
        appVersionValue: String,
    ): AppVersionCheckResult {
        val platform = AppPlatform.from(platformValue)
            ?: throw ApiException.BadRequest(ErrorCode.INVALID_PLATFORM)
        val appVersion = SemanticVersion.parseOrNull(appVersionValue)
            ?: throw ApiException.BadRequest(ErrorCode.INVALID_REQUEST)
        val policy = policies.getValue(platform)

        return AppVersionCheckResult(
            latestVersion = policy.latestVersionValue,
            minSupportedVersion = policy.minSupportedVersionValue,
            forceUpdate = appVersion < policy.minSupportedVersion,
            updateUrl = policy.updateUrl,
        )
    }

    private fun PlatformVersionProperties.toPolicy(platform: AppPlatform): AppVersionPolicy {
        val latestVersion = requireNotNull(SemanticVersion.parseOrNull(latestVersion)) {
            "${platform.name} latest-version은 유효한 SemVer 형식이어야 합니다."
        }
        val minSupportedVersion = requireNotNull(SemanticVersion.parseOrNull(minSupportedVersion)) {
            "${platform.name} min-supported-version은 유효한 SemVer 형식이어야 합니다."
        }

        require(minSupportedVersion <= latestVersion) {
            "${platform.name} min-supported-version은 latest-version보다 높을 수 없습니다."
        }
        require(updateUrl.isAbsolute && updateUrl.scheme.equals("https", ignoreCase = true)) {
            "${platform.name} update-url은 절대 HTTPS URL이어야 합니다."
        }

        return AppVersionPolicy(
            latestVersionValue = this.latestVersion,
            minSupportedVersionValue = this.minSupportedVersion,
            minSupportedVersion = minSupportedVersion,
            updateUrl = updateUrl.toString(),
        )
    }

    private data class AppVersionPolicy(
        val latestVersionValue: String,
        val minSupportedVersionValue: String,
        val minSupportedVersion: SemanticVersion,
        val updateUrl: String,
    )
}

data class AppVersionCheckResult(
    val latestVersion: String,
    val minSupportedVersion: String,
    val forceUpdate: Boolean,
    val updateUrl: String,
)
