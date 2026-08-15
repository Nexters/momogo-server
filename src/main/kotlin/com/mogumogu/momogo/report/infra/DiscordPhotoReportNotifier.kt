package com.mogumogu.momogo.report.infra

import com.mogumogu.momogo.global.discord.DiscordAllowedMentions
import com.mogumogu.momogo.global.discord.DiscordEmbed
import com.mogumogu.momogo.global.discord.DiscordEmbedField
import com.mogumogu.momogo.global.discord.DiscordWebhookClient
import com.mogumogu.momogo.global.discord.DiscordWebhookException
import com.mogumogu.momogo.global.discord.DiscordWebhookPayload
import com.mogumogu.momogo.global.discord.send
import com.mogumogu.momogo.report.application.PhotoReportNotification
import com.mogumogu.momogo.report.application.PhotoReportNotificationException
import com.mogumogu.momogo.report.application.PhotoReportNotifier
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.Locale

@Component
class DiscordPhotoReportNotifier(
    @param:Qualifier(DISCORD_PHOTO_REPORT_CLIENT)
    private val discordWebhookClient: DiscordWebhookClient,
    private val clock: Clock,
) : PhotoReportNotifier {

    override fun notify(notification: PhotoReportNotification) {
        try {
            discordWebhookClient.send(notification.toDiscordPayload())
        } catch (_: DiscordWebhookException) {
            throw PhotoReportNotificationException()
        }
    }

    private fun PhotoReportNotification.toDiscordPayload(): DiscordWebhookPayload =
        DiscordWebhookPayload(
            username = "momogo-report",
            allowedMentions = DiscordAllowedMentions(parse = emptyList()),
            embeds = listOf(
                DiscordEmbed(
                    title = "[${phase.uppercase(Locale.ROOT)}] 사진 신고 접수",
                    color = REPORT_EMBED_COLOR,
                    fields = listOf(
                        DiscordEmbedField(
                            name = "환경",
                            value = phase.uppercase(Locale.ROOT),
                            inline = true,
                        ),
                        DiscordEmbedField(
                            name = "신고자 ID",
                            value = reporterId.toString(),
                            inline = true,
                        ),
                        DiscordEmbedField(
                            name = "그룹 ID",
                            value = groupId.toString(),
                            inline = true,
                        ),
                        DiscordEmbedField(
                            name = "사진 ID",
                            value = photoId.toString(),
                            inline = true,
                        ),
                        DiscordEmbedField(
                            name = "신고 사유",
                            value = reason,
                            inline = false,
                        ),
                    ),
                    timestamp = clock.instant().toString(),
                ),
            ),
        )

    private companion object {
        const val REPORT_EMBED_COLOR = 15_158_332
    }
}
