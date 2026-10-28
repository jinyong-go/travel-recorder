package com.yong.travel.place.presentation

import com.yong.travel.auth.security.LoginUser
import com.yong.travel.common.presentation.PageResponse
import com.yong.travel.common.web.requireLogin
import com.yong.travel.place.service.PlaceSearchService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 기록 등록 폼에서 쓰는 장소 검색.
 * 저장 단위가 아닌 외부 조회라 기록과 경로를 나눴고, API 키를 숨기려고 서버를 거친다.
 */
@RestController
@RequestMapping("/api/places")
class PlaceSearchController(
    private val placeSearchService: PlaceSearchService,
) {

    /**
     * 장소를 검색한다. 익명 요청으로 외부 API 일일 한도가 소진되지 않도록 로그인을 요구한다.
     *
     * @param page 0부터 시작
     */
    @GetMapping("/search")
    fun search(
        @RequestParam keyword: String,
        @RequestParam(defaultValue = "0") page: Int,
        @AuthenticationPrincipal principal: LoginUser?,
    ): PageResponse<PlaceSearchResultResponse> {
        requireLogin(principal)
        return PageResponse.of(placeSearchService.search(keyword, page))
            .map { PlaceSearchResultResponse.from(it) }
    }
}
