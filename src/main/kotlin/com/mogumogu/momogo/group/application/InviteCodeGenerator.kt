package com.mogumogu.momogo.group.application

import com.mogumogu.momogo.group.domain.InviteCode
import com.mogumogu.momogo.group.infra.GroupRepository
import org.springframework.stereotype.Component

@Component
class InviteCodeGenerator(
    private val groupRepository: GroupRepository,
) {

    fun generate(): InviteCode = generate { InviteCode.generate() }

    internal fun generate(generateCandidate: () -> InviteCode): InviteCode {
        repeat(MAX_GENERATION_ATTEMPTS) {
            val inviteCode = generateCandidate()
            if (groupRepository.findByInviteCode(inviteCode) == null) {
                return inviteCode
            }
        }

        error("사용 가능한 초대 코드를 생성하지 못했습니다.")
    }

    private companion object {
        const val MAX_GENERATION_ATTEMPTS = 10
    }
}
