package com.mogumogu.momogo.global.error

import io.swagger.v3.oas.annotations.media.Schema
import java.net.URI

@Schema(description = "API 오류 응답")
data class ApiProblemDetail(
    @field:Schema(
        description = "오류 유형을 식별하는 URI",
        example = "about:blank",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED,
    )
    val type: URI? = null,

    @field:Schema(
        description = "HTTP 상태를 설명하는 제목",
        example = "Bad Request",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val title: String,

    @field:Schema(
        description = "HTTP 상태 코드",
        example = "400",
        minimum = "100",
        maximum = "599",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val status: Int,

    @field:Schema(
        description = "외부에 공개해도 안전한 오류 설명",
        example = "요청 값이 올바르지 않습니다.",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val detail: String,

    @field:Schema(
        description = "오류가 발생한 요청 경로",
        example = "/api/v1/groups/invitations",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val instance: URI,

    @field:Schema(
        description = "클라이언트가 오류를 구분할 때 사용하는 코드",
        example = "INVALID_REQUEST",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val code: ErrorCode,

    @field:Schema(
        description = "요청 본문 필드별 검증 오류. 필드 검증이 실패한 경우에만 포함됩니다.",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED,
    )
    val errors: List<ApiValidationError>? = null,
)

@Schema(description = "요청 필드 검증 오류")
data class ApiValidationError(
    @field:Schema(
        description = "검증에 실패한 요청 필드",
        example = "nickname",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val field: String,

    @field:Schema(
        description = "검증 실패 이유",
        example = "닉네임은 비어 있을 수 없습니다.",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val message: String,
)
