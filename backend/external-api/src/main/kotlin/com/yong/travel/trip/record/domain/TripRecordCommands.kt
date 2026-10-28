package com.yong.travel.trip.record.domain

/** 기록 등록 입력. */
data class TripRecordCreateCommand(
    val tripId: Long,
    val name: String,
    val category: Category,
    val tags: List<String>,
    val address: String,
    val roadAddress: String?,
    val externalLink: String?,
    val latitude: Double,
    val longitude: Double,
    val rating: Double,
    val memo: String?,
)

/** 기록 수정 입력. 여행 이동은 따로 한다. */
data class TripRecordUpdateCommand(
    val name: String,
    val category: Category,
    val tags: List<String>,
    val address: String,
    val roadAddress: String?,
    val externalLink: String?,
    val latitude: Double,
    val longitude: Double,
    val rating: Double,
    val memo: String?,
)
