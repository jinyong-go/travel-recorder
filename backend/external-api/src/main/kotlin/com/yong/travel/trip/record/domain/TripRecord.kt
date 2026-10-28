package com.yong.travel.trip.record.domain

import com.yong.travel.auth.domain.User
import com.yong.travel.photo.domain.Photo
import com.yong.travel.trip.domain.TripRef
import java.time.Instant

/** 기록 한 건. */
data class TripRecord(
    val id: Long,
    val trip: TripRef,
    val name: String,
    val category: Category,

    /** 이름순 태그. */
    val tags: List<String>,

    val address: String,
    val roadAddress: String?,
    val externalLink: String?,
    val latitude: Double,
    val longitude: Double,
    val rating: Double,
    val memo: String?,
    val photos: List<Photo>,

    /** 소속 여행의 소유자. */
    val author: User,

    /** 기준 위치로부터의 거리(km). 목록에서 기준 위치가 왔을 때만 채운다. */
    val distanceKm: Double?,

    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /** 요청자가 소속 여행의 소유자인지 확인한다. 비로그인(null)이면 false. */
    fun isAuthoredBy(userId: Long?): Boolean = userId != null && author.id == userId
}
