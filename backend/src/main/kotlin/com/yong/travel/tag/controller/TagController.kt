package com.yong.travel.tag.controller

import com.yong.travel.auth.security.CustomOAuth2User
import com.yong.travel.tag.dto.TagResponse
import com.yong.travel.tag.service.TagService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/tags")
class TagController(
    private val tagService: TagService,
) {

    /** 태그 자동완성. 후보는 요청자가 볼 수 있는 기록에 쓰인 태그로 제한된다. */
    @GetMapping
    fun search(
        @RequestParam(required = false) keyword: String?,
        @AuthenticationPrincipal principal: CustomOAuth2User?,
    ): List<TagResponse> = tagService.search(keyword, principal?.userId)
}
