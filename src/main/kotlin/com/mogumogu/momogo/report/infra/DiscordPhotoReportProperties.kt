package com.mogumogu.momogo.report.infra

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI

@ConfigurationProperties(prefix = "momogo.report.discord")
data class DiscordPhotoReportProperties(
    val webhookUrl: URI,
) {
    init {
        require(webhookUrl.scheme.equals("https", ignoreCase = true)) {
            "Discord webhook URL은 HTTPS 주소여야 합니다."
        }
        require(webhookUrl.host.equals(DISCORD_HOST, ignoreCase = true)) {
            "Discord webhook URL의 호스트가 올바르지 않습니다."
        }
        require(webhookUrl.userInfo == null && webhookUrl.port == -1) {
            "Discord webhook URL의 authority가 올바르지 않습니다."
        }
        require(WEBHOOK_PATH.matches(webhookUrl.rawPath.orEmpty())) {
            "Discord webhook URL의 경로가 올바르지 않습니다."
        }
        require(webhookUrl.rawQuery == null && webhookUrl.rawFragment == null) {
            "Discord webhook URL에는 query 또는 fragment를 지정할 수 없습니다."
        }
    }

    private companion object {
        const val DISCORD_HOST = "discord.com"
        val WEBHOOK_PATH = Regex("""/api/webhooks/[0-9]+/[A-Za-z0-9._~-]+""")
    }
}
