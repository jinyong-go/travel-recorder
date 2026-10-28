package com.yong.travel.trip.domain

/** 여행 공개 범위가 바뀐 원인. 그룹 쪽 사건으로 공유가 줄어든 경우도 포함한다. */
enum class TripVisibilityChangeCause {
    /** 소유자가 여행을 만들거나 공개 범위를 바꿈 */
    OWNER_CHANGE,

    /** 공유 그룹이 삭제됨 */
    GROUP_DELETED,

    /** 소유자가 공유 그룹에서 탈퇴함 */
    OWNER_LEFT,

    /** 소유자가 공유 그룹에서 제외됨 */
    OWNER_REMOVED,
}
