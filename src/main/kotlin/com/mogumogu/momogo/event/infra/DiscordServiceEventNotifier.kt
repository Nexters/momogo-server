package com.mogumogu.momogo.event.infra

import com.mogumogu.momogo.event.domain.ServiceEvent
import com.mogumogu.momogo.global.config.ApplicationPhase
import com.mogumogu.momogo.global.discord.DiscordAllowedMentions
import com.mogumogu.momogo.global.discord.DiscordEmbed
import com.mogumogu.momogo.global.discord.DiscordEmbedAuthor
import com.mogumogu.momogo.global.discord.DiscordEmbedField
import com.mogumogu.momogo.global.discord.DiscordWebhookClient
import com.mogumogu.momogo.global.discord.DiscordWebhookException
import com.mogumogu.momogo.global.discord.DiscordWebhookPayload
import com.mogumogu.momogo.global.discord.send
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionalEventListener
import java.time.Clock
import java.util.Locale

@Profile("!test")
@Component
class DiscordServiceEventNotifier(
    @param:Qualifier(DISCORD_SERVICE_EVENT_CLIENT)
    private val discordWebhookClient: DiscordWebhookClient,
    private val applicationPhase: ApplicationPhase,
    private val clock: Clock,
) {

    // 커밋된 이벤트만 알리고, Discord 왕복이 사용자 요청을 지연시키지 않도록 별도 스레드에서 전송한다.
    @Async
    @TransactionalEventListener
    fun onServiceEvent(event: ServiceEvent) {
        try {
            discordWebhookClient.send(event.toDiscordPayload())
        } catch (exception: DiscordWebhookException) {
            // 알림 실패로 사용자 요청까지 실패하지 않도록 삼킨다.
            log.warn(
                "Discord 서비스 이벤트 알림 전송에 실패했습니다. type={} reason={}",
                event.type,
                exception.reason,
            )
        }
    }

    private fun ServiceEvent.toDiscordPayload(): DiscordWebhookPayload {
        val phase = applicationPhase.value.uppercase(Locale.ROOT)

        return DiscordWebhookPayload(
            username = "momogo-event",
            allowedMentions = DiscordAllowedMentions(parse = emptyList()),
            embeds = listOf(
                DiscordEmbed(
                    // 여러 환경이 한 채널을 공유하므로 환경을 매 메시지 상단에 고정 노출한다.
                    author = DiscordEmbedAuthor(name = "momogo · $phase"),
                    title = "${type.emoji} ${type.title}",
                    color = type.color,
                    fields = buildList {
                        userId?.let { value ->
                            add(
                                DiscordEmbedField(
                                    name = "사용자 ID",
                                    value = value.toString(),
                                    inline = true,
                                ),
                            )
                        }
                        groupId?.let { value ->
                            add(
                                DiscordEmbedField(
                                    name = "그룹 ID",
                                    value = value.toString(),
                                    inline = true,
                                ),
                            )
                        }
                        totalUserCount?.let { value ->
                            add(
                                DiscordEmbedField(
                                    name = "현재 가입자",
                                    value = "${value}명",
                                    inline = true,
                                ),
                            )
                        }
                    },
                    timestamp = clock.instant().toString(),
                ),
            ),
        )
    }

    private companion object {
        val log = LoggerFactory.getLogger(DiscordServiceEventNotifier::class.java)
    }
}
