package com.mogumogu.momogo.photo.infra

import com.mogumogu.momogo.photo.application.PhotoUploadUrlGenerator
import com.mogumogu.momogo.photo.domain.PhotoContentType
import com.mogumogu.momogo.photo.domain.PhotoObjectKey
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
@ApplyExtension(SpringExtension::class)
class R2PhotoUploadUrlGeneratorTest(
    private val generator: PhotoUploadUrlGenerator,
    private val clock: Clock,
) : BehaviorSpec({

    given("사진 오브젝트 키와 이미지 MIME 타입이 있으면") {
        `when`("R2 업로드 URL을 생성할 때") {
            val objectKey = PhotoObjectKey.generate(
                phase = "test",
                userId = 1L,
                uploadDate = LocalDate.of(2026, 8, 3),
                objectId = UUID.fromString("9f8b3a1c-2d4e-4a6b-8c0d-123456789abc"),
                contentType = requireNotNull(PhotoContentType.from("image/png")),
            )
            val contentTypeValue = "IMAGE/PNG"
            val issuedAtEarliest = LocalDateTime.now(clock)
            val result = generator.generate(objectKey, contentTypeValue)
            val issuedAtLatest = LocalDateTime.now(clock)
            val uri = URI(result.uploadUrl)
            val queryParameters = uri.queryParameters()

            then("test 버킷의 오브젝트 키로 PUT URL을 서명한다") {
                uri.host shouldBe "00000000000000000000000000000000.r2.cloudflarestorage.com"
                uri.path shouldBe
                    "/momogo-test/test/users/1/2026-08-03/9f8b3a1c-2d4e-4a6b-8c0d-123456789abc.png"
                queryParameters.getValue("X-Amz-Expires") shouldBe "900"
                queryParameters.getValue("X-Amz-SignedHeaders")
                    .split(";")
                    .toSet() shouldBe setOf("content-type", "host")
            }

            then("Asia/Seoul 기준 15분 뒤 만료 시각을 반환한다") {
                result.expiresAt.isBefore(issuedAtEarliest.plusMinutes(15).minusSeconds(1)) shouldBe false
                result.expiresAt.isAfter(issuedAtLatest.plusMinutes(15).plusSeconds(1)) shouldBe false
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
