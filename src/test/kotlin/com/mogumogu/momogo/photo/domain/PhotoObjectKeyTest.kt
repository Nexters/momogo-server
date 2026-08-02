package com.mogumogu.momogo.photo.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.util.UUID

class PhotoObjectKeyTest : BehaviorSpec({

    given("사진 업로드 정보가 있으면") {
        `when`("오브젝트 키를 생성할 때") {
            then("Spring profile을 첫 경로로 사용한다") {
                val objectKey = PhotoObjectKey.generate(
                    phase = "local",
                    userId = 1L,
                    uploadDate = LocalDate.of(2026, 8, 3),
                    objectId = UUID.fromString("9f8b3a1c-2d4e-4a6b-8c0d-123456789abc"),
                    contentType = requireNotNull(PhotoContentType.from("image/webp")),
                )

                objectKey.value shouldBe
                    "local/users/1/2026-08-03/9f8b3a1c-2d4e-4a6b-8c0d-123456789abc.webp"
                objectKey.phase shouldBe "local"
                objectKey.userId shouldBe 1L
                objectKey.uploadDate shouldBe LocalDate.of(2026, 8, 3)
                objectKey.extension shouldBe "webp"
                objectKey.belongsTo("local", 1L) shouldBe true
                objectKey.belongsTo("dev", 1L) shouldBe false
                objectKey.belongsTo("local", 2L) shouldBe false
            }
        }

        `when`("사용자 ID가 양수가 아닐 때") {
            then("오브젝트 키 생성을 거부한다") {
                listOf(0L, -1L).forEach { userId ->
                    shouldThrow<IllegalArgumentException> {
                        PhotoObjectKey.generate(
                            phase = "dev",
                            userId = userId,
                            uploadDate = LocalDate.of(2026, 8, 3),
                            objectId = UUID.fromString("9f8b3a1c-2d4e-4a6b-8c0d-123456789abc"),
                            contentType = requireNotNull(PhotoContentType.from("image/webp")),
                        )
                    }
                }
            }
        }

        `when`("여러 이미지 MIME 타입으로 키를 생성할 때") {
            then("안전하게 만든 확장자를 포함하고 다시 파싱할 수 있다") {
                mapOf(
                    "image/jpeg" to "jpg",
                    "image/svg+xml" to "svg",
                    "image/vnd.example.format" to "vnd-example-format",
                ).forEach { (contentTypeValue, extension) ->
                    val generated = PhotoObjectKey.generate(
                        phase = "dev",
                        userId = 1L,
                        uploadDate = LocalDate.of(2026, 8, 3),
                        objectId = UUID.fromString("9f8b3a1c-2d4e-4a6b-8c0d-123456789abc"),
                        contentType = requireNotNull(PhotoContentType.from(contentTypeValue)),
                    )

                    generated.extension shouldBe extension
                    PhotoObjectKey.parse(generated.value) shouldBe generated
                }
            }
        }
    }

    given("사진 오브젝트 키 문자열이 있으면") {
        `when`("서버가 발급한 형식일 때") {
            then("구성 요소를 복원한다") {
                val objectKey = PhotoObjectKey.parse(
                    "dev/users/42/2026-08-03/9f8b3a1c-2d4e-4a6b-8c0d-123456789abc.webp",
                )

                objectKey.phase shouldBe "dev"
                objectKey.userId shouldBe 42L
                objectKey.uploadDate shouldBe LocalDate.of(2026, 8, 3)
                objectKey.objectId shouldBe UUID.fromString("9f8b3a1c-2d4e-4a6b-8c0d-123456789abc")
                objectKey.extension shouldBe "webp"
            }
        }

        `when`("형식이나 값이 올바르지 않을 때") {
            then("파싱을 거부한다") {
                listOf(
                    "../dev/users/1/2026-08-03/9f8b3a1c-2d4e-4a6b-8c0d-123456789abc.webp",
                    "dev/users/0/2026-08-03/9f8b3a1c-2d4e-4a6b-8c0d-123456789abc.webp",
                    "dev/users/1/2026-02-30/9f8b3a1c-2d4e-4a6b-8c0d-123456789abc.webp",
                    "dev/users/1/2026-08-03/not-a-uuid.webp",
                    "dev/users/1/2026-08-03/9f8b3a1c-2d4e-4a6b-8c0d-123456789abc.tar.gz",
                    "dev/users/1/2026-08-03/9f8b3a1c-2d4e-4a6b-8c0d-123456789abc.webp/../other",
                ).forEach { value ->
                    shouldThrow<IllegalArgumentException> {
                        PhotoObjectKey.parse(value)
                    }
                }
            }
        }
    }
})
