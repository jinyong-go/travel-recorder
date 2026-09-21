package com.yong.travel.record.dto

import com.yong.travel.auth.dto.UserResponse
import com.yong.travel.photo.dto.PhotoResponse
import com.yong.travel.record.domain.Category
import com.yong.travel.trip.dto.TripRefResponse
import java.time.Instant

/**
 * 기록 상세.
 *
 * **공개 범위와 공유 그룹은 담지 않는다** — 소유자에게도 마찬가지다. 그 값은 여행에 있으므로
 * `GET /api/trips/{id}` 로 내려간다 (명세 §4.4.1).
 */
data class TripRecordResponse(
    val id: Long,
    val trip: TripRefResponse,
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
    val photos: List<PhotoResponse>,
    /** `trip.owner` 를 그대로 옮긴 값이다. 기록은 작성자 컬럼을 갖지 않는다 (명세 §3.1). */
    val author: UserResponse,

    /** 요청자가 `trip.owner` 인지. 필드 이름은 이전 판 그대로 유지한다 (명세 §4.4.1). */
    val isAuthor: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class TripRecordSummaryResponse(
    val id: Long,
    val trip: TripRefResponse,
    val name: String,
    val category: Category,
    val tags: List<String>,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val rating: Double,
    val memo: String?,
    val thumbnailUrl: String?,
    val photoCount: Long,
    val author: UserResponse,
    val isAuthor: Boolean,
    val distanceKm: Double?,
    val createdAt: Instant,
)
