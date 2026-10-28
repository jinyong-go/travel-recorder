package com.yong.travel.trip.record.presentation

import com.yong.travel.auth.security.LoginUser
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import com.yong.travel.common.presentation.PageResponse
import com.yong.travel.common.web.requireLogin
import com.yong.travel.trip.record.domain.Category
import com.yong.travel.trip.record.domain.GeoPoint
import com.yong.travel.trip.record.domain.RecordListQuery
import com.yong.travel.trip.record.domain.RecordScope
import com.yong.travel.trip.record.domain.RecordSort
import com.yong.travel.trip.record.service.TripRecordService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
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

/** 기록 API. */
@RestController
@RequestMapping("/api/records")
class TripRecordController(
    private val recordService: TripRecordService,
) {

    /**
     * 기록 목록을 조회한다. `scope` 나 `tripId` 중 하나가 있어야 한다.
     * 남의 여행이 공유인지 공개인지 모르는 열람자도 볼 수 있도록 `tripId` 만으로도 조회한다.
     *
     * @param tag 태그 이름 완전 일치
     * @param keyword 이름·주소 부분 일치
     * @param lat 기준 위도. 거리순이면 필수
     * @param lng 기준 경도. 거리순이면 필수
     * @param page 0부터 시작
     */
    @GetMapping
    fun list(
        @RequestParam(required = false) scope: RecordScope?,
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
        if (scope == null && tripId == null) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "scope 와 tripId 중 하나는 있어야 합니다.")
        }
        // 한쪽만 온 좌표는 쓸 수 없어 없는 것으로 본다.
        val origin = if (lat != null && lng != null) GeoPoint(lat, lng) else null
        if (origin != null && !GeoPoint.isValid(origin.latitude, origin.longitude)) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "lat·lng 가 좌표 범위를 벗어났습니다.")
        }
        if (sort == RecordSort.DISTANCE && origin == null) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "거리순 정렬에는 lat 과 lng 가 필요합니다.")
        }
        val userId = when (scope) {
            RecordScope.MINE, RecordScope.SHARED -> requireLogin(principal)
            RecordScope.PUBLIC, null -> principal?.userId
        }
        val records = recordService.list(
            RecordListQuery(scope, tripId, category, tag, keyword, sort, origin),
            userId,
            page,
        )
        return PageResponse.of(records).map { TripRecordSummaryResponse.from(it, userId) }
    }

    /** 기록을 조회한다. */
    @GetMapping("/{recordId}")
    fun get(
        @PathVariable recordId: Long,
        @AuthenticationPrincipal principal: LoginUser?,
    ): TripRecordResponse {
        val userId = principal?.userId
        return TripRecordResponse.from(recordService.get(recordId, userId), userId)
    }

    /** 기록을 등록한다. 요청자가 소유한 여행에만 등록할 수 있다. */
    @PostMapping
    fun create(
        @RequestBody @Valid request: TripRecordCreateRequest,
        @AuthenticationPrincipal principal: LoginUser?,
    ): ResponseEntity<TripRecordResponse> {
        val userId = requireLogin(principal)
        val record = recordService.create(userId, request.toCommand())
        return ResponseEntity.status(HttpStatus.CREATED).body(TripRecordResponse.from(record, userId))
    }

    /** 기록을 수정한다. 여행 소유자만 할 수 있다. */
    @PutMapping("/{recordId}")
    fun update(
        @PathVariable recordId: Long,
        @RequestBody @Valid request: TripRecordUpdateRequest,
        @AuthenticationPrincipal principal: LoginUser?,
    ): TripRecordResponse {
        val userId = requireLogin(principal)
        return TripRecordResponse.from(recordService.update(recordId, userId, request.toCommand()), userId)
    }

    /** 기록을 다른 여행으로 옮긴다. */
    @PatchMapping("/{recordId}/trip")
    fun changeTrip(
        @PathVariable recordId: Long,
        @RequestBody @Valid request: TripChangeRequest,
        @AuthenticationPrincipal principal: LoginUser?,
    ): TripRecordResponse {
        val userId = requireLogin(principal)
        return TripRecordResponse.from(recordService.changeTrip(recordId, userId, request.tripId), userId)
    }

    /** 기록을 지운다. */
    @DeleteMapping("/{recordId}")
    fun delete(
        @PathVariable recordId: Long,
        @AuthenticationPrincipal principal: LoginUser?,
    ): ResponseEntity<Void> {
        recordService.delete(recordId, requireLogin(principal))
        return ResponseEntity.noContent().build()
    }
}
