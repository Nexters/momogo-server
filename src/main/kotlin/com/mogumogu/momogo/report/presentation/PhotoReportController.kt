package com.mogumogu.momogo.report.presentation

import com.mogumogu.momogo.global.config.OpenApiConfiguration
import com.mogumogu.momogo.global.error.ErrorCode
import com.mogumogu.momogo.global.openapi.ApiErrors
import com.mogumogu.momogo.global.openapi.ApiExamples
import com.mogumogu.momogo.global.openapi.OpenApiExample
import com.mogumogu.momogo.global.security.RequestUserId
import com.mogumogu.momogo.report.application.PhotoReportCommand
import com.mogumogu.momogo.report.application.PhotoReportService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(
    name = "신고",
    description = "사진 신고 API",
)
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
@RestController
@RequestMapping("/api/v1/groups/{groupId}/photos")
class PhotoReportController(
    private val photoReportService: PhotoReportService,
) {

    @Operation(
        summary = "사진 신고",
        description = "현재 사용자가 참여 중인 그룹의 활성 사진을 사유와 함께 신고합니다. " +
            "접수된 신고는 운영진의 Discord 채널로 전달됩니다.",
    )
    @ApiExamples(
        request = OpenApiExample.PHOTO_REPORT_REQUEST,
        success = OpenApiExample.EMPTY_OBJECT_RESPONSE,
    )
    @ApiErrors(
        badRequest = [ErrorCode.INVALID_REQUEST],
        forbidden = [ErrorCode.NOT_GROUP_MEMBER],
        notFound = [ErrorCode.PHOTO_NOT_FOUND],
        internalServerError = [ErrorCode.PHOTO_REPORT_NOTIFICATION_FAILED],
    )
    @PostMapping("/{photoId}/reports")
    fun report(
        @RequestUserId
        userId: Long,
        @Parameter(
            description = "신고할 사진이 올라온 그룹 ID",
            example = "10",
        )
        @Positive(message = "groupId는 0보다 커야 합니다.")
        @PathVariable
        groupId: Long,
        @Parameter(
            description = "신고할 사진 ID",
            example = "501",
        )
        @Positive(message = "photoId는 0보다 커야 합니다.")
        @PathVariable
        photoId: Long,
        @Valid
        @RequestBody
        request: PhotoReportRequest,
    ): Map<String, Any> {
        photoReportService.report(
            PhotoReportCommand(
                reporterId = userId,
                groupId = groupId,
                photoId = photoId,
                reason = request.reason,
            ),
        )
        return emptyMap()
    }
}

@Schema(description = "사진 신고 요청")
data class PhotoReportRequest(
    @field:NotBlank(message = "reason은 비어 있을 수 없습니다.")
    @field:Size(
        max = PhotoReportCommand.REASON_MAX_LENGTH,
        message = "reason은 ${PhotoReportCommand.REASON_MAX_LENGTH}자를 초과할 수 없습니다.",
    )
    @field:Schema(
        description = "사진을 신고하는 이유",
        example = "부적절한 사진이 포함되어 있습니다.",
        maxLength = PhotoReportCommand.REASON_MAX_LENGTH,
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val reason: String,
)
