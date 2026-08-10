package com.mogumogu.momogo.report.application

fun interface PhotoReportNotifier {
    fun notify(notification: PhotoReportNotification)
}

data class PhotoReportNotification(
    val phase: String,
    val reporterId: Long,
    val groupId: Long,
    val photoId: Long,
    val reason: String,
)

class PhotoReportNotificationException : RuntimeException()
