package com.yong.travel.trip.record.presentation

import com.yong.travel.auth.presentation.UserResponse
import com.yong.travel.photo.presentation.PhotoResponse
import com.yong.travel.trip.presentation.TripRefResponse
import com.yong.travel.trip.record.domain.Category
import com.yong.travel.trip.record.domain.TripRecord
import java.time.Instant

/** 기록 상세. 공개 범위는 여행에 있으므로 담지 않는다. */
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
    /** 소속 여행의 소유자. */
    val author: UserResponse,

    /** 요청자가 소속 여행의 소유자인지. */
    val isAuthor: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        /**
         * 기록 → 응답.
         *
         * @param requesterId 비로그인이면 null
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

/** 기록 목록 항목. 사진 목록 대신 썸네일과 사진 수를 담는다. */
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
        /**
         * 기록 → 응답.
         *
         * @param requesterId 비로그인이면 null
         */
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
