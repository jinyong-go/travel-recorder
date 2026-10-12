package com.yong.travel.tag.presentation

import com.yong.travel.auth.security.LoginUser
import com.yong.travel.tag.service.TagService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 태그 자동완성 API (`/api/tags`). 비로그인도 부를 수 있다. */
@RestController
@RequestMapping("/api/tags")
class TagController(
    private val tagService: TagService,
) {

    /**
     * 태그 자동완성 후보를 조회한다. 후보는 요청자가 볼 수 있는 기록에 쓰인 태그로 제한된다.
     *
     * @param keyword 부분 일치 검색어. 없으면 전체에서 고른다
     * @param principal 로그인 사용자. 비로그인이면 null
     * @return 태그 후보 목록
     */
    @GetMapping
    fun search(
        @RequestParam(required = false) keyword: String?,
        @AuthenticationPrincipal principal: LoginUser?,
    ): List<TagResponse> = tagService.search(keyword, principal?.userId).map { TagResponse.from(it) }
}
