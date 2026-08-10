package com.mogumogu.momogo.photo.presentation

import com.mogumogu.momogo.global.config.OpenApiConfiguration
import com.mogumogu.momogo.global.error.ApiException
import com.mogumogu.momogo.global.error.ErrorCode
import com.mogumogu.momogo.global.openapi.ApiErrors
import com.mogumogu.momogo.global.openapi.ApiExamples
import com.mogumogu.momogo.global.openapi.OpenApiExample
import com.mogumogu.momogo.global.security.RequestUserId
import com.mogumogu.momogo.photo.application.CreatePhotoCommand
import com.mogumogu.momogo.photo.application.PhotoQueryService
import com.mogumogu.momogo.photo.application.PhotoService
import com.mogumogu.momogo.photo.application.PhotoUploadUrlService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime

@Tag(
    name = "사진",
    description = "사진 조회와 업로드 API",
)
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
@RestController
@RequestMapping("/api/v1/photos")
class PhotoController(
    private val photoUploadUrlService: PhotoUploadUrlService,
    private val photoService: PhotoService,
    private val photoQueryService: PhotoQueryService,
) {

    @Operation(
        summary = "날짜별 내 사진 조회",
        description = "현재 사용자가 지정한 날짜(Asia/Seoul)에 올린 사진을 최신순으로 조회합니다. " +
            "날짜를 생략하면 오늘을 조회하며, 사진이 없으면 빈 목록을 반환합니다.",
    )
    @ApiExamples(success = OpenApiExample.MY_PHOTOS_RESPONSE)
    @ApiErrors(badRequest = [ErrorCode.INVALID_REQUEST])
    @GetMapping("/me")
    fun getMyPhotos(
        @RequestUserId
        userId: Long,
        @Parameter(
            description = "사진을 조회할 날짜(Asia/Seoul)",
            example = "2026-08-03",
            required = false,
            schema = Schema(type = "string", format = "date"),
        )
        @RequestParam(name = "date", required = false)
        dateValue: String?,
    ): MyPhotosResponse {
        val date = dateValue?.let {
            try {
                LocalDate.parse(it)
            } catch (_: DateTimeException) {
                throw ApiException.BadRequest(ErrorCode.INVALID_REQUEST)
            }
        }
        val result = photoQueryService.getMyPhotos(userId, date)

        return MyPhotosResponse(
            date = result.date,
            photos = result.photos.map { photo ->
                PhotoResponse(
                    photoId = photo.photoId,
                    downloadUrl = photo.downloadUrl,
                    contentType = photo.contentType,
                    createdAt = photo.createdAt,
                    expiresAt = photo.expiresAt,
                )
            },
        )
    }

    @Operation(
        summary = "사진 등록",
        description = "R2 업로드가 끝난 사진을 선택한 그룹에 등록합니다. " +
            "한 사용자는 그룹별로 하루에 사진 한 장만 등록할 수 있으며, " +
            "선택한 그룹 중 하나라도 오늘 업로드를 사용했으면 요청 전체를 거절합니다. " +
            "그룹에서 사진을 내리면 그 그룹의 오늘 업로드는 다시 사용할 수 있습니다. " +
            "오브젝트 키는 한 번만 등록할 수 있으므로, 재등록에는 새로 업로드한 오브젝트 키가 필요합니다.",
    )
    @ApiExamples(
        request = OpenApiExample.PHOTO_CREATE_REQUEST,
        success = OpenApiExample.PHOTO_CREATE_RESPONSE,
    )
    @ApiErrors(
        badRequest = [
            ErrorCode.INVALID_REQUEST,
            ErrorCode.INVALID_OBJECT_KEY,
        ],
        forbidden = [ErrorCode.NOT_GROUP_MEMBER],
        notFound = [ErrorCode.USER_NOT_FOUND],
        conflict = [
            ErrorCode.PHOTO_ALREADY_REGISTERED,
            ErrorCode.DAILY_GROUP_UPLOAD_LIMIT_EXCEEDED,
        ],
        unprocessableEntity = [ErrorCode.OBJECT_NOT_UPLOADED],
    )
    @PostMapping
    fun create(
        @RequestUserId
        userId: Long,
        @Valid
        @RequestBody
        request: PhotoCreateRequest,
    ): PhotoCreateResponse {
        val result = photoService.create(
            CreatePhotoCommand(
                userId = userId,
                objectKey = request.objectKey,
                groupIds = request.groupIds,
            ),
        )

        return PhotoCreateResponse(
            photoId = result.photoId,
            objectKey = result.objectKey,
            createdAt = result.createdAt,
        )
    }

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
            contentType = result.contentType,
            expiresAt = result.expiresAt,
        )
    }
}

