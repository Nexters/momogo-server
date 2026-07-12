package com.mogumogu.momogo

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MomogoServerApplication

fun main(args: Array<String>) {
    runApplication<MomogoServerApplication>(*args)
}
