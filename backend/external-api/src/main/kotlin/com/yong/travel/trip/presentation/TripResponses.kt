package com.yong.travel.trip.presentation

import com.yong.travel.auth.presentation.UserResponse
import com.yong.travel.group.presentation.GroupRefResponse
import com.yong.travel.trip.domain.Trip
import com.yong.travel.trip.domain.TripRef
import com.yong.travel.trip.domain.TripVisibility
import java.time.Instant
import java.time.LocalDate

/**
 * 기록에 붙어 나가는 소속 여행 참조.
 *
 * 식별자와 이름만 담는다. 여행의 기간·인원·예산까지 매 기록마다 반복해 내려주지 않으며,
 * 필요하면 `GET /api/trips/{id}` 로 조회한다.
 */
data class TripRefResponse(
    val id: Long,
    val name: String,
) {
    companion object {
        /** 소속 여행 참조 → 응답. */
        fun from(trip: TripRef) = TripRefResponse(trip.id, trip.name)
    }
}

/**
 * 여행 상세.
 *
 * `visibility` 와 `sharedGroups` 는 **소유자에게만** 채워진다 (그 외에는 null). 누구에게
 * 공유했는지는 열람자에게 알릴 이유가 없기 때문이다.
 *
 * **`budget` 은 제외하지 않는다.** 예산은 공개 범위를 그대로 따르는 값이라, 여행을 볼 수 있는
 * 사람은 예산도 볼 수 있다.
 */
data class TripResponse(
    val id: Long,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val headcount: Int,
    val budget: Long?,
    val memo: String?,

    /** 커버가 지정되지 않았으면 null 이다. 서버는 대체 이미지를 고르지 않는다. */
    val coverPhotoUrl: String?,

    /** 삭제되지 않은 하위 기록 수. 0 일 수 있다. */
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
         * @param requesterId 요청자의 사용자 id. 비로그인이면 null
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

/** 여행 목록 항목. 상세에서 `updatedAt` 만 빠진다. 카드가 설명을 두 줄까지 보여주므로 `memo` 를 담는다. */
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
         * 여행 목록 항목 → 응답.
         *
         * @param requesterId 요청자의 사용자 id. 비로그인이면 null
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
