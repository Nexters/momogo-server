package com.mogumogu.momogo.global.storage.r2

import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Duration

@SpringBootTest
@ActiveProfiles("test")
@ApplyExtension(SpringExtension::class)
class R2ConfigurationTest(
    private val properties: R2Properties,
    private val client: S3Client,
    private val presigner: S3Presigner,
) : BehaviorSpec({

    given("test 프로필의 R2 설정이 있으면") {
        `when`("R2 client와 presigner를 구성할 때") {
            val presignedRequest = presigner.presignPutObject(
                PutObjectPresignRequest
                    .builder()
                    .signatureDuration(Duration.ofMinutes(1))
                    .putObjectRequest(
                        PutObjectRequest
                            .builder()
                            .bucket(properties.bucket)
                            .key("setup-check")
                            .build(),
                    ).build(),
            )
            val uri = presignedRequest.url().toURI()

            then("Cloudflare R2 endpoint와 auto region을 사용한다") {
                properties.bucket shouldBe "momogo-test"
                client.serviceClientConfiguration().endpointOverride().orElseThrow() shouldBe
                    URI("https://00000000000000000000000000000000.r2.cloudflarestorage.com")
                client.serviceClientConfiguration().region() shouldBe Region.of("auto")
                uri.host shouldBe "00000000000000000000000000000000.r2.cloudflarestorage.com"
                uri.path shouldBe "/momogo-test/setup-check"
                uri.queryParameters().getValue("X-Amz-Credential")
                    .contains("/auto/s3/aws4_request") shouldBe true
            }
        }
    }
})

private fun URI.queryParameters(): Map<String, String> =
    rawQuery
        .split("&")
        .associate { parameter ->
            val (name, value) = parameter.split("=", limit = 2)
            URLDecoder.decode(name, StandardCharsets.UTF_8) to
                URLDecoder.decode(value, StandardCharsets.UTF_8)
        }
