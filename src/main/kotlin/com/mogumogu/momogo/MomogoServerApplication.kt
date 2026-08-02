package com.mogumogu.momogo

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import java.util.TimeZone

internal const val APPLICATION_TIME_ZONE_ID = "Asia/Seoul"

@SpringBootApplication
class MomogoServerApplication

fun main(args: Array<String>) {
    configureApplicationTimeZone()
    runApplication<MomogoServerApplication>(*args)
}

internal fun configureApplicationTimeZone() {
    System.setProperty("user.timezone", APPLICATION_TIME_ZONE_ID)
    TimeZone.setDefault(TimeZone.getTimeZone(APPLICATION_TIME_ZONE_ID))
}
