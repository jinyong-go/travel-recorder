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
     * `scope` 는 필수다. "무엇을 보는 목록인지" 가 화면마다 다르고, 기본값을 두면
     * 실수로 넓은 범위를 조회하는 쪽이 조용히 기본이 되기 때문이다.
     * MINE/SHARED 는 로그인해야 하며, 비로그인은 PUBLIC 만 조회할 수 있다.
     *
     * @param scope 조회 범위
     * @param keyword 여행 이름 부분 일치 검색어
     * @param sort 정렬
     * @param page 0부터 시작하는 페이지 번호
     * @param principal 로그인 사용자. 비로그인이면 null
     * @return 여행 요약 한 페이지
     * @throws ApiException `UNAUTHENTICATED` — MINE·SHARED 를 비로그인으로 요청했을 때
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

    /**
     * 여행 상세를 조회한다. 비로그인도 부를 수 있다.
     *
     * @param tripId 조회할 여행 id
     * @param principal 로그인 사용자. 비로그인이면 null
     * @return 여행 상세
     * @throws ApiException `TRIP_NOT_FOUND` — 여행이 없거나 볼 권한이 없을 때
     */
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
     * @param request 여행 입력값
     * @param principal 로그인 사용자. 비로그인이면 null
     * @return 만들어진 여행 상세
     * @throws ApiException `UNAUTHENTICATED` — 로그인하지 않았을 때
     * @throws ApiException `GROUP_NOT_FOUND` — 공유 그룹에 요청자가 속하지 않은 그룹이 섞였을 때
     */
    @PostMapping
    fun create(
        @RequestBody @Valid request: TripCreateRequest,
        @AuthenticationPrincipal principal: LoginUser?,
    ): TripResponse {
        val userId = requireLogin(principal)
        return TripResponse.from(tripService.create(userId, request.toCommand()), userId)
    }

    /**
     * 여행 기본 정보를 수정한다. 공개 범위는 이 경로로 바꾸지 않는다. 소유자만 할 수 있다.
     *
     * @param tripId 수정할 여행 id
     * @param request 새 기본 정보
     * @param principal 로그인 사용자. 비로그인이면 null
     * @return 수정된 여행 상세
     * @throws ApiException `UNAUTHENTICATED` — 로그인하지 않았을 때
     * @throws ApiException `TRIP_NOT_FOUND` — 여행이 없거나 볼 권한이 없을 때
     * @throws ApiException `FORBIDDEN` — 볼 수는 있으나 소유자가 아닐 때
     */
    @PutMapping("/{tripId}")
    fun update(
        @PathVariable tripId: Long,
        @RequestBody @Valid request: TripUpdateRequest,
        @AuthenticationPrincipal principal: LoginUser?,
    ): TripResponse {
        val userId = requireLogin(principal)
        return TripResponse.from(tripService.update(tripId, userId, request.toCommand()), userId)
    }

    /**
     * 공개 범위를 바꾼다. GROUP 이면 공유 그룹 목록도 함께 전체 교체된다.
     *
     * @param tripId 대상 여행 id
     * @param request 새 공개 범위와 공유 그룹
     * @param principal 로그인 사용자. 비로그인이면 null
     * @return 수정된 여행 상세
     * @throws ApiException `UNAUTHENTICATED` — 로그인하지 않았을 때
     * @throws ApiException `TRIP_NOT_FOUND` — 여행이 없거나 볼 권한이 없을 때
     * @throws ApiException `FORBIDDEN` — 볼 수는 있으나 소유자가 아닐 때
     * @throws ApiException `GROUP_NOT_FOUND` — 요청자가 속하지 않은 그룹이 섞였을 때
     */
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

    /**
     * 커버 사진을 지정하거나 해제한다. `photoId` 가 null 이면 해제한다.
     *
     * @param tripId 대상 여행 id
     * @param request 커버로 지정할 사진 id
     * @param principal 로그인 사용자. 비로그인이면 null
     * @return 수정된 여행 상세
     * @throws ApiException `UNAUTHENTICATED` — 로그인하지 않았을 때
     * @throws ApiException `TRIP_NOT_FOUND` — 여행이 없거나 볼 권한이 없을 때
     * @throws ApiException `FORBIDDEN` — 볼 수는 있으나 소유자가 아닐 때
     * @throws ApiException `PHOTO_NOT_FOUND` — 사진이 없거나 이 여행의 사진이 아닐 때
     */
    @PatchMapping("/{tripId}/cover")
    fun changeCover(
        @PathVariable tripId: Long,
        @RequestBody @Valid request: TripCoverUpdateRequest,
        @AuthenticationPrincipal principal: LoginUser?,
    ): TripResponse {
        val userId = requireLogin(principal)
        return TripResponse.from(tripService.changeCover(tripId, userId, request.photoId), userId)
    }

    /**
     * 여행을 삭제한다. soft delete 이며 하위 기록도 함께 사라진다.
     *
     * @param tripId 삭제할 여행 id
     * @param principal 로그인 사용자. 비로그인이면 null
     * @return 본문 없는 `204`
     * @throws ApiException `UNAUTHENTICATED` — 로그인하지 않았을 때
     * @throws ApiException `TRIP_NOT_FOUND` — 여행이 없거나 볼 권한이 없을 때
     * @throws ApiException `FORBIDDEN` — 볼 수는 있으나 소유자가 아닐 때
     */
    @DeleteMapping("/{tripId}")
    fun delete(
        @PathVariable tripId: Long,
        @AuthenticationPrincipal principal: LoginUser?,
    ): ResponseEntity<Void> {
        tripService.delete(tripId, requireLogin(principal))
        return ResponseEntity.noContent().build()
    }
}
