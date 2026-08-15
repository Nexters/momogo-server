package com.mogumogu.momogo.event.domain

enum class ServiceEventType(
    val emoji: String,
    val title: String,
    val color: Int,
) {
    USER_REGISTERED("🎉", "회원 가입", 3_066_993),
    USER_WITHDRAWN("👋", "회원 탈퇴", 9_807_270),
    GROUP_CREATED("✨", "그룹 생성", 3_447_003),
    GROUP_JOINED("🤝", "그룹 참여", 3_447_003),
    GROUP_DELETED("🌙", "그룹 소멸", 9_807_270),
}

// 운영 알림 전용 이벤트다. 개인정보가 외부 서비스에 남지 않도록 닉네임이나 그룹명 같은 사용자 입력은 담지 않는다.
data class ServiceEvent(
    val type: ServiceEventType,
    val userId: Long? = null,
    val groupId: Long? = null,
    // 탈퇴한 사용자는 행이 삭제되므로 가입 이력 총합이 아니라 현재 가입자 수다.
    val totalUserCount: Long? = null,
)
