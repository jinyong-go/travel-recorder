package com.yong.travel.record.presentation

import com.yong.travel.auth.presentation.UserResponse
import com.yong.travel.photo.presentation.PhotoResponse
import com.yong.travel.record.domain.Category
import com.yong.travel.record.domain.TripRecord
import com.yong.travel.trip.presentation.TripRefResponse
import java.time.Instant

/**
 * 기록 상세.
 *
 * **공개 범위와 공유 그룹은 담지 않는다** — 소유자에게도 마찬가지다. 그 값은 여행에 있으므로
 * `GET /api/trips/{id}` 로 내려간다.
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
    /** `trip.owner` 를 그대로 옮긴 값이다. 기록은 작성자 컬럼을 갖지 않는다. */
    val author: UserResponse,

    /** 요청자가 `trip.owner` 인지. 필드 이름은 이전 판 그대로 유지한다. */
    val isAuthor: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        /**
         * 기록 상세 → 응답.
         *
         * `isAuthor` 는 요청자마다 달라지므로 도메인 객체가 아니라 여기서 정한다. 필드 이름은 이전 판
         * 그대로 유지한다.
         */
        fun from(record: TripRecord, requesterId: Long?) = TripRecordResponse(
            id = record.id,
            trip = TripRefResponse.from(record.trip),
            name = record.name,
            category = record.category,
            tags = record.tags,
            address = record.address,
            roadAddress = record.roadAddress,
            externalLink = record.externalLink,
            latitude = record.latitude,
            longitude = record.longitude,
            rating = record.rating,
            memo = record.memo,
            photos = record.photos.map { PhotoResponse.from(it) },
            author = UserResponse.from(record.author),
            isAuthor = record.isAuthoredBy(requesterId),
            createdAt = record.createdAt,
            updatedAt = record.updatedAt,
        )
    }
}

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
) {
    companion object {
        /** 기록 목록 항목 → 응답. `isAuthor` 를 여기서 정하는 이유는 [TripRecordResponse.from] 과 같다. */
        fun from(record: TripRecord, requesterId: Long?) = TripRecordSummaryResponse(
            id = record.id,
            trip = TripRefResponse.from(record.trip),
            name = record.name,
            category = record.category,
            tags = record.tags,
            address = record.address,
            latitude = record.latitude,
            longitude = record.longitude,
            rating = record.rating,
            memo = record.memo,
            thumbnailUrl = record.photos.firstOrNull()?.url,
            photoCount = record.photos.size.toLong(),
            author = UserResponse.from(record.author),
            isAuthor = record.isAuthoredBy(requesterId),
            distanceKm = record.distanceKm,
            createdAt = record.createdAt,
        )
    }
}
