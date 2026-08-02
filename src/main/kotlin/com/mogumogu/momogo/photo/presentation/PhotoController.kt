package com.mogumogu.momogo.photo.presentation

import com.mogumogu.momogo.global.config.OpenApiConfiguration
import com.mogumogu.momogo.global.error.ErrorCode
import com.mogumogu.momogo.global.openapi.ApiErrors
import com.mogumogu.momogo.global.openapi.ApiExamples
import com.mogumogu.momogo.global.openapi.OpenApiExample
import com.mogumogu.momogo.global.security.RequestUserId
import com.mogumogu.momogo.photo.application.PhotoUploadUrlService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@Tag(
    name = "사진",
    description = "사진 업로드 API",
)
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
@RestController
@RequestMapping("/api/v1/photos")
class PhotoController(
    private val photoUploadUrlService: PhotoUploadUrlService,
) {

    @Operation(
        summary = "사진 업로드 URL 발급",
        description = "현재 사용자가 이미지를 R2에 직접 업로드할 수 있는 15분 유효 PUT URL을 발급합니다.",
    )
    @ApiExamples(
        request = OpenApiExample.PHOTO_UPLOAD_URL_REQUEST,
        success = OpenApiExample.PHOTO_UPLOAD_URL_RESPONSE,
    )
    @ApiErrors(
        badRequest = [
            ErrorCode.INVALID_REQUEST,
            ErrorCode.INVALID_CONTENT_TYPE,
        ],
        notFound = [ErrorCode.USER_NOT_FOUND],
    )
    @PostMapping("/upload-urls")
    fun issueUploadUrl(
        @RequestUserId
        userId: Long,
        @Valid
        @RequestBody
        request: PhotoUploadUrlRequest,
    ): PhotoUploadUrlResponse {
        val result = photoUploadUrlService.issue(
            userId = userId,
            contentTypeValue = request.contentType,
        )

        return PhotoUploadUrlResponse(
            uploadUrl = result.uploadUrl,
            objectKey = result.objectKey,
            expiresAt = result.expiresAt,
        )
    }
}

@Schema(description = "사진 업로드 URL 발급 요청")
data class PhotoUploadUrlRequest(
    @field:NotBlank(message = "contentType은 비어 있을 수 없습니다.")
    @field:Schema(
        description = "업로드할 이미지의 MIME 타입",
        example = "image/webp",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val contentType: String,
)

@Schema(description = "사진 업로드 URL 발급 응답")
data class PhotoUploadUrlResponse(
    @field:Schema(
        description = "이미지 바이너리를 PUT으로 업로드할 R2 presigned URL",
        format = "uri",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val uploadUrl: String,

    @field:Schema(
        description = "사진 확정 요청에서 다시 전달할 R2 오브젝트 키",
        example = "dev/users/1/2026-08-03/9f8b3a1c-2d4e-4a6b-8c0d-123456789abc.webp",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val objectKey: String,

    @field:Schema(
        description = "업로드 URL 만료 시각(Asia/Seoul)",
        example = "2026-08-03T18:15:00",
        type = "string",
        format = "date-time",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val expiresAt: LocalDateTime,
)
