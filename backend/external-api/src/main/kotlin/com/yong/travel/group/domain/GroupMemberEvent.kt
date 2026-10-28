package com.yong.travel.group.domain

/** 그룹 멤버십이 시작되거나 끝난 사건. */
enum class GroupMemberEvent {
    /** 그룹을 만들거나 초대를 수락해 멤버가 됨 */
    JOINED,

    /** 멤버 본인이 탈퇴 */
    LEFT,

    /** 소유자가 제외 */
    REMOVED,

    /** 그룹이 삭제되어 멤버십이 끝남 */
    GROUP_DELETED,
}
