package com.yong.travel.trip.domain

/**
 * 여행의 공개 범위. 기본값은 PRIVATE 이며, 소유자가 명시적으로 넓히지 않는 한 공개되지 않는다.
 *
 * 이 값은 여행에만 존재한다. 하위 기록은 범위를 갖지 않고 소속 여행의 값을 그대로 따르므로,
 * 여행 하나를 바꾸면 하위 기록 전체의 노출이 함께 바뀐다 (명세 §3.1).
 */
enum class Visibility {
    /** 소유자만 */
    PRIVATE,

    /** 지정한 그룹의 멤버까지 */
    GROUP,

    /** 누구나 (비로그인 포함) */
    PUBLIC,
}
