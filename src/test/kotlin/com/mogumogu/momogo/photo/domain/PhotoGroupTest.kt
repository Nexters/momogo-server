package com.mogumogu.momogo.photo.domain

import com.mogumogu.momogo.group.domain.Group
import com.mogumogu.momogo.group.domain.InviteCode
import com.mogumogu.momogo.user.domain.User
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class PhotoGroupTest : BehaviorSpec({

    given("그룹에 연결된 사진이 있으면") {
        `when`("사진 그룹 연결을 생성할 때") {
            then("사진과 그룹 정보를 조회할 수 있고 활성 상태다") {
                val photo = createPhotoForGroup()
                val group = createGroupForPhoto()
                val photoGroup = PhotoGroup(
                    _id = 1L,
                    _photo = photo,
                    _group = group,
                )

                photoGroup.id shouldBe 1L
                photoGroup.photo shouldBe photo
                photoGroup.group shouldBe group
                photoGroup.deletedAt shouldBe null
                photoGroup.isActive() shouldBe true
            }
        }

        `when`("그룹 연결을 해제할 때") {
            then("해제 시각을 기록하고 비활성 상태가 된다") {
                val photoGroup = createPhotoGroup()
                val deletedAt = Instant.parse("2030-01-01T00:00:00Z")

                photoGroup.unlink(deletedAt)

                photoGroup.deletedAt shouldBe deletedAt
                photoGroup.isActive() shouldBe false
            }
        }
    }

    given("이미 해제된 사진 그룹 연결이 있으면") {
        `when`("다시 연결을 해제할 때") {
            then("최초 해제 시각을 유지한다") {
                val photoGroup = createPhotoGroup()
                val firstDeletedAt = Instant.parse("2030-01-01T00:00:00Z")

                photoGroup.unlink(firstDeletedAt)
                photoGroup.unlink(firstDeletedAt.plusSeconds(1))

                photoGroup.deletedAt shouldBe firstDeletedAt
                photoGroup.isActive() shouldBe false
            }
        }
    }
})

private fun createPhotoGroup(): PhotoGroup =
    PhotoGroup(
        _photo = createPhotoForGroup(),
        _group = createGroupForPhoto(),
    )

private fun createPhotoForGroup(): Photo =
    Photo(
        _uploader = User(_nickname = "모고"),
        _objectKey = "photos/photo.jpg",
        _sizeBytes = 1_024L,
        _contentType = "image/jpeg",
    )

private fun createGroupForPhoto(): Group =
    Group(
        _name = "사진 그룹",
        _inviteCode = InviteCode(_value = "PHOTO1"),
    )
