package com.mogumogu.momogo.global.config

import com.mogumogu.momogo.APPLICATION_TIME_ZONE_ID
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.security.SecureRandom
import java.time.Clock
import java.time.ZoneId

@Configuration(proxyBeanMethods = false)
class InfrastructureConfiguration {

    @Bean
    fun clock(): Clock = Clock.system(APPLICATION_ZONE_ID)

    @Bean
    fun secureRandom(): SecureRandom = SecureRandom()

    private companion object {
        val APPLICATION_ZONE_ID: ZoneId = ZoneId.of(APPLICATION_TIME_ZONE_ID)
    }
}
