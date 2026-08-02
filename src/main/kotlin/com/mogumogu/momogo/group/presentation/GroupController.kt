package com.mogumogu.momogo.group.presentation

import com.mogumogu.momogo.global.config.OpenApiConfiguration
import com.mogumogu.momogo.global.security.RequestUserId
import com.mogumogu.momogo.group.application.CreateGroupCommand
import com.mogumogu.momogo.group.application.GetGroupInvitationCommand
import com.mogumogu.momogo.group.application.GroupService
import com.mogumogu.momogo.group.application.JoinGroupCommand
import com.mogumogu.momogo.group.application.LeaveGroupCommand
import com.mogumogu.momogo.group.application.UpdateGroupCommand
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
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
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

    @Operation(
        summary = "그룹명 변경",
        description = "현재 가입 중인 그룹 멤버가 그룹명을 변경합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "그룹명 변경 성공",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = UpdateGroupResponse::class),
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
                responseCode = "403",
                description = "현재 가입 중인 그룹 멤버가 아님",
                content = [
                    Content(
                        mediaType = "application/problem+json",
                        schema = Schema(implementation = ProblemDetail::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "그룹을 찾을 수 없음",
                content = [
                    Content(
                        mediaType = "application/problem+json",
                        schema = Schema(implementation = ProblemDetail::class),
                    ),
                ],
            ),
        ],
    )
    @PatchMapping("/{groupId}")
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
    fun update(
        @RequestUserId
        userId: Long,
        @PathVariable
        groupId: Long,
        @Valid
        @RequestBody
        request: UpdateGroupRequest,
    ): UpdateGroupResponse {
        val result = groupService.update(
            UpdateGroupCommand(
                userId = userId,
                groupId = groupId,
                name = request.name,
            ),
        )

        return UpdateGroupResponse(
            groupId = result.groupId,
            groupName = result.name,
        )
    }

    @Operation(
        summary = "초대 코드로 그룹 정보 확인",
        description = "그룹에 참여하기 전에 초대 코드에 해당하는 그룹 정보와 현재 참여 여부를 확인합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "그룹 정보 확인 성공",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = GroupInvitationResponse::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "유효하지 않은 초대 코드",
                content = [
                    Content(
                        mediaType = "application/problem+json",
                        schema = Schema(implementation = ProblemDetail::class),
                    ),
                ],
            ),
        ],
    )
    @GetMapping("/invitations")
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
    fun getInvitation(
        @RequestUserId
        userId: Long,
        @RequestParam
        code: String,
    ): GroupInvitationResponse {
        val result = groupService.getInvitation(
            GetGroupInvitationCommand(
                userId = userId,
                code = code,
            ),
        )

        return GroupInvitationResponse(
            groupId = result.groupId,
            groupName = result.groupName,
            totalMemberCount = result.totalMemberCount,
            participated = result.participated,
        )
    }

    @Operation(
        summary = "초대 코드로 그룹 참여",
        description = "초대 코드에 해당하는 그룹에 현재 사용자를 멤버로 등록합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "그룹 참여 성공",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = JoinGroupResponse::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "유효하지 않은 초대 코드 또는 사용자를 찾을 수 없음",
                content = [
                    Content(
                        mediaType = "application/problem+json",
                        schema = Schema(implementation = ProblemDetail::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "409",
                description = "이미 그룹에 가입했거나 그룹 최대 인원 초과",
                content = [
                    Content(
                        mediaType = "application/problem+json",
                        schema = Schema(implementation = ProblemDetail::class),
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/invitations")
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
    fun join(
        @RequestUserId
        userId: Long,
        @RequestBody
        request: JoinGroupRequest,
    ): JoinGroupResponse {
        val result = groupService.join(
            JoinGroupCommand(
                userId = userId,
                code = request.code,
            ),
        )

        return JoinGroupResponse(
            groupId = result.groupId,
            code = result.code,
        )
    }

    @Operation(
        summary = "그룹 탈퇴",
        description = "현재 사용자를 그룹에서 탈퇴시킵니다. 마지막 멤버가 탈퇴하면 그룹도 삭제합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "그룹 탈퇴 성공",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(type = "object", example = "{}"),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "그룹 또는 현재 사용자의 활성 멤버십을 찾을 수 없음",
                content = [
                    Content(
                        mediaType = "application/problem+json",
                        schema = Schema(implementation = ProblemDetail::class),
                    ),
                ],
            ),
        ],
    )
    @DeleteMapping("/{groupId}/members/me")
    @SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
    fun leave(
        @RequestUserId
        userId: Long,
        @PathVariable
        groupId: Long,
    ): Map<String, Any> {
        groupService.leave(
            LeaveGroupCommand(
                userId = userId,
                groupId = groupId,
            ),
        )
        return emptyMap()
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

@Schema(description = "그룹 수정 요청")
data class UpdateGroupRequest(
    @field:NotBlank(message = "name은 비어 있을 수 없습니다.")
    @field:Size(max = 255, message = "name은 255자를 초과할 수 없습니다.")
    @field:Schema(
        description = "변경할 그룹명",
        example = "우리 가족 하우스",
        maxLength = 255,
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val name: String,
)

@Schema(description = "그룹 수정 응답")
data class UpdateGroupResponse(
    @field:Schema(
        description = "그룹 ID",
        example = "10",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val groupId: Long,

    @field:Schema(
        description = "변경된 그룹명",
        example = "우리 가족 하우스",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val groupName: String,
)

@Schema(description = "초대 코드로 확인한 그룹 정보")
data class GroupInvitationResponse(
    @field:Schema(
        description = "그룹 ID",
        example = "10",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val groupId: Long,

    @field:Schema(
        description = "그룹명",
        example = "우리 가족",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val groupName: String,

    @field:Schema(
        description = "현재 그룹에 참여 중인 멤버 수",
        example = "4",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val totalMemberCount: Long,

    @field:Schema(
        description = "현재 사용자의 그룹 참여 여부",
        example = "false",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val participated: Boolean,
)

@Schema(description = "초대 코드로 그룹 참여 요청")
data class JoinGroupRequest(
    @field:Schema(
        description = "그룹 초대 코드",
        example = "A1B2C3",
        minLength = 6,
        maxLength = 6,
        pattern = "^[A-Z0-9]{6}$",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val code: String,
)

@Schema(description = "초대 코드로 그룹 참여 응답")
data class JoinGroupResponse(
    @field:Schema(
        description = "참여한 그룹 ID",
        example = "10",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val groupId: Long,

    @field:Schema(
        description = "사용한 그룹 초대 코드",
        example = "A1B2C3",
        pattern = "^[A-Z0-9]{6}$",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val code: String,
)
