package com.mogumogu.momogo.event.infra

import com.mogumogu.momogo.event.domain.ServiceEvent
import com.mogumogu.momogo.event.domain.ServiceEventType
import com.mogumogu.momogo.global.config.ApplicationPhase
import com.mogumogu.momogo.global.discord.discordWebhookClient
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.env.MockEnvironment
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

class DiscordServiceEventNotifierTest : BehaviorSpec({

    val webhookUrl = "https://discord.com/api/webhooks/123456789/test_webhook-token"
    val occurredAt = Instant.parse("2026-08-10T05:30:00Z")
    val clock = Clock.fixed(occurredAt, ZoneOffset.UTC)
    val applicationPhase = ApplicationPhase(MockEnvironment().apply { setActiveProfiles("dev") })
    val jsonMapper = JsonMapper.builder().build()

    given("그룹 참여 이벤트가 있으면") {
        `when`("Discord webhook이 성공할 때") {
            val builder = RestClient.builder().baseUrl(webhookUrl)
            val server = MockRestServiceServer.bindTo(builder).build()
            val notifier = DiscordServiceEventNotifier(discordWebhookClient(builder), applicationPhase, clock)

            server
                .expect(ExpectedCount.once(), requestTo("$webhookUrl?wait=true"))
                .andExpect(method(HttpMethod.POST))
                .andExpect { request ->
                    request.headers.contentType shouldBe MediaType.APPLICATION_JSON
                    val body = jsonMapper.readTree((request as MockClientHttpRequest).bodyAsString)
                    body.propertyNames().asSequence().toSet() shouldBe
                        setOf("username", "allowed_mentions", "embeds")
                    body["username"].stringValue() shouldBe "momogo-event"
                    body["allowed_mentions"]["parse"].size() shouldBe 0

                    val embed = body["embeds"][0]
                    embed["author"]["name"].stringValue() shouldBe "momogo · DEV"
                    embed["title"].stringValue() shouldBe "🤝 그룹 참여"
                    embed["color"].intValue() shouldBe ServiceEventType.GROUP_JOINED.color
                    embed["timestamp"].stringValue() shouldBe occurredAt.toString()
                    val fields = embed["fields"]
                    fields.size() shouldBe 2
                    fields[0]["name"].stringValue() shouldBe "사용자 ID"
                    fields[0]["value"].stringValue() shouldBe "7"
                    fields[1]["name"].stringValue() shouldBe "그룹 ID"
                    fields[1]["value"].stringValue() shouldBe "42"
                }
                .andRespond(withSuccess("""{"id":"987654321"}""", MediaType.APPLICATION_JSON))

            then("wait=true POST 한 번으로 멘션 없는 embed를 전송한다") {
                notifier.onServiceEvent(
                    ServiceEvent(
                        type = ServiceEventType.GROUP_JOINED,
                        userId = 7L,
                        groupId = 42L,
                    ),
                )
                server.verify()
            }
        }
    }

    given("그룹 ID가 없는 회원 가입 이벤트가 있으면") {
        `when`("알림을 전송할 때") {
            val builder = RestClient.builder().baseUrl(webhookUrl)
            val server = MockRestServiceServer.bindTo(builder).build()
            val notifier = DiscordServiceEventNotifier(discordWebhookClient(builder), applicationPhase, clock)

            server
                .expect(ExpectedCount.once(), requestTo("$webhookUrl?wait=true"))
                .andExpect { request ->
                    val body = jsonMapper.readTree((request as MockClientHttpRequest).bodyAsString)
                    val embed = body["embeds"][0]
                    embed["title"].stringValue() shouldBe "🎉 회원 가입"
                    val fields = embed["fields"]
                    fields.size() shouldBe 2
                    fields[0]["name"].stringValue() shouldBe "사용자 ID"
                    fields[1]["name"].stringValue() shouldBe "현재 가입자"
                    fields[1]["value"].stringValue() shouldBe "12명"
                }
                .andRespond(withSuccess("""{"id":"987654321"}""", MediaType.APPLICATION_JSON))

            then("빈 그룹 ID 필드를 보내지 않는다") {
                notifier.onServiceEvent(
                    ServiceEvent(
                        type = ServiceEventType.USER_REGISTERED,
                        userId = 7L,
                        totalUserCount = 12L,
                    ),
                )
                server.verify()
            }
        }
    }

    listOf(HttpStatus.NO_CONTENT, HttpStatus.FOUND, HttpStatus.BAD_GATEWAY).forEach { responseStatus ->
        given("Discord webhook이 ${responseStatus.value()} 상태를 반환하면") {
            `when`("알림을 전송할 때") {
                val builder = RestClient.builder().baseUrl(webhookUrl)
                val server = MockRestServiceServer.bindTo(builder).build()
                val notifier = DiscordServiceEventNotifier(discordWebhookClient(builder), applicationPhase, clock)
                server
                    .expect(ExpectedCount.once(), requestTo("$webhookUrl?wait=true"))
                    .andRespond(
                        withStatus(responseStatus)
                            .body("upstream detail must not escape"),
                    )

                then("사용자 요청이 실패하지 않도록 재시도 없이 예외를 삼킨다") {
                    notifier.onServiceEvent(
                        ServiceEvent(
                            type = ServiceEventType.USER_REGISTERED,
                            userId = 7L,
                        ),
                    )
                    server.verify()
                }
            }
        }
    }
})
