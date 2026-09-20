package com.yong.travel.record.controller

import com.yong.travel.auth.security.CustomOAuth2User
import com.yong.travel.common.dto.PageResponse
import com.yong.travel.common.web.listPageRequest
import com.yong.travel.common.web.requireLogin
import com.yong.travel.record.domain.Category
import com.yong.travel.record.dto.RecordListQuery
import com.yong.travel.record.dto.RecordScope
import com.yong.travel.record.dto.RecordSort
import com.yong.travel.record.dto.VisibilityUpdateRequest
import com.yong.travel.record.dto.VisitRecordCreateRequest
import com.yong.travel.record.dto.VisitRecordResponse
import com.yong.travel.record.dto.VisitRecordSummaryResponse
import com.yong.travel.record.dto.VisitRecordUpdateRequest
import com.yong.travel.record.service.VisitRecordService
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
class VisitRecordController(
    private val recordService: VisitRecordService,
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
        @RequestParam(required = false) category: Category?,
        @RequestParam(required = false) tag: String?,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(defaultValue = "RECENT") sort: RecordSort,
        @RequestParam(required = false) lat: Double?,
        @RequestParam(required = false) lng: Double?,
        @RequestParam(defaultValue = "0") page: Int,
        @AuthenticationPrincipal principal: CustomOAuth2User?,
    ): PageResponse<VisitRecordSummaryResponse> {
        val userId = when (scope) {
            RecordScope.MINE, RecordScope.SHARED -> requireLogin(principal)
            RecordScope.PUBLIC -> principal?.userId
        }
        return recordService.list(
            RecordListQuery(scope, category, tag, keyword, sort, lat, lng),
            userId,
            listPageRequest(page),
        )
    }

    /** 기록 상세 조회. 볼 수 없는 기록은 존재하지 않는 것과 같은 404 다. */
    @GetMapping("/{recordId}")
    fun get(
        @PathVariable recordId: Long,
        @AuthenticationPrincipal principal: CustomOAuth2User?,
    ): VisitRecordResponse = recordService.get(recordId, principal?.userId)

    /** 기록 등록. 작성자는 요청자로 고정되며 요청으로 지정할 수 없다. */
    @PostMapping
    fun create(
        @RequestBody @Valid request: VisitRecordCreateRequest,
        @AuthenticationPrincipal principal: CustomOAuth2User?,
    ): VisitRecordResponse = recordService.create(requireLogin(principal), request)

    /** 기록 수정. 작성자만 할 수 있다. */
    @PutMapping("/{recordId}")
    fun update(
        @PathVariable recordId: Long,
        @RequestBody @Valid request: VisitRecordUpdateRequest,
        @AuthenticationPrincipal principal: CustomOAuth2User?,
    ): VisitRecordResponse = recordService.update(recordId, requireLogin(principal), request)

    /** 기록의 공개 범위 변경. GROUP 이면 공유 그룹 목록도 함께 갱신된다. */
    @PatchMapping("/{recordId}/visibility")
    fun changeVisibility(
        @PathVariable recordId: Long,
        @RequestBody @Valid request: VisibilityUpdateRequest,
        @AuthenticationPrincipal principal: CustomOAuth2User?,
    ): VisitRecordResponse = recordService.changeVisibility(recordId, requireLogin(principal), request)

    /** 기록 삭제. soft delete 라 행은 남고 조회에서만 사라진다. */
    @DeleteMapping("/{recordId}")
    fun delete(
        @PathVariable recordId: Long,
        @AuthenticationPrincipal principal: CustomOAuth2User?,
    ): ResponseEntity<Void> {
        recordService.delete(recordId, requireLogin(principal))
        return ResponseEntity.noContent().build()
    }
}
