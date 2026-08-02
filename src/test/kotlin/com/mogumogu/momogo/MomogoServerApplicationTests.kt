package com.mogumogu.momogo

import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import jakarta.persistence.EntityManagerFactory
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.ZoneId

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ApplyExtension(SpringExtension::class)
class MomogoServerApplicationTests(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val entityManagerFactory: EntityManagerFactory,
    private val clock: Clock,
) : BehaviorSpec({

    given("애플리케이션이 실행된 상태에서") {
        `when`("Actuator 헬스 체크를 요청하면") {
            val response = mockMvc.perform(get("/actuator/health"))
                .andReturn()
                .response

            then("정상 상태를 반환한다") {
                response.status shouldBe HttpStatus.OK.value()
                objectMapper.readTree(response.contentAsString)["status"].stringValue() shouldBe "UP"
            }
        }
    }

    given("Hibernate JDBC 오류 로그 설정을 확인하면") {
        then("DB 제약 오류의 민감한 값을 프레임워크가 원문으로 기록하지 않는다") {
            entityManagerFactory.properties["hibernate.jdbc.log.errors"].toString() shouldBe "false"
        }
    }

    given("애플리케이션 전역 시계를 확인하면") {
        then("한국 시간대를 사용한다") {
            clock.zone shouldBe ZoneId.of("Asia/Seoul")
        }
    }

    given("기본 test 프로필에서 Swagger 문서가 비활성화된 상태에서") {
        `when`("OpenAPI 문서를 요청하면") {
            val response = mockMvc.perform(get("/v3/api-docs"))
                .andReturn()
                .response

            then("문서 엔드포인트를 노출하지 않는다") {
                response.status shouldBe HttpStatus.NOT_FOUND.value()
            }
        }
    }

})
