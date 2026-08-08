package com.mogumogu.momogo.reaction.presentation

import com.mogumogu.momogo.reaction.domain.Emoji
import com.mogumogu.momogo.reaction.domain.ReactionComment
import com.mogumogu.momogo.reaction.domain.ReactionConcept
import com.mogumogu.momogo.reaction.infra.ReactionCommentRepository
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.engine.concurrency.TestExecutionMode
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import tools.jackson.databind.ObjectMapper

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ApplyExtension(SpringExtension::class)
class ReactionCommentApiIntegrationTest(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val reactionCommentRepository: ReactionCommentRepository,
) : BehaviorSpec({
    testExecutionMode = TestExecutionMode.Sequential

    given("컨셉과 이모지별 리액션 문구가 등록되어 있으면") {
        `when`("인증 없이 리액션 문구를 조회할 때") {
            reactionCommentRepository.deleteAll()
            reactionCommentRepository.saveAll(
                listOf(
                    createReactionComment(Emoji.DELICIOUS, "맛있겠다"),
                    createReactionComment(Emoji.DELICIOUS, "군침이 싹 도네"),
                    createReactionComment(Emoji.HMM, "음..."),
                ),
            )

            val response = mockMvc.perform(get("/init/comments"))
                .andReturn()
                .response

            then("컨셉과 이모지 조합별로 묶인 문구 목록을 반환한다") {
                response.status shouldBe HttpStatus.OK.value()
                response.contentType shouldBe MediaType.APPLICATION_JSON_VALUE

                val body = objectMapper.readTree(response.contentAsString)
                body["revision"].isNull shouldBe false

                val comments = body["comments"]
                comments.size() shouldBe 2

                comments[0]["concept"].stringValue() shouldBe "YOUNG_CREATOR_CREW"
                comments[0]["emoji"].stringValue() shouldBe "DELICIOUS"
                comments[0]["contents"].size() shouldBe 2
                comments[0]["contents"][0].stringValue() shouldBe "맛있겠다"
                comments[0]["contents"][1].stringValue() shouldBe "군침이 싹 도네"
                comments[1]["emoji"].stringValue() shouldBe "HMM"
                comments[1]["contents"].size() shouldBe 1
                comments[1]["contents"][0].stringValue() shouldBe "음..."
            }
        }
    }

    given("이미 조회한 리액션 문구가 있으면") {
        `when`("문구를 추가한 뒤 다시 조회할 때") {
            reactionCommentRepository.deleteAll()
            reactionCommentRepository.save(createReactionComment(Emoji.FLEX, "플렉스 인정"))

            fun requestRevision(): String =
                objectMapper.readTree(
                    mockMvc.perform(get("/init/comments")).andReturn().response.contentAsString,
                )["revision"].stringValue()

            val previousRevision = requestRevision()
            reactionCommentRepository.save(createReactionComment(Emoji.SEXY, "이건 못 참지"))

            then("revision이 변경되어 캐시 갱신을 알 수 있다") {
                requestRevision() shouldNotBe previousRevision
            }
        }
    }

    given("등록된 리액션 문구가 없으면") {
        `when`("리액션 문구를 조회할 때") {
            reactionCommentRepository.deleteAll()

            val response = mockMvc.perform(get("/init/comments"))
                .andReturn()
                .response

            then("빈 목록과 null revision을 반환한다") {
                response.status shouldBe HttpStatus.OK.value()

                val body = objectMapper.readTree(response.contentAsString)
                body["comments"].size() shouldBe 0
                body["revision"].isNull shouldBe true
            }
        }
    }
})

private fun createReactionComment(
    emoji: Emoji,
    content: String,
): ReactionComment =
    ReactionComment(
        _concept = ReactionConcept.YOUNG_CREATOR_CREW,
        _emoji = emoji,
        _content = content,
    )
