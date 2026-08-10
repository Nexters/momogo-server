package com.mogumogu.momogo.report.infra

import com.fasterxml.jackson.annotation.JsonProperty
import com.mogumogu.momogo.report.application.PhotoReportNotification
import com.mogumogu.momogo.report.application.PhotoReportNotificationException
import com.mogumogu.momogo.report.application.PhotoReportNotifier
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.time.Clock
import java.util.Locale

@Component
class DiscordPhotoReportNotifier(
    @param:Qualifier(DISCORD_PHOTO_REPORT_REST_CLIENT)
    private val restClient: RestClient,
    private val clock: Clock,
) : PhotoReportNotifier {

    override fun notify(notification: PhotoReportNotification) {
        try {
            restClient
                .post()
                .uri { uriBuilder ->
                    uriBuilder
                        .queryParam("wait", true)
                        .build()
                }
                .contentType(MediaType.APPLICATION_JSON)
                .body(notification.toDiscordPayload())
                .retrieve()
                .onStatus({ status -> status != HttpStatus.OK }) { _, _ ->
                    throw PhotoReportNotificationException()
                }
                .toBodilessEntity()
        } catch (_: RestClientException) {
            // Webhook URL은 인증정보이므로 원본 예외의 메시지나 cause를 외부로 전달하지 않는다.
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

private data class DiscordWebhookPayload(
    val username: String,
    @field:JsonProperty("allowed_mentions")
    val allowedMentions: DiscordAllowedMentions,
    val embeds: List<DiscordEmbed>,
)

private data class DiscordAllowedMentions(
    val parse: List<String>,
)

private data class DiscordEmbed(
    val title: String,
    val color: Int,
    val fields: List<DiscordEmbedField>,
    val timestamp: String,
)

private data class DiscordEmbedField(
    val name: String,
    val value: String,
    val inline: Boolean,
)
