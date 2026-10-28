package com.yong.travel.trip.presentation

import com.yong.travel.auth.security.LoginUser
import com.yong.travel.common.presentation.PageResponse
import com.yong.travel.common.web.requireLogin
import com.yong.travel.trip.domain.TripListQuery
import com.yong.travel.trip.domain.TripScope
import com.yong.travel.trip.domain.TripSort
import com.yong.travel.trip.service.TripService
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

/** 여행 API. */
@RestController
@RequestMapping("/api/trips")
class TripController(
    private val tripService: TripService,
) {

    /**
     * 여행 목록을 조회한다. 비로그인은 PUBLIC 만 볼 수 있다.
     *
     * @param keyword 여행 이름 부분 일치
     * @param page 0부터 시작
     */
    @GetMapping
    fun list(
        @RequestParam scope: TripScope,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(defaultValue = "RECENT") sort: TripSort,
        @RequestParam(defaultValue = "0") page: Int,
        @AuthenticationPrincipal principal: LoginUser?,
    ): PageResponse<TripSummaryResponse> {
        val userId = when (scope) {
            TripScope.MINE, TripScope.SHARED -> requireLogin(principal)
            TripScope.PUBLIC -> principal?.userId
        }
        val trips = tripService.list(TripListQuery(scope, keyword, sort), userId, page)
        return PageResponse.of(trips).map { TripSummaryResponse.from(it, userId) }
    }

    /** 여행을 조회한다. */
    @GetMapping("/{tripId}")
    fun get(
        @PathVariable tripId: Long,
        @AuthenticationPrincipal principal: LoginUser?,
    ): TripResponse {
        val userId = principal?.userId
        return TripResponse.from(tripService.get(tripId, userId), userId)
    }

    /** 요청자를 소유자로 여행을 만든다. */
    @PostMapping
    fun create(
        @RequestBody @Valid request: TripCreateRequest,
        @AuthenticationPrincipal principal: LoginUser?,
    ): ResponseEntity<TripResponse> {
        val userId = requireLogin(principal)
        val trip = tripService.create(userId, request.toCommand())
        return ResponseEntity.status(HttpStatus.CREATED).body(TripResponse.from(trip, userId))
    }

    /** 여행 기본 정보를 바꾼다. 소유자만 할 수 있다. */
    @PutMapping("/{tripId}")
    fun update(
        @PathVariable tripId: Long,
        @RequestBody @Valid request: TripUpdateRequest,
        @AuthenticationPrincipal principal: LoginUser?,
    ): TripResponse {
        val userId = requireLogin(principal)
        return TripResponse.from(tripService.update(tripId, userId, request.toCommand()), userId)
    }

    /** 공개 범위를 바꾼다. GROUP 이면 공유 그룹도 함께 바꾼다. */
    @PatchMapping("/{tripId}/visibility")
    fun changeVisibility(
        @PathVariable tripId: Long,
        @RequestBody @Valid request: TripVisibilityUpdateRequest,
        @AuthenticationPrincipal principal: LoginUser?,
    ): TripResponse {
        val userId = requireLogin(principal)
        val trip = tripService.changeVisibility(tripId, userId, request.visibility, request.groupIds)
        return TripResponse.from(trip, userId)
    }

    /** 커버 사진을 지정하거나 해제한다. */
    @PatchMapping("/{tripId}/cover")
    fun changeCover(
        @PathVariable tripId: Long,
        @RequestBody @Valid request: TripCoverUpdateRequest,
        @AuthenticationPrincipal principal: LoginUser?,
    ): TripResponse {
        val userId = requireLogin(principal)
        return TripResponse.from(tripService.changeCover(tripId, userId, request.photoId), userId)
    }

    /** 여행을 기록과 함께 지운다. */
    @DeleteMapping("/{tripId}")
    fun delete(
        @PathVariable tripId: Long,
        @AuthenticationPrincipal principal: LoginUser?,
    ): ResponseEntity<Void> {
        tripService.delete(tripId, requireLogin(principal))
        return ResponseEntity.noContent().build()
    }
}
