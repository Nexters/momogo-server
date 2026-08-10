package com.mogumogu.momogo.report.application

import com.mogumogu.momogo.global.config.ApplicationPhase
import com.mogumogu.momogo.global.error.ApiException
import com.mogumogu.momogo.global.error.ErrorCode
import com.mogumogu.momogo.group.infra.GroupMemberRepository
import com.mogumogu.momogo.photo.infra.PhotoGroupRepository
import org.springframework.stereotype.Service

@Service
class PhotoReportService(
    private val groupMemberRepository: GroupMemberRepository,
    private val photoGroupRepository: PhotoGroupRepository,
    private val photoReportNotifier: PhotoReportNotifier,
    private val applicationPhase: ApplicationPhase,
) {

    fun report(command: PhotoReportCommand) {
        validateIds(command)
        val reason = normalizeReason(command.reason)
        ensureJoinedGroup(
            userId = command.reporterId,
            groupId = command.groupId,
        )
        if (
            photoGroupRepository.findActiveByPhotoIdAndGroupId(
                photoId = command.photoId,
                groupId = command.groupId,
            ) == null
        ) {
            throw ApiException.NotFound(ErrorCode.PHOTO_NOT_FOUND)
        }

        try {
            photoReportNotifier.notify(
                PhotoReportNotification(
                    phase = applicationPhase.value,
                    reporterId = command.reporterId,
                    groupId = command.groupId,
                    photoId = command.photoId,
                    reason = reason,
                ),
            )
        } catch (_: PhotoReportNotificationException) {
            throw ApiException.InternalServerError(ErrorCode.PHOTO_REPORT_NOTIFICATION_FAILED)
        }
    }

    private fun validateIds(command: PhotoReportCommand) {
        if (command.groupId <= 0 || command.photoId <= 0) {
            throw ApiException.BadRequest(ErrorCode.INVALID_REQUEST)
        }
    }

    private fun normalizeReason(reason: String): String {
        val normalizedReason = reason.trim()
        if (normalizedReason.isEmpty() || normalizedReason.length > PhotoReportCommand.REASON_MAX_LENGTH) {
            throw ApiException.BadRequest(ErrorCode.INVALID_REQUEST)
        }
        return normalizedReason
    }

    // 그룹 멤버가 아닌 사용자에게 사진의 존재 여부를 알려주지 않도록 멤버십을 먼저 확인한다.
    private fun ensureJoinedGroup(
        userId: Long,
        groupId: Long,
    ) {
        if (
            groupMemberRepository.findJoinedGroupIdsByUserIdAndGroupIds(
                userId = userId,
                groupIds = listOf(groupId),
            ).isEmpty()
        ) {
            throw ApiException.Forbidden(ErrorCode.NOT_GROUP_MEMBER)
        }
    }
}

data class PhotoReportCommand(
    val reporterId: Long,
    val groupId: Long,
    val photoId: Long,
    val reason: String,
) {
    companion object {
        const val REASON_MAX_LENGTH = 500
    }
}
