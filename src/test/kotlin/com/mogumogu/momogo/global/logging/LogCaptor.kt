package com.mogumogu.momogo.global.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory

/**
 * 로그가 실제로 남는지 검증하기 위해 특정 로거의 로그를 수집한다.
 */
fun captureLogs(
    loggerClass: Class<*>,
    level: Level = Level.INFO,
    block: () -> Unit,
): List<ILoggingEvent> {
    val logger = LoggerFactory.getLogger(loggerClass) as Logger
    val appender = ListAppender<ILoggingEvent>().apply { start() }
    val originalLevel = logger.level

    logger.level = level
    logger.addAppender(appender)

    try {
        block()
    } finally {
        logger.detachAppender(appender)
        appender.stop()
        logger.level = originalLevel
    }

    return appender.list.toList()
}

fun List<ILoggingEvent>.messagesAt(level: Level): List<String> =
    filter { it.level == level }.map { it.formattedMessage }
