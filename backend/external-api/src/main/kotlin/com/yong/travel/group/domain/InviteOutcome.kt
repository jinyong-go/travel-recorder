package com.yong.travel.group.domain

/** 초대가 끝난 방식. 초대에는 만료가 없다. */
enum class InviteOutcome {
    /** 받은 쪽이 수락 */
    ACCEPTED,

    /** 받은 쪽이 거절 */
    REJECTED,

    /** 보낸 쪽이 철회 */
    REVOKED,

    /** 그룹이 삭제되어 끝남 */
    GROUP_DELETED,
}
