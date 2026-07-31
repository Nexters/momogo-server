package com.mogumogu.momogo.photo.domain

import com.mogumogu.momogo.user.domain.User
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class PhotoTest : BehaviorSpec({

    given("유효한 사진 정보가 있으면") {
        `when`("사진을 생성할 때") {
            then("사진 정보를 읽기 전용으로 조회할 수 있다") {
                val uploader = User(_nickname = "모고")
                val photo = Photo(
                    _id = 1L,
                    _uploader = uploader,
                    _objectKey = "photos/2026/07/photo.jpg",
                    _sizeBytes = 1_024L,
                    _contentType = "image/jpeg",
                )

                photo.id shouldBe 1L
                photo.uploader shouldBe uploader
                photo.objectKey shouldBe "photos/2026/07/photo.jpg"
                photo.sizeBytes shouldBe 1_024L
                photo.contentType shouldBe "image/jpeg"
            }
        }

        `when`("허용되는 경계값으로 사진을 생성할 때") {
            then("object key 512자, content type 100자와 1 byte 크기를 허용한다") {
                val photo = Photo(
                    _uploader = User(_nickname = "모고"),
                    _objectKey = "k".repeat(512),
                    _sizeBytes = 1L,
                    _contentType = "t".repeat(100),
                )

                photo.objectKey.length shouldBe 512
                photo.sizeBytes shouldBe 1L
                photo.contentType.length shouldBe 100
            }
        }
    }

    given("유효하지 않은 사진 정보가 있으면") {
        `when`("업로더가 없을 때") {
            then("사진 생성을 거부한다") {
                shouldThrow<IllegalArgumentException> {
                    createPhoto(_uploader = null)
                }
            }
        }

        `when`("object key가 비어 있을 때") {
            then("사진 생성을 거부한다") {
                listOf("", " ", "\t").forEach { objectKey ->
                    shouldThrow<IllegalArgumentException> {
                        createPhoto(_objectKey = objectKey)
                    }
                }
            }
        }

        `when`("object key가 512자를 초과할 때") {
            then("사진 생성을 거부한다") {
                shouldThrow<IllegalArgumentException> {
                    createPhoto(_objectKey = "k".repeat(513))
                }
            }
        }

        `when`("content type이 비어 있을 때") {
            then("사진 생성을 거부한다") {
                listOf("", " ", "\t").forEach { contentType ->
                    shouldThrow<IllegalArgumentException> {
                        createPhoto(_contentType = contentType)
                    }
                }
            }
        }

        `when`("content type이 100자를 초과할 때") {
            then("사진 생성을 거부한다") {
                shouldThrow<IllegalArgumentException> {
                    createPhoto(_contentType = "t".repeat(101))
                }
            }
        }

        `when`("사진 크기가 양수가 아닐 때") {
            then("사진 생성을 거부한다") {
                listOf(0L, -1L).forEach { sizeBytes ->
                    shouldThrow<IllegalArgumentException> {
                        createPhoto(_sizeBytes = sizeBytes)
                    }
                }
            }
        }
    }
})

private fun createPhoto(
    _uploader: User? = User(_nickname = "모고"),
    _objectKey: String = "photos/photo.jpg",
    _sizeBytes: Long = 1_024L,
    _contentType: String = "image/jpeg",
): Photo =
    Photo(
        _uploader = _uploader,
        _objectKey = _objectKey,
        _sizeBytes = _sizeBytes,
        _contentType = _contentType,
    )
