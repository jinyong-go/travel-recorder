package com.yong.travel.trip.record.domain

import com.yong.travel.auth.domain.User
import com.yong.travel.photo.domain.Photo
import com.yong.travel.trip.domain.TripRef
import java.time.Instant

/**
 * 기록 한 건. 서비스가 조회를 모두 끝낸 결과를 묶어 컨트롤러에 넘기는 경계다.
 *
 * 목록과 상세가 함께 쓴다. 목록 응답에서 빠지는 값(도로명 주소·외부 링크·사진 목록·updatedAt)을
 * 걸러 내고 썸네일·사진 수를 계산하는 것은 응답 DTO 의 몫이다.
 *
 * **공개 범위와 공유 그룹은 담지 않는다** — 소유자에게도 마찬가지다. 그 값은 여행에 있고
 * 여행 상세로 내려간다.
 */
data class TripRecord(
    val id: Long,
    val trip: TripRef,
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

    /** 소속 여행의 소유자. 기록은 작성자 컬럼을 갖지 않는다. */
    val author: User,

    /**
     * 요청자의 기준 좌표로부터의 거리(km). **목록에서 좌표가 왔을 때만 채운다** (그 외에는 null).
     *
     * 저장된 값이 아니라 요청마다 계산되는 값이다 — 기준 좌표는 계산에만 쓰고 저장하지 않는다.
     */
    val distanceKm: Double?,

    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /** 요청자가 소속 여행의 소유자인지. 고칠 수 있는 사람은 그 한 명뿐이다. */
    fun isAuthoredBy(userId: Long?): Boolean = userId != null && author.id == userId
}
