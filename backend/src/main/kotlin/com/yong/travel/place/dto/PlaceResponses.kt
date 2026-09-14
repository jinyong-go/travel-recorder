package com.yong.travel.place.dto

import com.yong.travel.auth.dto.UserResponse
import com.yong.travel.photo.dto.PhotoResponse
import com.yong.travel.place.domain.Category
import java.time.Instant

data class RatingSummary(
    val average: Double,
    val count: Long,
)

data class PlaceResponse(
    val id: Long,
    val name: String,
    val category: Category,
    val tags: List<String>,
    val address: String,
    val roadAddress: String?,
    val externalLink: String?,
    val latitude: Double,
    val longitude: Double,
    val memo: String?,
    val photos: List<PhotoResponse>,
    val rating: RatingSummary,
    val owner: UserResponse,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class PlaceSummaryResponse(
    val id: Long,
    val name: String,
    val category: Category,
    val tags: List<String>,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val memo: String?,
    val thumbnailUrl: String?,
    val rating: RatingSummary,
    val distanceKm: Double?,
    val createdAt: Instant,
)
