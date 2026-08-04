package com.mogumogu.momogo.photo.infra

import com.mogumogu.momogo.global.storage.r2.R2Properties
import com.mogumogu.momogo.photo.domain.PhotoObjectKey
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectResponse
import software.amazon.awssdk.services.s3.model.S3Exception
import java.lang.reflect.Proxy
import java.net.URI

class R2PhotoObjectMetadataReaderTest : BehaviorSpec({

    val objectKey = PhotoObjectKey.parse(
        "test/users/1/2026-08-03/9f8b3a1c-2d4e-4a6b-8c0d-123456789abc.png",
    )
    val properties = R2Properties(
        endpoint = URI("https://00000000000000000000000000000000.r2.cloudflarestorage.com"),
        bucket = "momogo-test",
        accessKeyId = "test-access-key-id",
        secretAccessKey = "test-secret-access-key",
    )

    given("R2에 사진 오브젝트가 있으면") {
        `when`("오브젝트 메타데이터를 조회할 때") {
            var capturedRequest: HeadObjectRequest? = null
            val client = fakeS3Client { request ->
                capturedRequest = request
                HeadObjectResponse
                    .builder()
                    .contentLength(1_024L)
                    .contentType("IMAGE/PNG")
                    .build()
            }
            val reader = R2PhotoObjectMetadataReader(client, properties)

            val metadata = reader.find(objectKey)

            then("설정된 버킷과 키로 HEAD 요청하고 크기와 Content-Type을 반환한다") {
                capturedRequest?.bucket() shouldBe "momogo-test"
                capturedRequest?.key() shouldBe objectKey.value
                metadata?.sizeBytes shouldBe 1_024L
                metadata?.contentTypeValue shouldBe "IMAGE/PNG"
            }
        }
    }

    given("R2에 사진 오브젝트가 없으면") {
        `when`("HEAD 요청이 404를 반환할 때") {
            val reader = R2PhotoObjectMetadataReader(
                client = fakeS3Client {
                    throw S3Exception.builder().statusCode(404).build()
                },
                properties = properties,
            )

            then("오브젝트가 없음을 반환한다") {
                reader.find(objectKey).shouldBeNull()
            }
        }
    }

    given("R2 접근 실패가 오브젝트 없음이 아니면") {
        `when`("HEAD 요청이 403을 반환할 때") {
            val reader = R2PhotoObjectMetadataReader(
                client = fakeS3Client {
                    throw S3Exception.builder().statusCode(403).build()
                },
                properties = properties,
            )

            then("인프라 오류를 숨기지 않고 전파한다") {
                shouldThrow<S3Exception> {
                    reader.find(objectKey)
                }.statusCode() shouldBe 403
            }
        }
    }
})

private fun fakeS3Client(
    headObject: (HeadObjectRequest) -> HeadObjectResponse,
): S3Client =
    Proxy.newProxyInstance(
        S3Client::class.java.classLoader,
        arrayOf(S3Client::class.java),
    ) { _, method, arguments ->
        when (method.name) {
            "headObject" -> headObject(arguments?.first() as HeadObjectRequest)
            "serviceName" -> "s3"
            "close" -> null
            "toString" -> "FakeS3Client"
            else -> throw UnsupportedOperationException(method.name)
        }
    } as S3Client
