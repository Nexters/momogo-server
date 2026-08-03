package com.mogumogu.momogo.global.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.auditing.DateTimeProvider
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
class JpaConfig {

    // timestamp 컬럼의 정밀도가 마이크로초이므로 감사 시각도 같은 정밀도로 맞춘다.
    // 나노초를 그대로 두면 메모리 엔티티와 저장된 값이 달라져, 저장 직후 응답으로 내려준 시각과
    // 다시 조회한 시각이 어긋난다.
    @Bean
    fun auditingDateTimeProvider(): DateTimeProvider =
        DateTimeProvider {
            Optional.of(Instant.now().truncatedTo(ChronoUnit.MICROS))
        }
}
