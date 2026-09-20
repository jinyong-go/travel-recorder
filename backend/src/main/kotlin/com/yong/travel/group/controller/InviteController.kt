package com.yong.travel.group.controller

import com.yong.travel.auth.security.CustomOAuth2User
import com.yong.travel.common.dto.PageResponse
import com.yong.travel.common.web.listPageRequest
import com.yong.travel.common.web.requireLogin
import com.yong.travel.group.dto.ReceivedInviteResponse
import com.yong.travel.group.service.InviteService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 받은 초대를 다루는 경로. 초대는 받은 사람의 계정에 쌓이므로 **비로그인으로 열 수 있는 경로가 없다**
 * (공통 명세 §2.1). 보내는 쪽은 `GroupController` 의 `/api/groups/{groupId}/invites` 다.
 */
@RestController
@RequestMapping("/api/invites")
class InviteController(
    private val inviteService: InviteService,
) {

    /** 내 앞으로 온 초대 목록. */
    @GetMapping
    fun received(
        @RequestParam(defaultValue = "0") page: Int,
        @AuthenticationPrincipal principal: CustomOAuth2User?,
    ): PageResponse<ReceivedInviteResponse> =
        inviteService.listReceived(requireLogin(principal), listPageRequest(page))

    /** 초대 수락 → 그룹 멤버가 된다. 정원이 차 있으면 409 이고 초대는 남는다. */
    @PostMapping("/{inviteId}/accept")
    fun accept(
        @PathVariable inviteId: Long,
        @AuthenticationPrincipal principal: CustomOAuth2User?,
    ): ResponseEntity<Void> {
        inviteService.accept(inviteId, requireLogin(principal))
        return ResponseEntity.noContent().build()
    }

    /** 초대 거절. 흔적을 남기지 않고 지운다. */
    @PostMapping("/{inviteId}/reject")
    fun reject(
        @PathVariable inviteId: Long,
        @AuthenticationPrincipal principal: CustomOAuth2User?,
    ): ResponseEntity<Void> {
        inviteService.reject(inviteId, requireLogin(principal))
        return ResponseEntity.noContent().build()
    }
}
