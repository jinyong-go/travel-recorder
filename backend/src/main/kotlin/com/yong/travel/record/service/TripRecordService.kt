package com.yong.travel.record.service

import com.yong.travel.common.dto.PageResponse
import com.yong.travel.record.dto.RecordListQuery
import com.yong.travel.record.dto.TripChangeRequest
import com.yong.travel.record.dto.TripRecordCreateRequest
import com.yong.travel.record.dto.TripRecordResponse
import com.yong.travel.record.dto.TripRecordSummaryResponse
import com.yong.travel.record.dto.TripRecordUpdateRequest
import org.springframework.data.domain.Pageable

interface TripRecordService {
    fun list(query: RecordListQuery, userId: Long?, pageable: Pageable): PageResponse<TripRecordSummaryResponse>

    /** 볼 권한이 없으면 없는 기록과 똑같이 RECORD_NOT_FOUND 로 응답한다 (존재 은닉). */
    fun get(recordId: Long, userId: Long?): TripRecordResponse

    /** 소속 여행은 요청자가 소유한 것이어야 한다. 아니면 TRIP_NOT_FOUND 다. */
    fun create(authorId: Long, request: TripRecordCreateRequest): TripRecordResponse

    fun update(recordId: Long, authorId: Long, request: TripRecordUpdateRequest): TripRecordResponse

    /** 기록을 다른 여행으로 옮긴다. 공개 범위는 새 여행의 것을 따르게 된다 (명세 §4.4). */
    fun changeTrip(recordId: Long, authorId: Long, request: TripChangeRequest): TripRecordResponse

    fun delete(recordId: Long, authorId: Long)
}
