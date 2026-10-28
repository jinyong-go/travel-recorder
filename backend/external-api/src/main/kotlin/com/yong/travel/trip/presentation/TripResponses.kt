package com.yong.travel.trip.presentation

import com.yong.travel.auth.presentation.UserResponse
import com.yong.travel.group.presentation.GroupRefResponse
import com.yong.travel.trip.domain.Trip
import com.yong.travel.trip.domain.TripRef
import com.yong.travel.trip.domain.TripVisibility
import java.time.Instant
import java.time.LocalDate

/** 기록에 붙는 소속 여행의 id 와 이름. */
data class TripRefResponse(
    val id: Long,
    val name: String,
) {
    companion object {
        /** 여행 참조 → 응답. */
        fun from(trip: TripRef) = TripRefResponse(trip.id, trip.name)
    }
}

/**
 * 여행 상세. 공개 범위와 공유 그룹은 소유자에게만 채운다.
 * 예산은 여행을 볼 수 있으면 함께 보인다.
 */
data class TripResponse(
    val id: Long,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val headcount: Int,
    val budget: Long?,
    val memo: String?,

    /** 커버를 지정하지 않았으면 null. */
    val coverPhotoUrl: String?,

    /** 삭제되지 않은 기록 수. */
    val recordCount: Long,
    val owner: UserResponse,
    val isOwner: Boolean,
    val visibility: TripVisibility?,
    val sharedGroups: List<GroupRefResponse>?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        /**
         * 여행 → 응답.
         *
         * @param requesterId 비로그인이면 null
         */
        fun from(trip: Trip, requesterId: Long?) = TripResponse(
            id = trip.id,
            name = trip.name,
            startDate = trip.startDate,
            endDate = trip.endDate,
            headcount = trip.headcount,
            budget = trip.budget,
            memo = trip.memo,
            coverPhotoUrl = trip.coverPhotoUrl,
            recordCount = trip.recordCount,
            owner = UserResponse.from(trip.owner),
            isOwner = trip.isOwnedBy(requesterId),
            visibility = trip.visibility,
            sharedGroups = trip.sharedGroups?.map { GroupRefResponse.from(it) },
            createdAt = trip.createdAt,
            updatedAt = trip.updatedAt,
        )
    }
}

/** 여행 목록 항목. 상세에서 `updatedAt` 만 뺀다. */
data class TripSummaryResponse(
    val id: Long,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val headcount: Int,
    val budget: Long?,
    val memo: String?,
    val coverPhotoUrl: String?,
    val recordCount: Long,
    val owner: UserResponse,
    val isOwner: Boolean,
    val visibility: TripVisibility?,
    val sharedGroups: List<GroupRefResponse>?,
    val createdAt: Instant,
) {
    companion object {
        /**
         * 여행 → 응답.
         *
         * @param requesterId 비로그인이면 null
         */
        fun from(trip: Trip, requesterId: Long?) = TripSummaryResponse(
            id = trip.id,
            name = trip.name,
            startDate = trip.startDate,
            endDate = trip.endDate,
            headcount = trip.headcount,
            budget = trip.budget,
            memo = trip.memo,
            coverPhotoUrl = trip.coverPhotoUrl,
            recordCount = trip.recordCount,
            owner = UserResponse.from(trip.owner),
            isOwner = trip.isOwnedBy(requesterId),
            visibility = trip.visibility,
            sharedGroups = trip.sharedGroups?.map { GroupRefResponse.from(it) },
            createdAt = trip.createdAt,
        )
    }
}
