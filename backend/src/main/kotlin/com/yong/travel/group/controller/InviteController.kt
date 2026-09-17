package com.yong.travel.group.controller

import com.yong.travel.auth.security.CustomOAuth2User
import com.yong.travel.common.web.requireLogin
import com.yong.travel.group.dto.InvitePreviewResponse
import com.yong.travel.group.service.InviteService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/invites")
class InviteController(
    private val inviteService: InviteService,
) {

    /** 로그인 전에도 "무슨 그룹 초대인지" 는 보여줘야 하므로 인증을 요구하지 않는다. */
    @GetMapping("/{token}")
    fun preview(@PathVariable token: String): InvitePreviewResponse = inviteService.preview(token)

    @PostMapping("/{token}/accept")
    fun accept(
        @PathVariable token: String,
        @AuthenticationPrincipal principal: CustomOAuth2User?,
    ): Map<String, Long> = mapOf("groupId" to inviteService.accept(token, requireLogin(principal)))
}
