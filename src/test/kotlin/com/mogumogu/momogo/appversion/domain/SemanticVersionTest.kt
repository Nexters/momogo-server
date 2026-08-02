package com.mogumogu.momogo.appversion.domain

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class SemanticVersionTest : BehaviorSpec({

    given("유효한 앱 버전들이 있으면") {
        `when`("major, minor, patch가 다른 버전을 비교할 때") {
            val versions = listOf(
                "0.9.9",
                "1.0.0",
                "1.1.0",
                "1.1.1",
                "2.0.0",
            ).map { value -> requireNotNull(SemanticVersion.parseOrNull(value)) }

            then("숫자 우선순위 순서대로 비교한다") {
                versions.zipWithNext().forEach { (lower, higher) ->
                    (lower < higher) shouldBe true
                }
            }
        }
    }

    given("유효하지 않은 버전 문자열이 있으면") {
        then("파싱에 실패한다") {
            listOf(
                "1.2",
                "1.2.3.4",
                "01.2.3",
                "1.02.3",
                "1.2.03",
                "1.2.3-rc.1",
                "1.2.3+build.1",
                "999999999999999999999999.2.3",
                " 1.2.3",
                "1.2.3 ",
            ).forEach { value ->
                SemanticVersion.parseOrNull(value) shouldBe null
            }
        }
    }
})
