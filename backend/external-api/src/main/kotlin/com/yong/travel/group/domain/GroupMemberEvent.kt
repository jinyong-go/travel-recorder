package com.yong.travel.group.domain

/** 그룹 멤버십이 시작되거나 끝난 사건. 행위자는 사건마다 하나로 정해져 따로 적지 않는다. */
enum class GroupMemberEvent {
    /** 그룹을 만든 소유자, 또는 초대를 수락한 사람이 멤버가 됨 */
    JOINED,

    /** 멤버 본인이 탈퇴 */
    LEFT,

    /** 소유자가 제외 */
    REMOVED,

    /** 그룹이 삭제되어 남아 있던 멤버 전원의 멤버십이 끝남 */
    GROUP_DELETED,
}
