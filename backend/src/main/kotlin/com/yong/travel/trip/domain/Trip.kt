package com.yong.travel.trip.domain

/**
 * 기록에 붙어 나가는 소속 여행 참조. 식별자와 이름까지다.
 *
 * 기간·인원·예산을 매 기록마다 반복해 싣지 않는다 — 필요하면 여행 상세를 따로 조회한다
 * (명세 §4.4.1).
 */
data class Trip(
    val id: Long,
    val name: String,
)
