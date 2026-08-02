package com.mogumogu.momogo.appversion.presentation

import com.mogumogu.momogo.appversion.application.AppVersionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(
    name = "앱 초기화",
    description = "앱 최초 접근에 필요한 정보 조회 API",
)
@RestController
@RequestMapping("/init/versions")
class AppVersionController(
    private val appVersionService: AppVersionService,
) {

    @Operation(
        summary = "앱 버전 체크",
        description = "현재 앱 버전이 최소 지원 버전보다 낮은지 확인합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "앱 버전 체크 성공",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = AppVersionResponse::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "지원하지 않는 플랫폼이거나 앱 버전 형식이 올바르지 않음",
                content = [
                    Content(
                        mediaType = "application/problem+json",
                        schema = Schema(implementation = ProblemDetail::class),
                    ),
                ],
            ),
        ],
    )
    @GetMapping
    fun check(
        @Parameter(
            description = "앱 플랫폼",
            required = true,
            schema = Schema(allowableValues = ["IOS", "ANDROID"]),
            example = "IOS",
        )
        @RequestParam
        platform: String,
        @Parameter(
            description = "현재 앱 버전(MAJOR.MINOR.PATCH)",
            required = true,
            example = "1.2.0",
        )
        @RequestParam
        appVersion: String,
    ): AppVersionResponse {
        val result = appVersionService.check(
            platformValue = platform,
            appVersionValue = appVersion,
        )

        return AppVersionResponse(
            latestVersion = result.latestVersion,
            minSupportedVersion = result.minSupportedVersion,
            forceUpdate = result.forceUpdate,
            updateUrl = result.updateUrl,
        )
    }
}

@Schema(description = "앱 버전 체크 응답")
data class AppVersionResponse(
    @field:Schema(
        description = "플랫폼에 배포된 최신 버전",
        example = "1.3.0",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val latestVersion: String,

    @field:Schema(
        description = "서비스를 이용할 수 있는 최소 버전",
        example = "1.1.0",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val minSupportedVersion: String,

    @field:Schema(
        description = "강제 업데이트 필요 여부",
        example = "true",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val forceUpdate: Boolean,

    @field:Schema(
        description = "플랫폼별 앱 스토어 URL",
        example = "https://apps.apple.com/app/id000000000",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val updateUrl: String,
)
