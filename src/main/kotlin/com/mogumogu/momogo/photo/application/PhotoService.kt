package com.mogumogu.momogo.photo.application

import com.mogumogu.momogo.global.config.ApplicationPhase
import com.mogumogu.momogo.global.error.ApiException
import com.mogumogu.momogo.global.error.ErrorCode
import com.mogumogu.momogo.global.time.DailyTimeRangeFactory
import com.mogumogu.momogo.group.domain.Group
import com.mogumogu.momogo.group.infra.GroupMemberRepository
import com.mogumogu.momogo.group.infra.GroupRepository
import com.mogumogu.momogo.photo.domain.Photo
import com.mogumogu.momogo.photo.domain.PhotoGroup
import com.mogumogu.momogo.photo.domain.PhotoObjectKey
import com.mogumogu.momogo.photo.infra.PhotoGroupRepository
import com.mogumogu.momogo.photo.infra.PhotoRepository
import com.mogumogu.momogo.user.domain.User
import com.mogumogu.momogo.user.infra.UserRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class PhotoService(
    private val photoRepository: PhotoRepository,
    private val photoGroupRepository: PhotoGroupRepository,
    private val userRepository: UserRepository,
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val objectMetadataReader: PhotoObjectMetadataReader,
    private val applicationPhase: ApplicationPhase,
    private val dailyTimeRangeFactory: DailyTimeRangeFactory,
    private val clock: Clock,
) {

    @Transactional
    fun create(command: CreatePhotoCommand): CreatePhotoResult {
        val groupIds = normalizeGroupIds(command.groupIds)
        val objectKey = parseObjectKey(
            value = command.objectKey,
            userId = command.userId,
        )
        val objectMetadata = objectMetadataReader.find(objectKey)
            ?: throw ApiException.UnprocessableEntity(ErrorCode.OBJECT_NOT_UPLOADED)
        val uploader = userRepository.findByIdForUpdate(command.userId)
            ?: throw ApiException.NotFound(ErrorCode.USER_NOT_FOUND)
        val groups = findJoinedGroupsForUpdate(
            userId = command.userId,
            groupIds = groupIds,
        )
        val timeRange = dailyTimeRangeFactory.today()
        if (
            photoGroupRepository.existsUploadByUserIdAndGroupIdsAndCreatedAtRange(
                userId = command.userId,
                groupIds = groupIds,
                startAt = timeRange.startAt,
                endAt = timeRange.endAt,
            )
        ) {
            throw ApiException.Conflict(ErrorCode.DAILY_GROUP_UPLOAD_LIMIT_EXCEEDED)
        }

        val photo = try {
            photoRepository.saveAndFlush(
                createPhoto(
                    uploader = uploader,
                    objectKey = objectKey,
                    objectMetadata = objectMetadata,
                ),
            )
        } catch (_: DataIntegrityViolationException) {
            throw ApiException.Conflict(ErrorCode.PHOTO_ALREADY_REGISTERED)
        }
        photoGroupRepository.saveAll(
            groups.map { group -> PhotoGroup(_photo = photo, _group = group) },
        )

        return CreatePhotoResult(
            photoId = checkNotNull(photo.id) { "저장된 사진 ID가 없습니다." },
            objectKey = photo.objectKey,
            createdAt = LocalDateTime.ofInstant(photo.createdAt, clock.zone),
        )
    }

    @Transactional
    fun unlink(command: UnlinkPhotoCommand) {
        if (command.groupId <= 0 || command.photoId <= 0) {
            throw ApiException.BadRequest(ErrorCode.INVALID_REQUEST)
        }

        findJoinedGroupsForUpdate(
            userId = command.userId,
            groupIds = listOf(command.groupId),
        )
        val photoGroup = photoGroupRepository.findActiveByPhotoIdAndGroupId(
            photoId = command.photoId,
            groupId = command.groupId,
        ) ?: throw ApiException.NotFound(ErrorCode.PHOTO_NOT_FOUND)
        if (photoGroup.photo.uploader?.id != command.userId) {
            throw ApiException.Forbidden(ErrorCode.FORBIDDEN)
        }

        photoGroup.unlink(clock.instant())
    }

    private fun normalizeGroupIds(groupIds: List<Long>): List<Long> {
        if (
            groupIds.isEmpty() ||
            groupIds.any { groupId -> groupId <= 0 } ||
            groupIds.distinct().size != groupIds.size
        ) {
            throw ApiException.BadRequest(ErrorCode.INVALID_REQUEST)
        }
        return groupIds.sorted()
    }

    private fun parseObjectKey(
        value: String,
        userId: Long,
    ): PhotoObjectKey {
        val objectKey = try {
            PhotoObjectKey.parse(value)
        } catch (_: IllegalArgumentException) {
            throw ApiException.BadRequest(ErrorCode.INVALID_OBJECT_KEY)
        }
        if (!objectKey.belongsTo(applicationPhase.value, userId)) {
            throw ApiException.BadRequest(ErrorCode.INVALID_OBJECT_KEY)
        }
        return objectKey
    }

    private fun createPhoto(
        uploader: User,
        objectKey: PhotoObjectKey,
        objectMetadata: PhotoObjectMetadata,
    ): Photo =
        try {
            Photo(
                _uploader = uploader,
                _objectKey = objectKey.value,
                _sizeBytes = objectMetadata.sizeBytes,
                _contentType = objectMetadata.contentTypeValue.orEmpty(),
            )
        } catch (_: IllegalArgumentException) {
            throw ApiException.UnprocessableEntity(ErrorCode.OBJECT_NOT_UPLOADED)
        }

    private fun findJoinedGroupsForUpdate(
        userId: Long,
        groupIds: List<Long>,
    ): List<Group> {
        // 그룹 행 락이 일일 업로드 제한 검사와 저장을 직렬화한다. 제한이 그룹 단위이므로 제한을 위반할 수 있는
        // 요청끼리는 반드시 그룹을 공유하고, 그 그룹 행에서 직렬화된다. 비잠금 조회로 바꾸면 동시 요청이 제한을
        // 우회한다. groupIds는 코드베이스 공통 락 순서(user -> group 오름차순)를 맞추기 위해 정렬된 상태로 받는다.
        val groups = groupIds.map { groupId ->
            groupRepository.findActiveByIdForUpdate(groupId)
                ?: throw ApiException.Forbidden(ErrorCode.NOT_GROUP_MEMBER)
        }
        val joinedGroupIds = groupMemberRepository.findJoinedGroupIdsByUserIdAndGroupIds(
            userId = userId,
            groupIds = groupIds,
        ).toSet()
        if (joinedGroupIds != groupIds.toSet()) {
            throw ApiException.Forbidden(ErrorCode.NOT_GROUP_MEMBER)
        }
        return groups
    }
}

data class CreatePhotoCommand(
    val userId: Long,
    val objectKey: String,
    val groupIds: List<Long>,
)

data class CreatePhotoResult(
    val photoId: Long,
    val objectKey: String,
    val createdAt: LocalDateTime,
)

data class UnlinkPhotoCommand(
    val userId: Long,
    val groupId: Long,
    val photoId: Long,
)
