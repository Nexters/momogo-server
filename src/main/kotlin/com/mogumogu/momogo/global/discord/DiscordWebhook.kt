package com.mogumogu.momogo.global.discord

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.annotation.PostExchange
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import java.net.URI
import java.net.http.HttpClient
import java.time.Duration

private const val DISCORD_HOST = "discord.com"
private val WEBHOOK_PATH = Regex("""/api/webhooks/[0-9]+/[A-Za-z0-9._~-]+""")
private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(3)
private val READ_TIMEOUT: Duration = Duration.ofSeconds(5)

// reason은 webhook URL이 섞이지 않는 값만 담는다. 원본 예외의 메시지에는 요청 URL이 포함될 수 있다.
class DiscordWebhookException(val reason: String) : RuntimeException()

interface DiscordWebhookClient {

    // wait는 JSON 요청이므로 form body가 아닌 쿼리 파라미터로 전송된다.
    @PostExchange(contentType = MediaType.APPLICATION_JSON_VALUE)
    fun postMessage(
        @RequestParam("wait") wait: Boolean,
        @RequestBody payload: DiscordWebhookPayload,
    )
}

fun DiscordWebhookClient.send(payload: DiscordWebhookPayload) {
    try {
        postMessage(wait = true, payload = payload)
    } catch (exception: RestClientException) {
        throw DiscordWebhookException(exception.javaClass.simpleName)
    }
}

// 검증 실패 메시지는 webhook token을 포함하지 않도록 URL 원문을 담지 않는다.
fun requireDiscordWebhookUrl(webhookUrl: URI) {
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

fun discordWebhookClient(webhookUrl: URI): DiscordWebhookClient {
    val httpClient = HttpClient
        .newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()
    val requestFactory = JdkClientHttpRequestFactory(httpClient).apply {
        setReadTimeout(READ_TIMEOUT)
    }

    return discordWebhookClient(
        RestClient
            .builder()
            .baseUrl(webhookUrl)
            .requestFactory(requestFactory),
    )
}

// 테스트가 MockRestServiceServer에 bind한 builder로 같은 클라이언트를 만들 수 있도록 분리한다.
fun discordWebhookClient(builder: RestClient.Builder): DiscordWebhookClient {
    val restClient = builder
        // Discord가 메시지 저장을 확인한 200 응답만 성공으로 취급한다.
        .defaultStatusHandler({ status -> status != HttpStatus.OK }) { _, response ->
            throw DiscordWebhookException("HTTP ${response.statusCode.value()}")
        }
        .build()

    return HttpServiceProxyFactory
        .builderFor(RestClientAdapter.create(restClient))
        .build()
        .createClient(DiscordWebhookClient::class.java)
}

data class DiscordWebhookPayload(
    val username: String,
    @field:JsonProperty("allowed_mentions")
    val allowedMentions: DiscordAllowedMentions,
    val embeds: List<DiscordEmbed>,
)

data class DiscordAllowedMentions(
    val parse: List<String>,
)

// author를 쓰지 않는 알림이 있으므로 null 필드는 직렬화하지 않는다. Discord는 author=null을 거부한다.
@JsonInclude(JsonInclude.Include.NON_NULL)
data class DiscordEmbed(
    val title: String,
    val color: Int,
    val fields: List<DiscordEmbedField>,
    val timestamp: String,
    val author: DiscordEmbedAuthor? = null,
)

data class DiscordEmbedAuthor(
    val name: String,
)

data class DiscordEmbedField(
    val name: String,
    val value: String,
    val inline: Boolean,
)
