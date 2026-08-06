package com.mogumogu.momogo.global.time

import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

@Component
class DailyTimeRangeFactory(
    private val clock: Clock,
) {

    fun today(): DailyTimeRange = create(LocalDate.now(clock))

    fun create(date: LocalDate): DailyTimeRange =
        DailyTimeRange(
            date = date,
            startAt = date.atStartOfDay(clock.zone).toInstant(),
            endAt = date.plusDays(1).atStartOfDay(clock.zone).toInstant(),
        )
}

data class DailyTimeRange(
    val date: LocalDate,
    val startAt: Instant,
    val endAt: Instant,
)
