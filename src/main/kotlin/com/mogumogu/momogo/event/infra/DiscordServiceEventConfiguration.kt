package com.mogumogu.momogo.event.infra

import com.mogumogu.momogo.global.discord.DiscordWebhookClient
import com.mogumogu.momogo.global.discord.discordWebhookClient
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.EnableAsync

internal const val DISCORD_SERVICE_EVENT_CLIENT = "discordServiceEventClient"

// test 프로필에서는 통합 테스트가 실제 Discord로 요청하지 않도록 알림 전체를 비활성화한다.
@Profile("!test")
@EnableAsync
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DiscordServiceEventProperties::class)
class DiscordServiceEventConfiguration {

    @Bean(DISCORD_SERVICE_EVENT_CLIENT)
    fun discordServiceEventClient(properties: DiscordServiceEventProperties): DiscordWebhookClient =
        discordWebhookClient(properties.webhookUrl)
}
