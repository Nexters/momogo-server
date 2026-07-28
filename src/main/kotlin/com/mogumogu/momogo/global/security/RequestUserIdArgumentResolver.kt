package com.mogumogu.momogo.global.security

import com.mogumogu.momogo.global.error.ApiException
import com.mogumogu.momogo.global.error.ErrorCode
import org.springframework.core.MethodParameter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

@Component
class RequestUserIdArgumentResolver : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(RequestUserId::class.java) &&
            (
                parameter.parameterType == Long::class.javaPrimitiveType ||
                    parameter.parameterType == Long::class.javaObjectType
                )

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Long {
        val authentication = SecurityContextHolder.getContext().authentication
        val jwt = authentication
            ?.takeIf { it.isAuthenticated }
            ?.principal as? Jwt
            ?: throw ApiException.Unauthorized(ErrorCode.INVALID_AUTH_CREDENTIALS)

        return jwt.subject?.toLongOrNull()
            ?: throw ApiException.Unauthorized(ErrorCode.INVALID_AUTH_CREDENTIALS)
    }
}
