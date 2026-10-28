package com.yong.travel.trip.domain

import java.time.LocalDate

/** 여행 생성 입력. */
data class TripCreateCommand(
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val headcount: Int,
    val budget: Long?,
    val memo: String?,
    val visibility: TripVisibility,
    val groupIds: List<Long>,
)

/** 여행 기본 정보 수정 입력. 공개 범위는 따로 바꾼다. */
data class TripUpdateCommand(
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val headcount: Int,
    val budget: Long?,
    val memo: String?,
)
