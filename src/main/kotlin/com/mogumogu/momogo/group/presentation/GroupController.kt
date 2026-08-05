package com.mogumogu.momogo.group.presentation

import com.mogumogu.momogo.global.config.OpenApiConfiguration
import com.mogumogu.momogo.global.error.ErrorCode
import com.mogumogu.momogo.global.openapi.ApiErrors
import com.mogumogu.momogo.global.openapi.ApiExamples
import com.mogumogu.momogo.global.openapi.OpenApiExample
import com.mogumogu.momogo.global.security.RequestUserId
import com.mogumogu.momogo.group.application.CreateGroupCommand
import com.mogumogu.momogo.group.application.GetGroupInvitationCommand
import com.mogumogu.momogo.group.application.GroupService
import com.mogumogu.momogo.group.application.JoinGroupCommand
import com.mogumogu.momogo.group.application.LeaveGroupCommand
import com.mogumogu.momogo.group.application.UpdateGroupCommand
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@Tag(
    name = "그룹",
    description = "그룹 관리 API",
)
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
@RestController
@RequestMapping("/api/v1/groups")
class GroupController(
    private val groupService: GroupService,
) {

    @Operation(
        summary = "내가 참여한 그룹 조회",
        description = "현재 사용자가 참여 중인 그룹과 오늘 사진을 올린 그룹 멤버 수를 조회합니다.",
    )
    @ApiExamples(success = OpenApiExample.JOINED_GROUPS_RESPONSE)
    @ApiErrors(notFound = [ErrorCode.USER_NOT_FOUND])
    @GetMapping
    fun getJoinedGroups(
        @RequestUserId
        userId: Long,
    ): JoinedGroupsResponse {
        val result = groupService.getJoinedGroups(userId)

        return JoinedGroupsResponse(
            date = result.date,
            groups = result.groups.map { group ->
                JoinedGroupResponse(
                    groupId = group.groupId,
                    groupName = group.groupName,
                    totalMemberCount = group.totalMemberCount,
                    todayPhotoUploaderCount = group.todayPhotoUploaderCount,
                )
            },
        )
    }

    @Operation(
        summary = "그룹 생성",
        description = "그룹을 만들고 현재 사용자를 첫 번째 멤버로 등록합니다.",
    )
    @ApiExamples(
        request = OpenApiExample.CREATE_GROUP_REQUEST,
        success = OpenApiExample.CREATE_GROUP_RESPONSE,
    )
    @ApiErrors(
        badRequest = [ErrorCode.INVALID_REQUEST],
        notFound = [ErrorCode.USER_NOT_FOUND],
    )
    @PostMapping
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
    @ApiExamples(
        request = OpenApiExample.UPDATE_GROUP_REQUEST,
        success = OpenApiExample.UPDATE_GROUP_RESPONSE,
    )
    @ApiErrors(
        badRequest = [ErrorCode.INVALID_REQUEST],
        forbidden = [ErrorCode.NOT_GROUP_MEMBER],
        notFound = [ErrorCode.GROUP_NOT_FOUND],
    )
    @PatchMapping("/{groupId}")
    fun update(
        @RequestUserId
        userId: Long,
        @Parameter(example = "10")
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
    @ApiExamples(success = OpenApiExample.GROUP_INVITATION_RESPONSE)
    @ApiErrors(notFound = [ErrorCode.INVALID_INVITATION_CODE])
    @GetMapping("/invitations")
    fun getInvitation(
        @RequestUserId
        userId: Long,
        @Parameter(example = "A1B2C3")
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
    @ApiExamples(
        request = OpenApiExample.JOIN_GROUP_REQUEST,
        success = OpenApiExample.JOIN_GROUP_RESPONSE,
    )
    @ApiErrors(
        notFound = [
            ErrorCode.USER_NOT_FOUND,
            ErrorCode.INVALID_INVITATION_CODE,
        ],
        conflict = [
            ErrorCode.ALREADY_JOINED,
            ErrorCode.GROUP_FULL,
        ],
    )
    @PostMapping("/invitations")
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
    @ApiExamples(success = OpenApiExample.EMPTY_OBJECT_RESPONSE)
    @ApiErrors(
        notFound = [
            ErrorCode.GROUP_NOT_FOUND,
            ErrorCode.MEMBER_NOT_FOUND,
        ],
    )
    @DeleteMapping("/{groupId}/members/me")
    fun leave(
        @RequestUserId
        userId: Long,
        @Parameter(example = "10")
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

@Schema(description = "현재 사용자가 참여 중인 그룹 목록")
data class JoinedGroupsResponse(
    @field:Schema(
        description = "오늘의 그룹 활동을 계산한 날짜(Asia/Seoul)",
        example = "2026-08-02",
        type = "string",
        format = "date",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val date: LocalDate,

    @field:Schema(
        description = "현재 참여 중인 그룹 목록",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val groups: List<JoinedGroupResponse>,
)

@Schema(description = "참여 중인 그룹 요약")
data class JoinedGroupResponse(
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
        minimum = "1",
        maximum = "8",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val totalMemberCount: Long,

    @field:Schema(
        description = "오늘 그룹에 사진을 올린 현재 멤버 수",
        example = "2",
        minimum = "0",
        maximum = "8",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val todayPhotoUploaderCount: Long,
)

@Schema(description = "그룹 생성 요청")
data class CreateGroupRequest(
    @field:NotBlank(message = "name은 비어 있을 수 없습니다.")
    @field:Size(max = 16, message = "name은 16자를 초과할 수 없습니다.")
    @field:Schema(
        description = "생성할 그룹명",
        example = "모고모고",
        maxLength = 16,
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
    @field:Size(max = 16, message = "name은 16자를 초과할 수 없습니다.")
    @field:Schema(
        description = "변경할 그룹명",
        example = "우리 가족 하우스",
        maxLength = 16,
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
