package com.mogumogu.momogo.user.domain

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly

class LoginProviderTest : BehaviorSpec({

    given("지원하는 로그인 제공자를 확인하면") {
        then("게스트와 카카오, 네이버, 애플을 제공한다") {
            LoginProvider.entries shouldContainExactly listOf(
                LoginProvider.GUEST,
                LoginProvider.KAKAO,
                LoginProvider.NAVER,
                LoginProvider.APPLE,
            )
        }
    }
})
