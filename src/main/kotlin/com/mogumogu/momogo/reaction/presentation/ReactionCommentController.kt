package com.mogumogu.momogo.reaction.presentation

import com.mogumogu.momogo.global.openapi.ApiExamples
import com.mogumogu.momogo.global.openapi.OpenApiExample
import com.mogumogu.momogo.reaction.application.ReactionCommentQueryService
import com.mogumogu.momogo.reaction.domain.Emoji
import com.mogumogu.momogo.reaction.domain.ReactionConcept
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@Tag(
    name = "앱 초기화",
    description = "앱 최초 접근에 필요한 정보 조회 API",
)
@RestController
@RequestMapping("/init/comments")
class ReactionCommentController(
    private val reactionCommentQueryService: ReactionCommentQueryService,
) {

    @Operation(
        summary = "리액션 문구 조회",
        description = "컨셉과 이모지별 리액션 문구를 모두 조회합니다. " +
            "클라이언트는 이 응답을 캐싱해 두고, 리액션을 발화할 때 해당 컨셉과 이모지의 문구 중 하나를 무작위로 고릅니다. " +
            "캐시를 갱신할지는 revision 값이 바뀌었는지로 판단합니다. " +
            "등록된 문구가 없으면 빈 목록을 반환합니다.",
    )
    @ApiExamples(success = OpenApiExample.INIT_COMMENTS_RESPONSE)
    @GetMapping
    fun getComments(): ReactionCommentsResponse {
        val result = reactionCommentQueryService.getAll()

        return ReactionCommentsResponse(
            revision = result.revision,
            comments = result.comments.map { group ->
                ReactionCommentGroupResponse(
                    concept = group.concept,
                    emoji = group.emoji,
                    contents = group.contents,
                )
            },
        )
    }
}

@Schema(description = "리액션 문구 조회 응답")
data class ReactionCommentsResponse(
    @field:Schema(
        description = "문구 전체의 최종 수정 시각(Asia/Seoul). 캐시한 값과 다르면 문구가 갱신된 것이다. " +
            "등록된 문구가 없으면 null",
        example = "2026-08-08T14:30:00.123456",
        type = "string",
        format = "date-time",
        nullable = true,
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val revision: LocalDateTime?,

    @field:Schema(
        description = "컨셉과 이모지 조합별 문구 목록. 등록된 문구가 없으면 빈 목록",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val comments: List<ReactionCommentGroupResponse>,
)

@Schema(description = "컨셉과 이모지 조합의 리액션 문구")
data class ReactionCommentGroupResponse(
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
        description = "무작위로 고를 수 있는 문구 목록",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val contents: List<String>,
)
