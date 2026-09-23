package com.yong.travel.trip.controller

import com.yong.travel.auth.security.LoginUser
import com.yong.travel.common.dto.PageResponse
import com.yong.travel.common.web.listPageRequest
import com.yong.travel.common.web.requireLogin
import com.yong.travel.trip.dto.TripCoverUpdateRequest
import com.yong.travel.trip.dto.TripCreateRequest
import com.yong.travel.trip.dto.TripListQuery
import com.yong.travel.trip.dto.TripResponse
import com.yong.travel.trip.dto.TripScope
import com.yong.travel.trip.dto.TripSort
import com.yong.travel.trip.dto.TripSummaryResponse
import com.yong.travel.trip.dto.TripUpdateRequest
import com.yong.travel.trip.dto.TripVisibilityUpdateRequest
import com.yong.travel.trip.dto.toResponse
import com.yong.travel.trip.dto.toSummaryResponse
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

@RestController
@RequestMapping("/api/trips")
class TripController(
    private val tripService: TripService,
) {

    /**
     * 여행 목록 조회.
     *
     * `scope` 는 필수다. "무엇을 보는 목록인지" 가 화면마다 다르고, 기본값을 두면
     * 실수로 넓은 범위를 조회하는 쪽이 조용히 기본이 되기 때문이다.
     * MINE/SHARED 는 로그인해야 하며, 비로그인은 PUBLIC 만 조회할 수 있다.
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
        return tripService.list(TripListQuery(scope, keyword, sort), userId, listPageRequest(page))
            .map { it.toSummaryResponse(userId) }
    }

    /** 여행 상세 조회. 볼 수 없는 여행은 존재하지 않는 것과 같은 404 다. */
    @GetMapping("/{tripId}")
    fun get(
        @PathVariable tripId: Long,
        @AuthenticationPrincipal principal: LoginUser?,
    ): TripResponse {
        val userId = principal?.userId
        return tripService.get(tripId, userId).toResponse(userId)
    }

    /** 여행 생성. 소유자는 요청자로 고정되며 요청으로 지정할 수 없다. */
    @PostMapping
    fun create(
        @RequestBody @Valid request: TripCreateRequest,
        @AuthenticationPrincipal principal: LoginUser?,
    ): TripResponse {
        val userId = requireLogin(principal)
        return tripService.create(userId, request).toResponse(userId)
    }

    /** 여행 기본 정보 수정. 공개 범위는 이 경로로 바꾸지 않는다. 소유자만 할 수 있다. */
    @PutMapping("/{tripId}")
    fun update(
        @PathVariable tripId: Long,
        @RequestBody @Valid request: TripUpdateRequest,
        @AuthenticationPrincipal principal: LoginUser?,
    ): TripResponse {
        val userId = requireLogin(principal)
        return tripService.update(tripId, userId, request).toResponse(userId)
    }

    /** 공개 범위 변경. GROUP 이면 공유 그룹 목록도 함께 전체 교체된다. */
    @PatchMapping("/{tripId}/visibility")
    fun changeVisibility(
        @PathVariable tripId: Long,
        @RequestBody @Valid request: TripVisibilityUpdateRequest,
        @AuthenticationPrincipal principal: LoginUser?,
    ): TripResponse {
        val userId = requireLogin(principal)
        return tripService.changeVisibility(tripId, userId, request).toResponse(userId)
    }

    /** 커버 사진 지정·해제. `photoId` 가 null 이면 해제한다. */
    @PatchMapping("/{tripId}/cover")
    fun changeCover(
        @PathVariable tripId: Long,
        @RequestBody @Valid request: TripCoverUpdateRequest,
        @AuthenticationPrincipal principal: LoginUser?,
    ): TripResponse {
        val userId = requireLogin(principal)
        return tripService.changeCover(tripId, userId, request).toResponse(userId)
    }

    /** 여행 삭제. soft delete 이며 하위 기록도 함께 사라진다. */
    @DeleteMapping("/{tripId}")
    fun delete(
        @PathVariable tripId: Long,
        @AuthenticationPrincipal principal: LoginUser?,
    ): ResponseEntity<Void> {
        tripService.delete(tripId, requireLogin(principal))
        return ResponseEntity.noContent().build()
    }
}
