package com.mogumogu.momogo.global.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

@Profile("local")
@Configuration(proxyBeanMethods = false)
class LocalH2ConsoleSecurityConfiguration {

    @Bean
    @Order(1)
    fun localH2ConsoleSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/h2-console/**")
            .csrf { csrf -> csrf.disable() }
            .requestCache { requestCache -> requestCache.disable() }
            .sessionManagement { sessions ->
                sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }
            .headers { headers ->
                headers.frameOptions { frameOptions -> frameOptions.sameOrigin() }
            }
            .authorizeHttpRequests { requests ->
                requests.anyRequest().permitAll()
            }

        return http.build()
    }
}
