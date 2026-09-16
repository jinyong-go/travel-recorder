package com.yong.travel.place.controller

import com.yong.travel.auth.security.CustomOAuth2User
import com.yong.travel.common.dto.PageResponse
import com.yong.travel.common.web.requireLogin
import com.yong.travel.place.domain.Category
import com.yong.travel.place.dto.PlaceCreateRequest
import com.yong.travel.place.dto.PlaceListQuery
import com.yong.travel.place.dto.PlaceResponse
import com.yong.travel.place.dto.PlaceSort
import com.yong.travel.place.dto.PlaceSummaryResponse
import com.yong.travel.place.dto.PlaceUpdateRequest
import com.yong.travel.place.service.PlaceService
import com.yong.travel.search.dto.PlaceSearchResultResponse
import com.yong.travel.search.service.PlaceSearchService
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/places")
class PlaceController(
    private val placeService: PlaceService,
    private val placeSearchService: PlaceSearchService,
) {

    @GetMapping
    fun list(
        @RequestParam(required = false) category: Category?,
        @RequestParam(required = false) tag: String?,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(defaultValue = "RECENT") sort: PlaceSort,
        @RequestParam(required = false) lat: Double?,
        @RequestParam(required = false) lng: Double?,
        @PageableDefault(size = 10) pageable: Pageable,
    ): PageResponse<PlaceSummaryResponse> =
        placeService.list(PlaceListQuery(category, tag, keyword, sort, lat, lng), pageable)

    /** 등록 폼용 장소 검색. 네이버 지역 검색 오픈API 를 백엔드에서 호출·집계한다. */
    @GetMapping("/search")
    fun search(
        @RequestParam keyword: String,
        @RequestParam(required = false) lat: Double?,
        @RequestParam(required = false) lng: Double?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
    ): PageResponse<PlaceSearchResultResponse> = placeSearchService.search(keyword, lat, lng, page, size)

    @GetMapping("/{placeId}")
    fun get(@PathVariable placeId: Long): PlaceResponse = placeService.get(placeId)

    @PostMapping
    fun create(
        @RequestBody @Valid request: PlaceCreateRequest,
        @AuthenticationPrincipal principal: CustomOAuth2User?,
    ): PlaceResponse = placeService.create(requireLogin(principal), request)

    @PutMapping("/{placeId}")
    fun update(
        @PathVariable placeId: Long,
        @RequestBody @Valid request: PlaceUpdateRequest,
        @AuthenticationPrincipal principal: CustomOAuth2User?,
    ): PlaceResponse = placeService.update(placeId, requireLogin(principal), request)

    @DeleteMapping("/{placeId}")
    fun delete(
        @PathVariable placeId: Long,
        @AuthenticationPrincipal principal: CustomOAuth2User?,
    ): ResponseEntity<Void> {
        placeService.delete(placeId, requireLogin(principal))
        return ResponseEntity.noContent().build()
    }
}
