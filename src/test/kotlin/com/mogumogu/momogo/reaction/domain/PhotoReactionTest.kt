package com.mogumogu.momogo.reaction.domain

import com.mogumogu.momogo.group.domain.Group
import com.mogumogu.momogo.group.domain.InviteCode
import com.mogumogu.momogo.photo.domain.Photo
import com.mogumogu.momogo.photo.domain.PhotoGroup
import com.mogumogu.momogo.user.domain.User
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class PhotoReactionTest : BehaviorSpec({

    given("그룹에 올라가 있는 사진이 있으면") {
        `when`("리액션을 남길 때") {
            then("리액션 정보를 읽기 전용으로 조회할 수 있다") {
                val photoGroup = createPhotoGroup()
                val reactor = User(_nickname = "모모")
                val photoReaction = PhotoReaction(
                    _id = 1L,
                    _photoGroup = photoGroup,
                    _user = reactor,
                    _concept = ReactionConcept.YOUNG_CREATOR_CREW,
                    _emoji = Emoji.DELICIOUS,
                    _comment = "야르~",
                )

                photoReaction.id shouldBe 1L
                photoReaction.photoGroup shouldBe photoGroup
                photoReaction.user shouldBe reactor
                photoReaction.concept shouldBe ReactionConcept.YOUNG_CREATOR_CREW
                photoReaction.emoji shouldBe Emoji.DELICIOUS
                photoReaction.comment shouldBe "야르~"
            }
        }

        `when`("허용되는 경계값으로 리액션을 남길 때") {
            then("문구 30자를 허용한다") {
                val photoReaction = createPhotoReaction(_comment = "맛".repeat(30))

                photoReaction.comment.length shouldBe 30
            }
        }
    }

    given("유효하지 않은 리액션 정보가 있으면") {
        `when`("문구가 비어 있을 때") {
            then("리액션 생성을 거부한다") {
                listOf("", " ", "\t").forEach { comment ->
                    shouldThrow<IllegalArgumentException> {
                        createPhotoReaction(_comment = comment)
                    }
                }
            }
        }

        `when`("문구가 30자를 초과할 때") {
            then("리액션 생성을 거부한다") {
                shouldThrow<IllegalArgumentException> {
                    createPhotoReaction(_comment = "맛".repeat(31))
                }
            }
        }
    }

    given("그룹에서 내린 사진이 있으면") {
        `when`("리액션을 남길 때") {
            then("리액션 생성을 거부한다") {
                val unlinkedPhotoGroup = createPhotoGroup()
                unlinkedPhotoGroup.unlink(Instant.parse("2030-01-01T00:00:00Z"))

                shouldThrow<IllegalStateException> {
                    createPhotoReaction(_photoGroup = unlinkedPhotoGroup)
                }
            }
        }
    }
})

private fun createPhotoGroup(): PhotoGroup =
    PhotoGroup(
        _photo = Photo(
            _uploader = User(_nickname = "모고"),
            _objectKey = "photos/reaction.jpg",
            _sizeBytes = 1_024L,
            _contentType = "image/jpeg",
        ),
        _group = Group(
            _name = "리액션 그룹",
            _inviteCode = InviteCode(_value = "RCT001"),
        ),
    )

private fun createPhotoReaction(
    _photoGroup: PhotoGroup = createPhotoGroup(),
    _comment: String = "야르~",
): PhotoReaction =
    PhotoReaction(
        _photoGroup = _photoGroup,
        _user = User(_nickname = "모모"),
        _concept = ReactionConcept.YOUNG_CREATOR_CREW,
        _emoji = Emoji.DELICIOUS,
        _comment = _comment,
    )
