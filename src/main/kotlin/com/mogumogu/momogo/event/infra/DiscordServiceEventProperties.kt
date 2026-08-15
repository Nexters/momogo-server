package com.mogumogu.momogo.event.infra

import com.mogumogu.momogo.global.discord.requireDiscordWebhookUrl
import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI

@ConfigurationProperties(prefix = "momogo.event.discord")
data class DiscordServiceEventProperties(
    val webhookUrl: URI,
) {
    init {
        requireDiscordWebhookUrl(webhookUrl)
    }
}
