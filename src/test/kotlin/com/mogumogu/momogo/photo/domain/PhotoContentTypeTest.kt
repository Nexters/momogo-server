package com.mogumogu.momogo.photo.domain

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class PhotoContentTypeTest : BehaviorSpec({

    given("사진 content type이 있으면") {
        `when`("image 하위 타입일 때") {
            then("정규화된 content type과 안전한 확장자로 변환한다") {
                mapOf(
                    "image/webp" to "webp",
                    "image/jpeg" to "jpg",
                    "image/png" to "png",
                    "image/gif" to "gif",
                    "image/svg+xml" to "svg",
                    "image/vnd.microsoft.icon" to "ico",
                    "image/vnd.example.format" to "vnd-example-format",
                ).forEach { (value, extension) ->
                    val contentType = requireNotNull(PhotoContentType.from(value))

                    contentType.value shouldBe value
                    contentType.extension shouldBe extension
                }
            }
        }

        `when`("대소문자가 섞여 있을 때") {
            then("소문자로 정규화한다") {
                val contentType = requireNotNull(PhotoContentType.from("IMAGE/WEBP"))

                contentType.value shouldBe "image/webp"
                contentType.extension shouldBe "webp"
            }
        }

        `when`("이미지가 아니거나 올바른 MIME 타입이 아닐 때") {
            then("변환하지 않는다") {
                listOf(
                    "application/octet-stream",
                    "text/plain",
                    "image/",
                    "image/*",
                    "image/webp; charset=utf-8",
                    " image/webp",
                ).forEach { value ->
                    PhotoContentType.from(value).shouldBeNull()
                }
            }
        }
    }
})
