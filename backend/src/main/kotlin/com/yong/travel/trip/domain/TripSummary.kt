package com.yong.travel.trip.domain

import com.yong.travel.auth.domain.User
import com.yong.travel.group.domain.Group
import java.time.Instant
import java.time.LocalDate

/** 여행 목록의 한 줄. [TripDetail] 에서 `updatedAt` 만 빠진다. */
data class TripSummary(
    val id: Long,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val headcount: Int,
    val budget: Long?,
    val memo: String?,
    val coverPhotoUrl: String?,
    val recordCount: Long,
    val owner: User,

    /** 소유자 본인의 조회에서만 채운다. 이유는 [TripDetail.visibility] 와 같다. */
    val visibility: Visibility?,
    val sharedGroups: List<Group>?,

    val createdAt: Instant,
) {
    fun isOwnedBy(userId: Long?): Boolean = userId != null && owner.id == userId
}
