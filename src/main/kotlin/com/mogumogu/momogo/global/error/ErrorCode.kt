package com.mogumogu.momogo.global.error

enum class ErrorCode(
    val message: String,
) {
    INVALID_REQUEST(
        message = "요청 값이 올바르지 않습니다.",
    ),
    UNSUPPORTED_PROVIDER(
        message = "지원하지 않는 로그인 제공자입니다.",
    ),
    INVALID_AUTH_CREDENTIALS(
        message = "인증 정보가 올바르지 않습니다.",
    ),
    INVALID_REFRESH_TOKEN(
        message = "유효하지 않은 리프레시 토큰입니다.",
    ),
    USER_NOT_FOUND(
        message = "사용자를 찾을 수 없습니다.",
    ),
    INVALID_INVITATION_CODE(
        message = "유효하지 않은 초대 코드입니다.",
    ),
    ALREADY_JOINED(
        message = "이미 그룹에 가입되어 있습니다.",
    ),
    GROUP_FULL(
        message = "그룹의 최대 인원을 초과했습니다.",
    ),
    DUPLICATE_LOGIN_ACCOUNT(
        message = "이미 등록된 로그인 계정입니다.",
    ),
    FORBIDDEN(
        message = "접근 권한이 없습니다.",
    ),
    RESOURCE_NOT_FOUND(
        message = "요청한 리소스를 찾을 수 없습니다.",
    ),
    INTERNAL_SERVER_ERROR(
        message = "서버 내부 오류가 발생했습니다.",
    ),
}
