package com.yong.travel.record.domain

import com.yong.travel.auth.domain.User
import com.yong.travel.photo.domain.Photo
import com.yong.travel.trip.domain.Trip
import java.time.Instant

/**
 * 기록 한 건. 서비스가 조회를 모두 끝낸 결과를 묶어 컨트롤러에 넘기는 경계다.
 *
 * **공개 범위와 공유 그룹은 담지 않는다** — 소유자에게도 마찬가지다. 그 값은 여행에 있고
 * 여행 상세로 내려간다 (명세 §4.4.1).
 */
data class RecordDetail(
    val id: Long,
    val trip: Trip,
    val name: String,
    val category: Category,

    /** 이름순으로 정렬해 담는다. 입력 순서는 의미가 없다. */
    val tags: List<String>,

    val address: String,
    val roadAddress: String?,
    val externalLink: String?,
    val latitude: Double,
    val longitude: Double,
    val rating: Double,
    val memo: String?,
    val photos: List<Photo>,

    /** 소속 여행의 소유자. 기록은 작성자 컬럼을 갖지 않는다 (명세 §3.1). */
    val author: User,

    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /** 요청자가 소속 여행의 소유자인지. 고칠 수 있는 사람은 그 한 명뿐이다 (명세 §3.1). */
    fun isAuthoredBy(userId: Long?): Boolean = userId != null && author.id == userId
}
