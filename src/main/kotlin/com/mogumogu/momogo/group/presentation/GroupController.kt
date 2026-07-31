package com.mogumogu.momogo.group.presentation

import com.mogumogu.momogo.global.config.OpenApiConfiguration
import com.mogumogu.momogo.global.security.RequestUserId
import com.mogumogu.momogo.group.application.CreateGroupCommand
import com.mogumogu.momogo.group.application.GroupService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(
    name = "그룹",
    description = "그룹 관리 API",
)
@RestController
@RequestMapping("/api/v1/groups")
class GroupController(
    private val groupService: GroupService,
) {

    @Operation(
        summary = "그룹 생성",
        description = "그룹을 만들고 현재 사용자를 첫 번째 멤버로 등록합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "그룹 생성 성공",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = CreateGroupResponse::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "그룹명이 비어 있거나 255자를 초과함",
                content = [
                    Content(
                        mediaType = "application/problem+json",
                        schema = Schema(implementation = ProblemDetail::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "사용자를 찾을 수 없음",
                content = [
                    Content(
                        mediaType = "application/problem+json",
                        schema = Schema(implementation = ProblemDetail::class),
                    ),
                ],
            ),
        ],
    )
    @PostMapping
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
    fun create(
        @RequestUserId
        userId: Long,
        @Valid
        @RequestBody
        request: CreateGroupRequest,
    ): CreateGroupResponse {
        val result = groupService.create(
            CreateGroupCommand(
                userId = userId,
                name = request.name,
            ),
        )

        return CreateGroupResponse(
            groupId = result.groupId,
            groupName = result.name,
            inviteCode = result.inviteCode,
        )
    }
}

@Schema(description = "그룹 생성 요청")
data class CreateGroupRequest(
    @field:NotBlank(message = "name은 비어 있을 수 없습니다.")
    @field:Size(max = 255, message = "name은 255자를 초과할 수 없습니다.")
    @field:Schema(
        description = "생성할 그룹명",
        example = "모고모고",
        maxLength = 255,
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val name: String,
)

@Schema(description = "그룹 생성 응답")
data class CreateGroupResponse(
    @field:Schema(
        description = "그룹 ID",
        example = "1",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val groupId: Long,

    @field:Schema(
        description = "그룹명",
        example = "모고모고",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val groupName: String,

    @field:Schema(
        description = "그룹 초대 코드",
        example = "ABC123",
        pattern = "^[A-Z0-9]{6}$",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val inviteCode: String,
)
