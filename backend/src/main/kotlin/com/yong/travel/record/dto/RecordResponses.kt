package com.yong.travel.record.dto

import com.yong.travel.auth.dto.UserResponse
import com.yong.travel.group.dto.GroupSummaryResponse
import com.yong.travel.photo.dto.PhotoResponse
import com.yong.travel.record.domain.Category
import com.yong.travel.record.domain.Visibility
import java.time.Instant

/**
 * 기록 상세.
 *
 * `visibility` 와 `sharedGroups` 는 작성자에게만 채워진다 (그 외에는 null). 열람자에게
 * "이 기록이 어느 그룹에 공유되었는지" 를 알릴 이유가 없기 때문이다.
 */
data class VisitRecordResponse(
    val id: Long,
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
    val author: UserResponse,
    val isAuthor: Boolean,
    val visibility: Visibility?,
    val sharedGroups: List<GroupSummaryResponse>?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class VisitRecordSummaryResponse(
    val id: Long,
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
    val visibility: Visibility?,
    val distanceKm: Double?,
    val createdAt: Instant,
)
