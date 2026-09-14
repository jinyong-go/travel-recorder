package com.yong.travel.place.service

import com.yong.travel.common.dto.PageResponse
import com.yong.travel.place.dto.PlaceCreateRequest
import com.yong.travel.place.dto.PlaceListQuery
import com.yong.travel.place.dto.PlaceResponse
import com.yong.travel.place.dto.PlaceSummaryResponse
import com.yong.travel.place.dto.PlaceUpdateRequest
import org.springframework.data.domain.Pageable

interface PlaceService {
    fun list(query: PlaceListQuery, pageable: Pageable): PageResponse<PlaceSummaryResponse>
    fun get(placeId: Long): PlaceResponse
    fun create(ownerId: Long, request: PlaceCreateRequest): PlaceResponse
    fun update(placeId: Long, ownerId: Long, request: PlaceUpdateRequest): PlaceResponse
    fun delete(placeId: Long, ownerId: Long)
}
