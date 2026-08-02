package com.mogumogu.momogo.global.openapi

import com.mogumogu.momogo.global.error.ErrorCode

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ApiErrors(
    val badRequest: Array<ErrorCode> = [],
    val unauthorized: Array<ErrorCode> = [],
    val forbidden: Array<ErrorCode> = [],
    val notFound: Array<ErrorCode> = [],
    val conflict: Array<ErrorCode> = [],
    val internalServerError: Array<ErrorCode> = [],
)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ApiExamples(
    val request: OpenApiExample = OpenApiExample.NONE,
    val success: OpenApiExample = OpenApiExample.NONE,
)

enum class OpenApiExample(
    val componentName: String,
    val summary: String,
    val value: Any?,
) {
    NONE(
        componentName = "",
        summary = "",
        value = null,
    ),
    REGISTER_REQUEST(
        componentName = "RegisterRequestExample",
        summary = "게스트 회원가입 요청",
        value = mapOf(
            "provider" to "GUEST",
            "providerToken" to "guest-device-token",
            "nickname" to "모모",
        ),
    ),
    LOGIN_REQUEST(
        componentName = "LoginRequestExample",
        summary = "게스트 로그인 요청",
        value = mapOf(
            "provider" to "GUEST",
            "providerToken" to "guest-device-token",
        ),
    ),
    REFRESH_TOKEN_REQUEST(
        componentName = "RefreshTokenRequestExample",
        summary = "리프레시 토큰 요청",
        value = mapOf(
            "refreshToken" to "example-refresh-token",
        ),
    ),
    UPDATE_NICKNAME_REQUEST(
        componentName = "UpdateNicknameRequestExample",
        summary = "닉네임 변경 요청",
        value = mapOf(
            "nickname" to "새 닉네임",
        ),
    ),
    CREATE_GROUP_REQUEST(
        componentName = "CreateGroupRequestExample",
        summary = "그룹 생성 요청",
        value = mapOf(
            "name" to "모고모고",
        ),
    ),
    UPDATE_GROUP_REQUEST(
        componentName = "UpdateGroupRequestExample",
        summary = "그룹명 변경 요청",
        value = mapOf(
            "name" to "우리 가족 하우스",
        ),
    ),
    JOIN_GROUP_REQUEST(
        componentName = "JoinGroupRequestExample",
        summary = "초대 코드로 그룹 참여 요청",
        value = mapOf(
            "code" to "A1B2C3",
        ),
    ),
    AUTH_RESPONSE(
        componentName = "AuthResponseExample",
        summary = "인증 성공 응답",
        value = mapOf(
            "userId" to 1,
            "nickname" to "모모",
            "accessToken" to "example-access-token",
            "refreshToken" to "example-refresh-token",
        ),
    ),
    REISSUE_RESPONSE(
        componentName = "ReissueResponseExample",
        summary = "토큰 재발급 성공 응답",
        value = mapOf(
            "accessToken" to "new-example-access-token",
            "refreshToken" to "new-example-refresh-token",
        ),
    ),
    EMPTY_OBJECT_RESPONSE(
        componentName = "EmptyObjectResponseExample",
        summary = "빈 객체 응답",
        value = emptyMap<String, Any>(),
    ),
    USER_RESPONSE(
        componentName = "UserResponseExample",
        summary = "사용자 정보 응답",
        value = mapOf(
            "userId" to 1,
            "nickname" to "모모",
        ),
    ),
    UPDATED_USER_RESPONSE(
        componentName = "UpdatedUserResponseExample",
        summary = "닉네임 변경 성공 응답",
        value = mapOf(
            "userId" to 1,
            "nickname" to "새 닉네임",
        ),
    ),
    JOINED_GROUPS_RESPONSE(
        componentName = "JoinedGroupsResponseExample",
        summary = "참여 중인 그룹 목록 응답",
        value = mapOf(
            "date" to "2026-08-02",
            "groups" to listOf(
                mapOf(
                    "groupId" to 10,
                    "groupName" to "우리 가족",
                    "totalMemberCount" to 4,
                    "todayPhotoUploaderCount" to 2,
                ),
            ),
        ),
    ),
    CREATE_GROUP_RESPONSE(
        componentName = "CreateGroupResponseExample",
        summary = "그룹 생성 성공 응답",
        value = mapOf(
            "groupId" to 10,
            "groupName" to "모고모고",
            "inviteCode" to "A1B2C3",
        ),
    ),
    UPDATE_GROUP_RESPONSE(
        componentName = "UpdateGroupResponseExample",
        summary = "그룹명 변경 성공 응답",
        value = mapOf(
            "groupId" to 10,
            "groupName" to "우리 가족 하우스",
        ),
    ),
    GROUP_INVITATION_RESPONSE(
        componentName = "GroupInvitationResponseExample",
        summary = "초대 코드로 확인한 그룹 정보 응답",
        value = mapOf(
            "groupId" to 10,
            "groupName" to "우리 가족",
            "totalMemberCount" to 4,
            "participated" to false,
        ),
    ),
    JOIN_GROUP_RESPONSE(
        componentName = "JoinGroupResponseExample",
        summary = "그룹 참여 성공 응답",
        value = mapOf(
            "groupId" to 10,
            "code" to "A1B2C3",
        ),
    ),
    APP_VERSION_RESPONSE(
        componentName = "AppVersionResponseExample",
        summary = "앱 버전 체크 응답",
        value = mapOf(
            "latestVersion" to "1.0.0",
            "minSupportedVersion" to "1.0.0",
            "forceUpdate" to false,
            "updateUrl" to "https://apps.apple.com/app/id000000000",
        ),
    ),
}
