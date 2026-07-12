package com.mogumogu.momogo.global.error

enum class ErrorCode(
    val message: String,
) {
    INVALID_REQUEST(
        message = "요청 값이 올바르지 않습니다.",
    ),
    RESOURCE_NOT_FOUND(
        message = "요청한 리소스를 찾을 수 없습니다.",
    ),
    INTERNAL_SERVER_ERROR(
        message = "서버 내부 오류가 발생했습니다.",
    ),
}
