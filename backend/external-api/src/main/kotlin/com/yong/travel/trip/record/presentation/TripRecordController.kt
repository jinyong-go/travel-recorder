package com.yong.travel.trip.record.presentation

import com.yong.travel.auth.security.LoginUser
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import com.yong.travel.common.presentation.PageResponse
import com.yong.travel.common.web.requireLogin
import com.yong.travel.trip.record.domain.Category
import com.yong.travel.trip.record.domain.RecordListQuery
import com.yong.travel.trip.record.domain.RecordScope
import com.yong.travel.trip.record.domain.RecordSort
import com.yong.travel.trip.record.service.TripRecordService
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

/** 여행 기록 API (`/api/records`). */
@RestController
@RequestMapping("/api/records")
class TripRecordController(
    private val recordService: TripRecordService,
) {

    /**
     * 기록 목록 조회.
     *
     * `scope` 는 `tripId` 가 없으면 필수다. "무엇을 보는 목록인지" 가 화면마다 다르고, 기본값을
     * 두면 실수로 넓은 범위를 조회하는 쪽이 조용히 기본이 되기 때문이다. `tripId` 만 주면 그 여행의
     * 하위 기록이다 — 열람자는 남의 여행이 공유인지 공개인지 몰라 scope 를 고를 수 없다.
     * MINE/SHARED 는 로그인해야 하며, 비로그인은 PUBLIC 과 tripId 조회만 할 수 있다.
     *
     * @param scope 조회 범위. `tripId` 가 없으면 필수
     * @param tripId 이 여행의 하위 기록으로 한정한다
     * @param category 카테고리 필터
     * @param tag 태그 이름 완전 일치 필터
     * @param keyword 이름·주소 부분 일치 검색어
     * @param sort 정렬
     * @param lat 기준 위도. 거리 계산·거리순 정렬에만 쓴다. 거리순이면 필수
     * @param lng 기준 경도. 거리 계산·거리순 정렬에만 쓴다. 거리순이면 필수
     * @param page 0부터 시작하는 페이지 번호
     * @param principal 로그인 사용자. 비로그인이면 null
     * @return 기록 요약 한 페이지
     * @throws ApiException `VALIDATION_ERROR` — `scope` 와 `tripId` 가 모두 없을 때, 거리순인데 `lat`·`lng` 가 없을 때
     * @throws ApiException `UNAUTHENTICATED` — MINE·SHARED 를 비로그인으로 요청했을 때
     * @throws ApiException `TRIP_NOT_FOUND` — `tripId` 의 여행이 없거나 볼 권한이 없을 때
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
        // 거리순은 DB 가 기준 위치로 정렬하므로 좌표 없이는 정렬할 기준이 없다.
        if (sort == RecordSort.DISTANCE && (lat == null || lng == null)) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "거리순 정렬에는 lat 과 lng 가 필요합니다.")
        }
        val userId = when (scope) {
            RecordScope.MINE, RecordScope.SHARED -> requireLogin(principal)
            RecordScope.PUBLIC, null -> principal?.userId
        }
        val records = recordService.list(
            RecordListQuery(scope, tripId, category, tag, keyword, sort, lat, lng),
            userId,
            page,
        )
        return PageResponse.of(records).map { TripRecordSummaryResponse.from(it, userId) }
    }

    /**
     * 기록 상세를 조회한다. 비로그인도 부를 수 있다.
     *
     * @param recordId 조회할 기록 id
     * @param principal 로그인 사용자. 비로그인이면 null
     * @return 기록 상세
     * @throws ApiException `RECORD_NOT_FOUND` — 기록이 없거나 볼 권한이 없을 때
     */
    @GetMapping("/{recordId}")
    fun get(
        @PathVariable recordId: Long,
        @AuthenticationPrincipal principal: LoginUser?,
    ): TripRecordResponse {
        val userId = principal?.userId
        return TripRecordResponse.from(recordService.get(recordId, userId), userId)
    }

    /**
     * 기록을 등록한다. 소속 여행은 요청자가 소유한 것이어야 하며, 작성자는 그 여행의 소유자다.
     *
     * @param request 기록 입력값
     * @param principal 로그인 사용자. 비로그인이면 null
     * @return 등록된 기록 상세
     * @throws ApiException `UNAUTHENTICATED` — 로그인하지 않았을 때
     * @throws ApiException `TRIP_NOT_FOUND` — 소속 여행이 없거나 요청자의 여행이 아닐 때
     */
    @PostMapping
    fun create(
        @RequestBody @Valid request: TripRecordCreateRequest,
        @AuthenticationPrincipal principal: LoginUser?,
    ): TripRecordResponse {
        val userId = requireLogin(principal)
        return TripRecordResponse.from(recordService.create(userId, request.toCommand()), userId)
    }

    /**
     * 기록을 수정한다. 작성자만 할 수 있다.
     *
     * @param recordId 수정할 기록 id
     * @param request 새 기록 내용
     * @param principal 로그인 사용자. 비로그인이면 null
     * @return 수정된 기록 상세
     * @throws ApiException `UNAUTHENTICATED` — 로그인하지 않았을 때
     * @throws ApiException `RECORD_NOT_FOUND` — 기록이 없거나 볼 권한이 없을 때
     * @throws ApiException `FORBIDDEN` — 볼 수는 있으나 작성자가 아닐 때
     */
    @PutMapping("/{recordId}")
    fun update(
        @PathVariable recordId: Long,
        @RequestBody @Valid request: TripRecordUpdateRequest,
        @AuthenticationPrincipal principal: LoginUser?,
    ): TripRecordResponse {
        val userId = requireLogin(principal)
        return TripRecordResponse.from(recordService.update(recordId, userId, request.toCommand()), userId)
    }

    /**
     * 소속 여행 변경. 공개 범위를 바꾸는 수단은 기록에 없다 —
     * `PATCH /api/trips/{id}/visibility` 로 여행에서 바꾼다.
     *
     * @param recordId 옮길 기록 id
     * @param request 옮겨 갈 여행 id
     * @param principal 로그인 사용자. 비로그인이면 null
     * @return 옮겨진 기록 상세
     * @throws ApiException `UNAUTHENTICATED` — 로그인하지 않았을 때
     * @throws ApiException `RECORD_NOT_FOUND` — 기록이 없거나 볼 권한이 없을 때
     * @throws ApiException `FORBIDDEN` — 볼 수는 있으나 작성자가 아닐 때
     * @throws ApiException `TRIP_NOT_FOUND` — 옮겨 갈 여행이 없거나 요청자의 여행이 아닐 때
     */
    @PatchMapping("/{recordId}/trip")
    fun changeTrip(
        @PathVariable recordId: Long,
        @RequestBody @Valid request: TripChangeRequest,
        @AuthenticationPrincipal principal: LoginUser?,
    ): TripRecordResponse {
        val userId = requireLogin(principal)
        return TripRecordResponse.from(recordService.changeTrip(recordId, userId, request.tripId), userId)
    }

    /**
     * 기록을 삭제한다. soft delete 라 행은 남고 조회에서만 사라진다.
     *
     * @param recordId 삭제할 기록 id
     * @param principal 로그인 사용자. 비로그인이면 null
     * @return 본문 없는 `204`
     * @throws ApiException `UNAUTHENTICATED` — 로그인하지 않았을 때
     * @throws ApiException `RECORD_NOT_FOUND` — 기록이 없거나 볼 권한이 없을 때
     * @throws ApiException `FORBIDDEN` — 볼 수는 있으나 작성자가 아닐 때
     */
    @DeleteMapping("/{recordId}")
    fun delete(
        @PathVariable recordId: Long,
        @AuthenticationPrincipal principal: LoginUser?,
    ): ResponseEntity<Void> {
        recordService.delete(recordId, requireLogin(principal))
        return ResponseEntity.noContent().build()
    }
}
