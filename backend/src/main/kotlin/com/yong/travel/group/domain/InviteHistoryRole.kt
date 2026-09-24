package com.yong.travel.group.domain

/**
 * 초대 이력을 어느 관점으로 볼지. 어느 쪽이든 본인이 당사자인 것만 조회된다 (명세 §4.8).
 *
 * 본문이 아니라 쿼리 파라미터(`?role=`)로 들어온다.
 */
enum class InviteHistoryRole { RECEIVED, SENT }
