package com.mogumogu.momogo.global.time

import com.mogumogu.momogo.APPLICATION_TIME_ZONE_ID
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DailyTimeRangeFactoryTest : BehaviorSpec({
    val zoneId = ZoneId.of(APPLICATION_TIME_ZONE_ID)
    val clock = Clock.fixed(Instant.parse("2026-08-03T15:30:00Z"), zoneId)
    val factory = DailyTimeRangeFactory(clock)

    given("애플리케이션 시간대의 현재 시각이 있으면") {
        `when`("오늘 범위를 생성할 때") {
            then("오늘 날짜와 시작 시각 이상, 다음 날 시작 시각 미만의 범위를 반환한다") {
                factory.today() shouldBe
                    DailyTimeRange(
                        date = LocalDate.of(2026, 8, 4),
                        startAt = Instant.parse("2026-08-03T15:00:00Z"),
                        endAt = Instant.parse("2026-08-04T15:00:00Z"),
                    )
            }
        }
    }

    given("조회할 날짜가 있으면") {
        `when`("해당 날짜 범위를 생성할 때") {
            then("애플리케이션 시간대를 기준으로 양 끝 시각을 계산한다") {
                factory.create(LocalDate.of(2026, 8, 3)) shouldBe
                    DailyTimeRange(
                        date = LocalDate.of(2026, 8, 3),
                        startAt = Instant.parse("2026-08-02T15:00:00Z"),
                        endAt = Instant.parse("2026-08-03T15:00:00Z"),
                    )
            }
        }
    }
})
