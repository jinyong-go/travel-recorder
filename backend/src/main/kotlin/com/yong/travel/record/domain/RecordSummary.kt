package com.yong.travel.record.domain

import com.yong.travel.auth.domain.User
import com.yong.travel.trip.domain.Trip
import java.time.Instant

/** 기록 목록의 한 줄. 사진은 첫 장의 URL 과 개수까지만 든다. */
data class RecordSummary(
    val id: Long,
    val trip: Trip,
    val name: String,
    val category: Category,
    val tags: List<String>,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val rating: Double,
    val memo: String?,
    val thumbnailUrl: String?,
    val photoCount: Long,
    val author: User,

    /**
     * 요청자의 기준 좌표로부터의 거리(km). 좌표가 오지 않으면 null 이다.
     *
     * 저장된 값이 아니라 요청마다 계산되는 값이다 — 기준 좌표는 계산에만 쓰고 저장하지 않는다
     * (공통 명세 §5).
     */
    val distanceKm: Double?,

    val createdAt: Instant,
) {
    fun isAuthoredBy(userId: Long?): Boolean = userId != null && author.id == userId
}
