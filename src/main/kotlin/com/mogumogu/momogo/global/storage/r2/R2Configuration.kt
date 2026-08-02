package com.mogumogu.momogo.global.storage.r2

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(R2Properties::class)
class R2Configuration {

    @Bean
    fun r2Client(properties: R2Properties): S3Client =
        S3Client
            .builder()
            .endpointOverride(properties.endpoint)
            .region(R2_REGION)
            .credentialsProvider(properties.credentialsProvider())
            .httpClientBuilder(UrlConnectionHttpClient.builder())
            .serviceConfiguration(s3Configuration())
            .build()

    @Bean
    fun r2Presigner(properties: R2Properties): S3Presigner =
        S3Presigner
            .builder()
            .endpointOverride(properties.endpoint)
            .region(R2_REGION)
            .credentialsProvider(properties.credentialsProvider())
            .serviceConfiguration(s3Configuration())
            .build()

    private fun R2Properties.credentialsProvider(): StaticCredentialsProvider =
        StaticCredentialsProvider.create(
            AwsBasicCredentials.create(accessKeyId, secretAccessKey),
        )

    private fun s3Configuration(): S3Configuration =
        S3Configuration
            .builder()
            .pathStyleAccessEnabled(true)
            .chunkedEncodingEnabled(false)
            .build()

    private companion object {
        val R2_REGION: Region = Region.of("auto")
    }
}
