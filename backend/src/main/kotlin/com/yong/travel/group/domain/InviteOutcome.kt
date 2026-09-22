package com.yong.travel.group.domain

/**
 * 초대가 끝난 방식. 만료는 없다 — 초대에 수명이 없기 때문이다 (공통 명세 §3.7).
 *
 * 거절과 취소는 끝낸 주체가 다르다. 이름만으로는 갈리지 않으므로 값마다 적어 둔다.
 */
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
