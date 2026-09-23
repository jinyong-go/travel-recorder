package com.yong.travel.record.controller

import com.yong.travel.auth.security.LoginUser
import com.yong.travel.common.dto.PageResponse
import com.yong.travel.common.web.listPageRequest
import com.yong.travel.common.web.requireLogin
import com.yong.travel.record.domain.Category
import com.yong.travel.record.dto.RecordListQuery
import com.yong.travel.record.dto.RecordScope
import com.yong.travel.record.dto.RecordSort
import com.yong.travel.record.dto.TripChangeRequest
import com.yong.travel.record.dto.TripRecordCreateRequest
import com.yong.travel.record.dto.TripRecordResponse
import com.yong.travel.record.dto.TripRecordSummaryResponse
import com.yong.travel.record.dto.TripRecordUpdateRequest
import com.yong.travel.record.dto.toResponse
import com.yong.travel.record.dto.toSummaryResponse
import com.yong.travel.record.service.TripRecordService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/records")
class TripRecordController(
    private val recordService: TripRecordService,
) {

    /**
     * 기록 목록 조회.
     *
     * `scope` 는 필수다. "무엇을 보는 목록인지" 가 화면마다 다르고, 기본값을 두면
     * 실수로 넓은 범위를 조회하는 쪽이 조용히 기본이 되기 때문이다.
     * MINE/SHARED 는 로그인해야 하며, 비로그인은 PUBLIC 만 조회할 수 있다.
     */
    @GetMapping
    fun list(
        @RequestParam scope: RecordScope,
        @RequestParam(required = false) tripId: Long?,
        @RequestParam(required = false) category: Category?,
        @RequestParam(required = false) tag: String?,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(defaultValue = "RECENT") sort: RecordSort,
        @RequestParam(required = false) lat: Double?,
        @RequestParam(required = false) lng: Double?,
        @RequestParam(defaultValue = "0") page: Int,
        @AuthenticationPrincipal principal: LoginUser?,
    ): PageResponse<TripRecordSummaryResponse> {
        val userId = when (scope) {
            RecordScope.MINE, RecordScope.SHARED -> requireLogin(principal)
            RecordScope.PUBLIC -> principal?.userId
        }
        return recordService.list(
            RecordListQuery(scope, tripId, category, tag, keyword, sort, lat, lng),
            userId,
            listPageRequest(page),
        ).map { it.toSummaryResponse(userId) }
    }

    /** 기록 상세 조회. 볼 수 없는 기록은 존재하지 않는 것과 같은 404 다. */
    @GetMapping("/{recordId}")
    fun get(
        @PathVariable recordId: Long,
        @AuthenticationPrincipal principal: LoginUser?,
    ): TripRecordResponse {
        val userId = principal?.userId
        return recordService.get(recordId, userId).toResponse(userId)
    }

    /** 기록 등록. 소속 여행은 요청자가 소유한 것이어야 하며, 작성자는 그 여행의 소유자다. */
    @PostMapping
    fun create(
        @RequestBody @Valid request: TripRecordCreateRequest,
        @AuthenticationPrincipal principal: LoginUser?,
    ): TripRecordResponse {
        val userId = requireLogin(principal)
        return recordService.create(userId, request).toResponse(userId)
    }

    /** 기록 수정. 작성자만 할 수 있다. */
    @PutMapping("/{recordId}")
    fun update(
        @PathVariable recordId: Long,
        @RequestBody @Valid request: TripRecordUpdateRequest,
        @AuthenticationPrincipal principal: LoginUser?,
    ): TripRecordResponse {
        val userId = requireLogin(principal)
        return recordService.update(recordId, userId, request).toResponse(userId)
    }

    /**
     * 소속 여행 변경. 공개 범위를 바꾸는 수단은 기록에 없다 —
     * `PATCH /api/trips/{id}/visibility` 로 여행에서 바꾼다 (명세 §4.4).
     */
    @PatchMapping("/{recordId}/trip")
    fun changeTrip(
        @PathVariable recordId: Long,
        @RequestBody @Valid request: TripChangeRequest,
        @AuthenticationPrincipal principal: LoginUser?,
    ): TripRecordResponse {
        val userId = requireLogin(principal)
        return recordService.changeTrip(recordId, userId, request).toResponse(userId)
    }

    /** 기록 삭제. soft delete 라 행은 남고 조회에서만 사라진다. */
    @DeleteMapping("/{recordId}")
    fun delete(
        @PathVariable recordId: Long,
        @AuthenticationPrincipal principal: LoginUser?,
    ): ResponseEntity<Void> {
        recordService.delete(recordId, requireLogin(principal))
        return ResponseEntity.noContent().build()
    }
}
