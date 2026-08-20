package com.mogumogu.momogo.global.logging

/**
 * 요청 하나를 여러 로그 줄에 걸쳐 이어볼 수 있도록 MDC에 담는 키를 정의한다.
 */
object LogContext {

    const val REQUEST_ID_HEADER = "X-Request-Id"
    const val REQUEST_ID_KEY = "requestId"
    const val USER_ID_KEY = "userId"
}
