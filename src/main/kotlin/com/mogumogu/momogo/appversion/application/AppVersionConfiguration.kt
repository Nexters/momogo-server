package com.mogumogu.momogo.appversion.application

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AppVersionProperties::class)
class AppVersionConfiguration
