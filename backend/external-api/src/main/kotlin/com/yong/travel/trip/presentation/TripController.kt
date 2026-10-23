package com.yong.travel.trip.presentation

import com.yong.travel.auth.security.LoginUser
import com.yong.travel.common.presentation.PageResponse
import com.yong.travel.common.web.requireLogin
import com.yong.travel.trip.domain.TripListQuery
import com.yong.travel.trip.domain.TripScope
import com.yong.travel.trip.domain.TripSort
import com.yong.travel.trip.service.TripService
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

/** 여행 API (`/api/trips`). */
@RestController
@RequestMapping("/api/trips")
class TripController(
    private val tripService: TripService,
) {

    /**
     * 여행 목록 조회.
     *
     * `scope` 는 필수다 ([TripScope]).
     * MINE/SHARED 는 로그인해야 하며, 비로그인은 PUBLIC 만 조회할 수 있다.
     *
     * @param keyword 여행 이름 부분 일치 검색어
     * @param page 0부터 시작하는 페이지 번호
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

    /** 여행 상세를 조회한다. 비로그인도 부를 수 있다. */
    @GetMapping("/{tripId}")
    fun get(
        @PathVariable tripId: Long,
        @AuthenticationPrincipal principal: LoginUser?,
    ): TripResponse {
        val userId = principal?.userId
        return TripResponse.from(tripService.get(tripId, userId), userId)
    }

    /**
     * 여행을 만든다. 소유자는 요청자로 고정되며 요청으로 지정할 수 없다.
     *
     * @return 만들어진 여행 상세
     */
    @PostMapping
    fun create(
        @RequestBody @Valid request: TripCreateRequest,
        @AuthenticationPrincipal principal: LoginUser?,
    ): TripResponse {
        val userId = requireLogin(principal)
        return TripResponse.from(tripService.create(userId, request.toCommand()), userId)
    }

    /** 여행 기본 정보를 수정한다. 공개 범위는 이 경로로 바꾸지 않는다. 소유자만 할 수 있다. */
    @PutMapping("/{tripId}")
    fun update(
        @PathVariable tripId: Long,
        @RequestBody @Valid request: TripUpdateRequest,
        @AuthenticationPrincipal principal: LoginUser?,
    ): TripResponse {
        val userId = requireLogin(principal)
        return TripResponse.from(tripService.update(tripId, userId, request.toCommand()), userId)
    }

    /** 공개 범위를 바꾼다. GROUP 이면 공유 그룹 목록도 함께 전체 교체된다. */
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

    /** 커버 사진을 지정하거나 해제한다. `photoId` 가 null 이면 해제한다. */
    @PatchMapping("/{tripId}/cover")
    fun changeCover(
        @PathVariable tripId: Long,
        @RequestBody @Valid request: TripCoverUpdateRequest,
        @AuthenticationPrincipal principal: LoginUser?,
    ): TripResponse {
        val userId = requireLogin(principal)
        return TripResponse.from(tripService.changeCover(tripId, userId, request.photoId), userId)
    }

    /** 여행을 삭제한다. soft delete 이며 하위 기록도 함께 사라진다. */
    @DeleteMapping("/{tripId}")
    fun delete(
        @PathVariable tripId: Long,
        @AuthenticationPrincipal principal: LoginUser?,
    ): ResponseEntity<Void> {
        tripService.delete(tripId, requireLogin(principal))
        return ResponseEntity.noContent().build()
    }
}
