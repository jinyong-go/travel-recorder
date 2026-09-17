package com.yong.travel.record.service

import com.yong.travel.common.dto.PageResponse
import com.yong.travel.record.dto.RecordListQuery
import com.yong.travel.record.dto.VisibilityUpdateRequest
import com.yong.travel.record.dto.VisitRecordCreateRequest
import com.yong.travel.record.dto.VisitRecordResponse
import com.yong.travel.record.dto.VisitRecordSummaryResponse
import com.yong.travel.record.dto.VisitRecordUpdateRequest
import org.springframework.data.domain.Pageable

interface VisitRecordService {
    fun list(query: RecordListQuery, userId: Long?, pageable: Pageable): PageResponse<VisitRecordSummaryResponse>

    /** 볼 권한이 없으면 없는 기록과 똑같이 RECORD_NOT_FOUND 로 응답한다 (존재 은닉). */
    fun get(recordId: Long, userId: Long?): VisitRecordResponse

    fun create(authorId: Long, request: VisitRecordCreateRequest): VisitRecordResponse
    fun update(recordId: Long, authorId: Long, request: VisitRecordUpdateRequest): VisitRecordResponse
    fun changeVisibility(recordId: Long, authorId: Long, request: VisibilityUpdateRequest): VisitRecordResponse
    fun delete(recordId: Long, authorId: Long)
}
