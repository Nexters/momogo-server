package com.mogumogu.momogo.report.infra

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.net.URI

class DiscordPhotoReportPropertiesTest : BehaviorSpec({

    given("HTTPS discord.com webhook URL이 있으면") {
        `when`("표준 webhook 경로이고 query와 fragment가 없을 때") {
            val webhookUrl = URI(
                "https://discord.com/api/webhooks/123456789/abc_DEF-ghi.jkl~token",
            )

            then("설정을 생성한다") {
                DiscordPhotoReportProperties(webhookUrl).webhookUrl shouldBe webhookUrl
            }
        }
    }

    given("Discord webhook 형식이 아닌 URL이 있으면") {
        val secretToken = "do-not-expose-this-token"
        val invalidUrls = listOf(
            "http://discord.com/api/webhooks/123/$secretToken",
            "https://example.com/api/webhooks/123/$secretToken",
            "https://discord.com:8443/api/webhooks/123/$secretToken",
            "https://user@discord.com/api/webhooks/123/$secretToken",
            "https://discord.com/api/webhooks/not-number/$secretToken",
            "https://discord.com/api/webhooks/123/$secretToken/extra",
            "https://discord.com/api/webhooks/123/$secretToken?wait=true",
            "https://discord.com/api/webhooks/123/$secretToken#fragment",
        )

        invalidUrls.forEach { invalidUrl ->
            `when`("$invalidUrl 설정을 생성할 때") {
                then("비밀값을 포함하지 않은 검증 오류를 던진다") {
                    val exception = shouldThrow<IllegalArgumentException> {
                        DiscordPhotoReportProperties(URI(invalidUrl))
                    }
                    exception.message.orEmpty().contains(secretToken) shouldBe false
                }
            }
        }
    }
})
