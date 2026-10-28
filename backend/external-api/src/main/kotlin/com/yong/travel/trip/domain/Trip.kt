package com.yong.travel.trip.domain

import com.yong.travel.auth.domain.User
import com.yong.travel.group.domain.GroupRef
import java.time.Instant
import java.time.LocalDate

/** 여행 한 건. */
data class Trip(
    val id: Long,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val headcount: Int,
    val budget: Long?,
    val memo: String?,

    /** 커버를 지정하지 않았으면 null. */
    val coverPhotoUrl: String?,

    /** 삭제되지 않은 하위 기록 수. */
    val recordCount: Long,

    val owner: User,

    /** 공개 범위. 소유자에게만 채우고 그 외에는 null 이다. */
    val visibility: TripVisibility?,

    /** 공유 그룹. `GROUP` 여행의 소유자에게만 채우고 그 외에는 null 이다. */
    val sharedGroups: List<GroupRef>?,

    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /** 요청자가 소유자인지 확인한다. 비로그인(null)이면 false. */
    fun isOwnedBy(userId: Long?): Boolean = userId != null && owner.id == userId
}
