package com.mogumogu.momogo.photo.presentation

import com.mogumogu.momogo.global.config.OpenApiConfiguration
import com.mogumogu.momogo.global.error.ErrorCode
import com.mogumogu.momogo.global.openapi.ApiErrors
import com.mogumogu.momogo.global.openapi.ApiExamples
import com.mogumogu.momogo.global.openapi.OpenApiExample
import com.mogumogu.momogo.global.security.RequestUserId
import com.mogumogu.momogo.photo.application.PhotoService
import com.mogumogu.momogo.photo.application.UnlinkPhotoCommand
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Positive
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(
    name = "사진",
    description = "사진 조회와 업로드 API",
)
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
@RestController
@RequestMapping("/api/v1/groups/{groupId}/photos")
class GroupPhotoController(
    private val photoService: PhotoService,
) {

    @Operation(
        summary = "그룹에서 사진 내리기",
        description = "현재 참여 중인 그룹에서 자신이 올린 활성 사진만 내립니다. " +
            "사진 자체와 다른 그룹의 연결은 유지되며, 오늘 올린 사진이면 " +
            "해당 그룹의 오늘 업로드 기회를 다시 사용할 수 있습니다.",
    )
    @ApiExamples(success = OpenApiExample.EMPTY_OBJECT_RESPONSE)
    @ApiErrors(
        badRequest = [ErrorCode.INVALID_REQUEST],
        forbidden = [
            ErrorCode.NOT_GROUP_MEMBER,
            ErrorCode.FORBIDDEN,
        ],
        notFound = [ErrorCode.PHOTO_NOT_FOUND],
    )
    @DeleteMapping("/{photoId}")
    fun unlink(
        @RequestUserId
        userId: Long,
        @Parameter(
            description = "사진을 내릴 그룹 ID",
            example = "10",
            schema = Schema(type = "integer", format = "int64", minimum = "1"),
        )
        @Positive(message = "groupId는 0보다 커야 합니다.")
        @PathVariable
        groupId: Long,
        @Parameter(
            description = "그룹에서 내릴 사진 ID",
            example = "501",
            schema = Schema(type = "integer", format = "int64", minimum = "1"),
        )
        @Positive(message = "photoId는 0보다 커야 합니다.")
        @PathVariable
        photoId: Long,
    ): Map<String, Any> {
        photoService.unlink(
            UnlinkPhotoCommand(
                userId = userId,
                groupId = groupId,
                photoId = photoId,
            ),
        )
        return emptyMap()
    }
}
