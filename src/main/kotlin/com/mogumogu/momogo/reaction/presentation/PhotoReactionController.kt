package com.mogumogu.momogo.reaction.presentation

import com.mogumogu.momogo.global.config.OpenApiConfiguration
import com.mogumogu.momogo.global.error.ErrorCode
import com.mogumogu.momogo.global.openapi.ApiErrors
import com.mogumogu.momogo.global.openapi.ApiExamples
import com.mogumogu.momogo.global.openapi.OpenApiExample
import com.mogumogu.momogo.global.security.RequestUserId
import com.mogumogu.momogo.reaction.application.CreatePhotoReactionCommand
import com.mogumogu.momogo.reaction.application.PhotoReactionService
import com.mogumogu.momogo.reaction.domain.Emoji
import com.mogumogu.momogo.reaction.domain.ReactionComment
import com.mogumogu.momogo.reaction.domain.ReactionConcept
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
import java.time.LocalDateTime

@Tag(
    name = "리액션",
    description = "사진 리액션 API",
)
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
@RestController
@RequestMapping("/api/v1/photos")
class PhotoReactionController(
    private val photoReactionService: PhotoReactionService,
) {

    @Operation(
        summary = "사진 리액션 등록",
        description = "그룹에 올라온 사진에 이모지와 문구로 리액션을 남깁니다. " +
            "문구는 `GET /init/comments`로 받은 목록에서 고른 값을 그대로 전달합니다. " +
            "같은 사진에 여러 번 리액션할 수 있습니다.",
    )
    @ApiExamples(
        request = OpenApiExample.PHOTO_REACTION_CREATE_REQUEST,
        success = OpenApiExample.PHOTO_REACTION_CREATE_RESPONSE,
    )
    @ApiErrors(
        badRequest = [ErrorCode.INVALID_REQUEST],
        forbidden = [ErrorCode.NOT_GROUP_MEMBER],
        notFound = [
            ErrorCode.PHOTO_NOT_FOUND,
            ErrorCode.USER_NOT_FOUND,
        ],
    )
    @PostMapping("/{photoId}/reactions")
    fun create(
        @RequestUserId
        userId: Long,
        @Parameter(example = "501")
        @PathVariable
        photoId: Long,
        @Valid
        @RequestBody
        request: PhotoReactionCreateRequest,
    ): PhotoReactionCreateResponse {
        val result = photoReactionService.create(
            CreatePhotoReactionCommand(
                userId = userId,
                photoId = photoId,
                groupId = request.groupId,
                concept = request.concept,
                emoji = request.emoji,
                comment = request.comment,
            ),
        )

        return PhotoReactionCreateResponse(
            reactionId = result.reactionId,
            photoId = result.photoId,
            groupId = result.groupId,
            concept = result.concept,
            emoji = result.emoji,
            comment = result.comment,
            createdAt = result.createdAt,
        )
    }
}

@Schema(description = "사진 리액션 등록 요청")
data class PhotoReactionCreateRequest(
    @field:Positive(message = "groupId는 0보다 커야 합니다.")
    @field:Schema(
        description = "사진이 올라온 그룹 ID. 같은 사진이라도 그룹마다 리액션이 따로 쌓인다.",
        example = "10",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val groupId: Long,

    @field:Schema(
        description = "리액션 컨셉",
        example = "YOUNG_CREATOR_CREW",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val concept: ReactionConcept,

    @field:Schema(
        description = "리액션 이모지",
        example = "DELICIOUS",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val emoji: Emoji,

    @field:NotBlank(message = "comment는 비어 있을 수 없습니다.")
    @field:Size(
        max = ReactionComment.CONTENT_MAX_LENGTH,
        message = "comment는 ${ReactionComment.CONTENT_MAX_LENGTH}자를 초과할 수 없습니다.",
    )
    @field:Schema(
        description = "리액션 문구",
        example = "야르~",
        maxLength = ReactionComment.CONTENT_MAX_LENGTH,
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val comment: String,
)

@Schema(description = "사진 리액션 등록 응답")
data class PhotoReactionCreateResponse(
    @field:Schema(
        description = "등록된 리액션 ID",
        example = "901",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val reactionId: Long,

    @field:Schema(
        description = "리액션을 남긴 사진 ID",
        example = "501",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val photoId: Long,

    @field:Schema(
        description = "리액션이 쌓인 그룹 ID",
        example = "10",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val groupId: Long,

    @field:Schema(
        description = "리액션 컨셉",
        example = "YOUNG_CREATOR_CREW",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val concept: ReactionConcept,

    @field:Schema(
        description = "리액션 이모지",
        example = "DELICIOUS",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val emoji: Emoji,

    @field:Schema(
        description = "리액션 문구",
        example = "야르~",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val comment: String,

    @field:Schema(
        description = "리액션이 등록된 시각(Asia/Seoul)",
        example = "2026-08-08T14:30:00.123456",
        type = "string",
        format = "date-time",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val createdAt: LocalDateTime,
)
