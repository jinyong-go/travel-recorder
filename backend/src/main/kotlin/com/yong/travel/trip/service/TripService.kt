package com.yong.travel.trip.service

import com.yong.travel.common.dto.PageResponse
import com.yong.travel.trip.dto.TripCoverUpdateRequest
import com.yong.travel.trip.dto.TripCreateRequest
import com.yong.travel.trip.dto.TripListQuery
import com.yong.travel.trip.dto.TripResponse
import com.yong.travel.trip.dto.TripSummaryResponse
import com.yong.travel.trip.dto.TripUpdateRequest
import com.yong.travel.trip.dto.TripVisibilityUpdateRequest
import org.springframework.data.domain.Pageable

interface TripService {
    fun list(query: TripListQuery, userId: Long?, pageable: Pageable): PageResponse<TripSummaryResponse>

    /** 볼 권한이 없으면 없는 여행과 똑같이 TRIP_NOT_FOUND 로 응답한다 (존재 은닉). */
    fun get(tripId: Long, userId: Long?): TripResponse

    fun create(ownerId: Long, request: TripCreateRequest): TripResponse

    /** 기본 정보만 바꾼다. 공개 범위는 changeVisibility 의 몫이다. */
    fun update(tripId: Long, ownerId: Long, request: TripUpdateRequest): TripResponse

    fun changeVisibility(tripId: Long, ownerId: Long, request: TripVisibilityUpdateRequest): TripResponse

    /**
     * 커버 사진 지정·해제. 그 여행의 하위 기록에 속한 사진만 지정할 수 있으며,
     * 아니면 PHOTO_NOT_FOUND 다 (존재 은닉, 명세 §4.3.2).
     */
    fun changeCover(tripId: Long, ownerId: Long, request: TripCoverUpdateRequest): TripResponse

    /** 하위 기록도 함께 soft delete 된다 (명세 §4.3). */
    fun delete(tripId: Long, ownerId: Long)
}
