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
    val unprocessableEntity: Array<ErrorCode> = [],
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
    PHOTO_UPLOAD_URL_REQUEST(
        componentName = "PhotoUploadUrlRequestExample",
        summary = "사진 업로드 URL 발급 요청",
        value = mapOf(
            "contentType" to "image/webp",
        ),
    ),
    PHOTO_CREATE_REQUEST(
        componentName = "PhotoCreateRequestExample",
        summary = "사진 등록 요청",
        value = mapOf(
            "objectKey" to "dev/users/1/2026-08-03/9f8b3a1c-2d4e-4a6b-8c0d-123456789abc.webp",
            "groupIds" to listOf(10, 20),
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
                    "createdAt" to "2026-08-01T09:00:00.123456",
                    "totalMemberCount" to 2,
                    "todayPhotoUploaderCount" to 2,
                    "todayPhotoUploaded" to true,
                    "latestUploadAt" to "2026-08-10T14:30:00.123456",
                    "members" to listOf(
                        mapOf(
                            "userId" to 1,
                            "nickname" to "모모",
                            "mine" to true,
                        ),
                        mapOf(
                            "userId" to 2,
                            "nickname" to "모고",
                            "mine" to false,
                        ),
                    ),
                ),
            ),
        ),
    ),
    GROUP_DETAIL_RESPONSE(
        componentName = "GroupDetailResponseExample",
        summary = "날짜별 그룹 사진 조회 응답",
        value = mapOf(
            "groupId" to 10,
            "groupName" to "우리 가족",
            "inviteCode" to "A1B2C3",
            "createdAt" to "2026-08-01T09:00:00.123456",
            "date" to "2026-08-05",
            "members" to listOf(
                mapOf(
                    "userId" to 1,
                    "nickname" to "모모",
                    "mine" to true,
                    "photo" to null,
                ),
                mapOf(
                    "userId" to 2,
                    "nickname" to "모고",
                    "mine" to false,
                    "photo" to mapOf(
                        "photoId" to 501,
                        "downloadUrl" to "https://example.r2.cloudflarestorage.com/momogo-dev/dev/users/2/2026-08-05/example.webp?X-Amz-Signature=example",
                        "contentType" to "image/webp",
                        "createdAt" to "2026-08-05T12:30:00.123456",
                        "expiresAt" to "2026-08-05T12:45:00.123456",
                        "latestReaction" to mapOf(
                            "reactionId" to 901,
                            "userId" to 1,
                            "nickname" to "모모",
                            "concept" to "YOUNG_CREATOR_CREW",
                            "emoji" to "DELICIOUS",
                            "comment" to "야르~",
                            "createdAt" to "2026-08-05T13:00:00.123456",
                            "mine" to true,
                        ),
                    ),
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
    PHOTO_UPLOAD_URL_RESPONSE(
        componentName = "PhotoUploadUrlResponseExample",
        summary = "사진 업로드 URL 발급 성공 응답",
        value = mapOf(
            "uploadUrl" to "https://example.r2.cloudflarestorage.com/momogo-dev/dev/users/1/2026-08-03/9f8b3a1c-2d4e-4a6b-8c0d-123456789abc.webp?X-Amz-Signature=example",
            "objectKey" to "dev/users/1/2026-08-03/9f8b3a1c-2d4e-4a6b-8c0d-123456789abc.webp",
            "contentType" to "image/webp",
            "expiresAt" to "2026-08-03T18:15:00",
        ),
    ),
    PHOTO_CREATE_RESPONSE(
        componentName = "PhotoCreateResponseExample",
        summary = "사진 등록 성공 응답",
        value = mapOf(
            "photoId" to 501,
            "objectKey" to "dev/users/1/2026-08-03/9f8b3a1c-2d4e-4a6b-8c0d-123456789abc.webp",
            "createdAt" to "2026-08-03T14:30:00.123456",
        ),
    ),
    MY_PHOTOS_RESPONSE(
        componentName = "MyPhotosResponseExample",
        summary = "날짜별 내 사진 조회 성공 응답",
        value = mapOf(
            "date" to "2026-08-03",
            "photos" to listOf(
                mapOf(
                    "photoId" to 501,
                    "downloadUrl" to "https://example.r2.cloudflarestorage.com/momogo-dev/dev/users/1/2026-08-03/example.webp?X-Amz-Signature=example",
                    "contentType" to "image/webp",
                    "createdAt" to "2026-08-03T14:30:00.123456",
                    "expiresAt" to "2026-08-03T14:45:00.123456",
                ),
            ),
        ),
    ),
    PHOTO_REACTIONS_RESPONSE(
        componentName = "PhotoReactionsResponseExample",
        summary = "사진 리액션 조회 성공 응답",
        value = mapOf(
            "photoId" to 501,
            "groupId" to 10,
            "reactions" to listOf(
                mapOf(
                    "reactionId" to 901,
                    "userId" to 1,
                    "nickname" to "모모",
                    "concept" to "YOUNG_CREATOR_CREW",
                    "emoji" to "DELICIOUS",
                    "comment" to "야르~",
                    "createdAt" to "2026-08-08T14:30:00.123456",
                    "mine" to true,
                ),
                mapOf(
                    "reactionId" to 902,
                    "userId" to 2,
                    "nickname" to "모고",
                    "concept" to "YOUNG_CREATOR_CREW",
                    "emoji" to "HOT",
                    "comment" to "매워보여",
                    "createdAt" to "2026-08-08T14:31:00.123456",
                    "mine" to false,
                ),
            ),
        ),
    ),
    PHOTO_REACTION_CREATE_REQUEST(
        componentName = "PhotoReactionCreateRequestExample",
        summary = "사진 리액션 등록 요청",
        value = mapOf(
            "concept" to "YOUNG_CREATOR_CREW",
            "emoji" to "DELICIOUS",
            "comment" to "야르~",
        ),
    ),
    PHOTO_REPORT_REQUEST(
        componentName = "PhotoReportRequestExample",
        summary = "사진 신고 요청",
        value = mapOf(
            "reason" to "부적절한 사진이 포함되어 있습니다.",
        ),
    ),
    PHOTO_REACTION_CREATE_RESPONSE(
        componentName = "PhotoReactionCreateResponseExample",
        summary = "사진 리액션 등록 성공 응답",
        value = mapOf(
            "reactionId" to 901,
            "photoId" to 501,
            "groupId" to 10,
            "concept" to "YOUNG_CREATOR_CREW",
            "emoji" to "DELICIOUS",
            "comment" to "야르~",
            "createdAt" to "2026-08-08T14:30:00.123456",
        ),
    ),
    INIT_COMMENTS_RESPONSE(
        componentName = "InitCommentsResponseExample",
        summary = "리액션 문구 조회 응답",
        value = mapOf(
            "revision" to "2026-08-08T14:30:00.123456",
            "comments" to listOf(
                mapOf(
                    "concept" to "YOUNG_CREATOR_CREW",
                    "emoji" to "DELICIOUS",
                    "contents" to listOf("맛있겠다", "군침이 싹 도네"),
                ),
                mapOf(
                    "concept" to "YOUNG_CREATOR_CREW",
                    "emoji" to "HMM",
                    "contents" to listOf("음..."),
                ),
            ),
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