@Schema(description = "날짜별 내 사진 조회 응답")
data class MyPhotosResponse(
    @field:Schema(
        description = "사진을 조회한 날짜(Asia/Seoul)",
        example = "2026-08-03",
        type = "string",
        format = "date",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val date: LocalDate,

    @field:Schema(
        description = "해당 날짜에 올린 사진 목록. 최신순으로 정렬되며 사진이 없으면 빈 목록",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val photos: List<PhotoResponse>,
)

@Schema(description = "다운로드 가능한 사진 정보")
data class PhotoResponse(
    @field:Schema(
        description = "사진 ID",
        example = "501",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val photoId: Long,

    @field:Schema(
        description = "이미지 바이너리에 바로 접근할 수 있는 R2 presigned GET URL",
        format = "uri",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val downloadUrl: String,

    @field:Schema(
        description = "이미지 MIME 타입",
        example = "image/webp",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val contentType: String,

    @field:Schema(
        description = "사진이 등록된 시각(Asia/Seoul)",
        example = "2026-08-03T14:30:00.123456",
        type = "string",
        format = "date-time",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val createdAt: LocalDateTime,

    @field:Schema(
        description = "다운로드 URL 만료 시각(Asia/Seoul)",
        example = "2026-08-03T14:45:00.123456",
        type = "string",
        format = "date-time",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val expiresAt: LocalDateTime,
)

@Schema(description = "사진 등록 요청")
data class PhotoCreateRequest(
    @field:NotBlank(message = "objectKey는 비어 있을 수 없습니다.")
    @field:Size(max = 512, message = "objectKey는 512자를 초과할 수 없습니다.")
    @field:Schema(
        description = "업로드 URL 발급 응답으로 받은 R2 오브젝트 키",
        example = "dev/users/1/2026-08-03/9f8b3a1c-2d4e-4a6b-8c0d-123456789abc.webp",
        maxLength = 512,
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val objectKey: String,

    @field:NotEmpty(message = "groupIds는 하나 이상이어야 합니다.")
    @field:Size(max = 20, message = "groupIds는 20개를 초과할 수 없습니다.")
    @field:Schema(
        description = "사진을 등록할 그룹 ID 목록",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val groupIds: List<
        @NotNull(message = "groupId는 null일 수 없습니다.")
        @Positive(message = "groupId는 0보다 커야 합니다.")
        Long,
    >,
)

@Schema(description = "사진 등록 응답")
data class PhotoCreateResponse(
    @field:Schema(
        description = "등록된 사진 ID",
        example = "501",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val photoId: Long,

    @field:Schema(
        description = "등록된 R2 오브젝트 키",
        example = "dev/users/1/2026-08-03/9f8b3a1c-2d4e-4a6b-8c0d-123456789abc.webp",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val objectKey: String,

    @field:Schema(
        description = "사진이 등록된 시각(Asia/Seoul)",
        example = "2026-08-03T14:30:00.123456",
        type = "string",
        format = "date-time",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val createdAt: LocalDateTime,
)

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
        description = "PUT 요청의 Content-Type 헤더에 그대로 지정해야 하는 값. 서명에 사용된 값이므로 다른 값으로 업로드하면 실패한다.",
        example = "image/webp",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val contentType: String,

    @field:Schema(
        description = "업로드 URL 만료 시각(Asia/Seoul)",
        example = "2026-08-03T18:15:00",
        type = "string",
        format = "date-time",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val expiresAt: LocalDateTime,
)
