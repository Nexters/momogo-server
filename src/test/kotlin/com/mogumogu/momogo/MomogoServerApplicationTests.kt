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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ApplyExtension(SpringExtension::class)
class MomogoServerApplicationTests(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val entityManagerFactory: EntityManagerFactory,
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

})
