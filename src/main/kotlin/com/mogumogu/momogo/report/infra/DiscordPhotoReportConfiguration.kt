package com.mogumogu.momogo.report.infra

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

internal const val DISCORD_PHOTO_REPORT_REST_CLIENT = "discordPhotoReportRestClient"

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DiscordPhotoReportProperties::class)
class DiscordPhotoReportConfiguration {

    @Bean(DISCORD_PHOTO_REPORT_REST_CLIENT)
    fun discordPhotoReportRestClient(properties: DiscordPhotoReportProperties): RestClient {
        val httpClient = HttpClient
            .newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()
        val requestFactory = JdkClientHttpRequestFactory(httpClient).apply {
            setReadTimeout(READ_TIMEOUT)
        }

        return RestClient
            .builder()
            .baseUrl(properties.webhookUrl)
            .requestFactory(requestFactory)
            .build()
    }

    private companion object {
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(3)
        val READ_TIMEOUT: Duration = Duration.ofSeconds(5)
    }
}
