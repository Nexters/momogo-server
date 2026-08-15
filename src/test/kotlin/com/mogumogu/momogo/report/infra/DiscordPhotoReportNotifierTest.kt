package com.mogumogu.momogo.report.infra

import com.mogumogu.momogo.global.discord.discordWebhookClient
import com.mogumogu.momogo.report.application.PhotoReportNotification
import com.mogumogu.momogo.report.application.PhotoReportNotificationException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.http.client.MockClientHttpRequest
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class DiscordPhotoReportNotifierTest : BehaviorSpec({

    val webhookUrl = "https://discord.com/api/webhooks/123456789/test_webhook-token"
    val reportedAt = Instant.parse("2026-08-10T05:30:00Z")
    val clock = Clock.fixed(reportedAt, ZoneOffset.UTC)
    val notification = PhotoReportNotification(
        phase = "dev",
        reporterId = 1L,
        groupId = 10L,
        photoId = 501L,
        reason = "부적절한 사진입니다.\n확인해 주세요.",
    )
    val jsonMapper = JsonMapper.builder().build()

    given("유효한 사진 신고 알림이 있으면") {
        `when`("Discord webhook이 성공할 때") {
            val builder = RestClient.builder().baseUrl(webhookUrl)
            val server = MockRestServiceServer.bindTo(builder).build()
            val notifier = DiscordPhotoReportNotifier(discordWebhookClient(builder), clock)

            server
                .expect(ExpectedCount.once(), requestTo("$webhookUrl?wait=true"))
                .andExpect(method(HttpMethod.POST))
                .andExpect { request ->
                    request.headers.contentType shouldBe MediaType.APPLICATION_JSON
                    val body = jsonMapper.readTree((request as MockClientHttpRequest).bodyAsString)
                    body.propertyNames().asSequence().toSet() shouldBe
                        setOf("username", "allowed_mentions", "embeds")
                    body["username"].stringValue() shouldBe "momogo-report"
                    body["allowed_mentions"]["parse"].size() shouldBe 0

                    val embed = body["embeds"][0]
                    // author를 쓰지 않는 알림이므로 null 필드가 직렬화되지 않아야 한다.
                    embed.propertyNames().asSequence().toSet() shouldBe
                        setOf("title", "color", "fields", "timestamp")
                    embed["title"].stringValue() shouldBe "[DEV] 사진 신고 접수"
                    embed["color"].intValue() shouldBe 15_158_332
                    embed["timestamp"].stringValue() shouldBe reportedAt.toString()
                    val fields = embed["fields"]
                    fields[0]["name"].stringValue() shouldBe "환경"
                    fields[0]["value"].stringValue() shouldBe "DEV"
                    fields[1]["name"].stringValue() shouldBe "신고자 ID"
                    fields[1]["value"].stringValue() shouldBe "1"
                    fields[2]["name"].stringValue() shouldBe "그룹 ID"
                    fields[2]["value"].stringValue() shouldBe "10"
                    fields[3]["name"].stringValue() shouldBe "사진 ID"
                    fields[3]["value"].stringValue() shouldBe "501"
                    fields[4]["name"].stringValue() shouldBe "신고 사유"
                    fields[4]["value"].stringValue() shouldBe notification.reason
                }
                .andRespond(withSuccess("""{"id":"987654321"}""", MediaType.APPLICATION_JSON))

            then("wait=true POST 한 번으로 멘션 없는 embed를 전송한다") {
                notifier.notify(notification)
                server.verify()
            }
        }
    }

    listOf(HttpStatus.NO_CONTENT, HttpStatus.FOUND, HttpStatus.BAD_GATEWAY).forEach { responseStatus ->
        given("Discord webhook이 ${responseStatus.value()} 상태를 반환하면") {
            `when`("신고 알림을 전송할 때") {
                val builder = RestClient.builder().baseUrl(webhookUrl)
                val server = MockRestServiceServer.bindTo(builder).build()
                val notifier = DiscordPhotoReportNotifier(discordWebhookClient(builder), clock)
                server
                    .expect(ExpectedCount.once(), requestTo("$webhookUrl?wait=true"))
                    .andRespond(
                        withStatus(responseStatus)
                            .body("upstream detail must not escape"),
                    )

                then("재시도하거나 원본 응답을 노출하지 않고 알림 예외를 던진다") {
                    val exception = shouldThrow<PhotoReportNotificationException> {
                        notifier.notify(notification)
                    }
                    exception.message.shouldBeNull()
                    exception.cause.shouldBeNull()
                    server.verify()
                }
            }
        }
    }
})
