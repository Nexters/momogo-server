package com.mogumogu.momogo

import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
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

})
