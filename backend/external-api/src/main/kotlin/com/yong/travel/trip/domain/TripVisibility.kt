package com.yong.travel.trip.domain

/** 여행 공개 범위. 기록은 소속 여행의 범위를 따른다. */
enum class TripVisibility {
    /** 소유자만 */
    PRIVATE,

    /** 지정한 그룹의 멤버까지 */
    GROUP,

    /** 누구나 (비로그인 포함) */
    PUBLIC,
}
