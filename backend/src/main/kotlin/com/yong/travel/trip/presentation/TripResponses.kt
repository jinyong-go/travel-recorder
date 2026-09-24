package com.yong.travel.trip.presentation

import com.yong.travel.auth.presentation.UserResponse
import com.yong.travel.auth.presentation.toResponse
import com.yong.travel.group.domain.Group
import com.yong.travel.trip.domain.TripDetail
import com.yong.travel.trip.domain.TripSummary
import com.yong.travel.trip.domain.Visibility
import java.time.Instant
import java.time.LocalDate

/**
 * 기록에 붙어 나가는 소속 여행 참조.
 *
 * 식별자와 이름만 담는다. 여행의 기간·인원·예산까지 매 기록마다 반복해 내려주지 않으며,
 * 필요하면 `GET /api/trips/{id}` 로 조회한다 (명세 §4.4.1).
 */
data class TripRefResponse(
    val id: Long,
    val name: String,
)

/**
 * 여행 응답에 담기는 공유 그룹.
 *
 * 그룹 목록 화면의 `GroupSummaryResponse` 와 달리 멤버 수·정원을 담지 않는다. 여행 응답에
 * 그룹의 인원 사정까지 실을 이유가 없다 (명세 §4.3.1).
 */
data class SharedGroupResponse(
    val id: Long,
    val name: String,
)

/**
 * 여행 상세.
 *
 * `visibility` 와 `sharedGroups` 는 **소유자에게만** 채워진다 (그 외에는 null). 누구에게
 * 공유했는지는 열람자에게 알릴 이유가 없기 때문이다.
 *
 * **`budget` 은 제외하지 않는다.** 예산은 공개 범위를 그대로 따르는 값이라, 여행을 볼 수 있는
 * 사람은 예산도 볼 수 있다 (명세 §4.3.1, 공통 명세 §3.5 노출 표).
 */
data class TripResponse(
    val id: Long,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val headcount: Int,
    val budget: Long?,
    val memo: String?,

    /** 커버가 지정되지 않았으면 null 이다. 서버는 대체 이미지를 고르지 않는다 (명세 §4.3.1). */
    val coverPhotoUrl: String?,

    /** 삭제되지 않은 하위 기록 수. 0 일 수 있다 (명세 §3.1). */
    val recordCount: Long,
    val owner: UserResponse,
    val isOwner: Boolean,
    val visibility: Visibility?,
    val sharedGroups: List<SharedGroupResponse>?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

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
    val visibility: Visibility?,
    val sharedGroups: List<SharedGroupResponse>?,
    val createdAt: Instant,
)

private fun Group.toSharedResponse() = SharedGroupResponse(id, name)

/**
 * 여행 상세 → 응답.
 *
 * `isOwner` 는 요청자마다 달라지므로 도메인 객체가 아니라 여기서 정한다. `visibility` 와
 * `sharedGroups` 는 서비스가 소유자에게만 채워 주므로 그대로 옮긴다.
 */
fun TripDetail.toResponse(requesterId: Long?): TripResponse =
    TripResponse(
        id = id,
        name = name,
        startDate = startDate,
        endDate = endDate,
        headcount = headcount,
        budget = budget,
        memo = memo,
        coverPhotoUrl = coverPhotoUrl,
        recordCount = recordCount,
        owner = owner.toResponse(),
        isOwner = isOwnedBy(requesterId),
        visibility = visibility,
        sharedGroups = sharedGroups?.map { it.toSharedResponse() },
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

/** 여행 목록 항목 → 응답. `isOwner` 를 여기서 정하는 이유는 [toResponse] 와 같다. */
fun TripSummary.toSummaryResponse(requesterId: Long?): TripSummaryResponse =
    TripSummaryResponse(
        id = id,
        name = name,
        startDate = startDate,
        endDate = endDate,
        headcount = headcount,
        budget = budget,
        memo = memo,
        coverPhotoUrl = coverPhotoUrl,
        recordCount = recordCount,
        owner = owner.toResponse(),
        isOwner = isOwnedBy(requesterId),
        visibility = visibility,
        sharedGroups = sharedGroups?.map { it.toSharedResponse() },
        createdAt = createdAt,
    )
