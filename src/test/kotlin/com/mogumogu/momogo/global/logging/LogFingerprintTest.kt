package com.mogumogu.momogo.global.logging

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch

class LogFingerprintTest : BehaviorSpec({

    given("로그에 남길 수 없는 값을 지문으로 바꾸면") {
        val providerToken = "29EC4D44-0688-4472-A771-021C5B7A3E27"

        `when`("같은 값을 여러 번 변환할 때") {
            then("항상 같은 지문이 되어 요청 간 대조할 수 있다") {
                LogFingerprint.of(providerToken) shouldBe LogFingerprint.of(providerToken)
            }
        }

        `when`("다른 값을 변환할 때") {
            then("다른 지문이 되어 값이 바뀐 것을 알 수 있다") {
                LogFingerprint.of(providerToken) shouldNotBe
                    LogFingerprint.of("9BEA747F-B40E-481B-A2B4-A925C861B10F")
            }
        }

        then("8자리 16진수만 남긴다") {
            LogFingerprint.of(providerToken) shouldMatch Regex("^[0-9a-f]{8}$")
        }

        then("원문을 포함하지 않는다") {
            LogFingerprint.of(providerToken).contains(providerToken) shouldBe false
            providerToken.contains(LogFingerprint.of(providerToken)) shouldBe false
        }
    }
})
