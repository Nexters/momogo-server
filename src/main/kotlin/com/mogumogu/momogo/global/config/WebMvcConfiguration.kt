package com.mogumogu.momogo.global.config

import com.mogumogu.momogo.global.security.RequestUserIdArgumentResolver
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration(proxyBeanMethods = false)
class WebMvcConfiguration(
    private val requestUserIdArgumentResolver: RequestUserIdArgumentResolver,
) : WebMvcConfigurer {

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(requestUserIdArgumentResolver)
    }
}
