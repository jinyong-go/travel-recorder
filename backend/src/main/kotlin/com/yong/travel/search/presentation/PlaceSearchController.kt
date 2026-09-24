package com.yong.travel.search.presentation

import com.yong.travel.common.presentation.PageResponse
import com.yong.travel.search.service.PlaceSearchService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 등록 폼용 장소 검색.
 *
 * 경로가 `/api/records` 와 분리된 이유는, 이 API 가 저장 단위를 다루지 않고 외부(네이버 지역 검색)
 * 조회 결과를 집계해 돌려줄 뿐이기 때문이다. 자체 DB 에 장소 테이블은 없다.
 * API 키를 브라우저에 노출하지 않으려고 백엔드를 경유한다.
 */
@RestController
@RequestMapping("/api/places")
class PlaceSearchController(
    private val placeSearchService: PlaceSearchService,
) {

    /** 장소 검색. 네이버 지역 검색 결과를 집계해 페이지로 돌려준다. */
    @GetMapping("/search")
    fun search(
        @RequestParam keyword: String,
        @RequestParam(required = false) lat: Double?,
        @RequestParam(required = false) lng: Double?,
        @RequestParam(defaultValue = "0") page: Int,
    ): PageResponse<PlaceSearchResultResponse> =
        PageResponse.of(placeSearchService.search(keyword, lat, lng, page)).map { it.toResponse() }
}
