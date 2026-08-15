package com.mogumogu.momogo.report.infra

import com.mogumogu.momogo.global.discord.DiscordWebhookClient
import com.mogumogu.momogo.global.discord.discordWebhookClient
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

internal const val DISCORD_PHOTO_REPORT_CLIENT = "discordPhotoReportClient"

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DiscordPhotoReportProperties::class)
class DiscordPhotoReportConfiguration {

    @Bean(DISCORD_PHOTO_REPORT_CLIENT)
    fun discordPhotoReportClient(properties: DiscordPhotoReportProperties): DiscordWebhookClient =
        discordWebhookClient(properties.webhookUrl)
}
