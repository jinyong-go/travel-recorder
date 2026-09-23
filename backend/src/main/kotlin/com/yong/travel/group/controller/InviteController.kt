package com.yong.travel.group.controller

import com.yong.travel.auth.security.LoginUser
import com.yong.travel.common.dto.PageResponse
import com.yong.travel.common.web.listPageRequest
import com.yong.travel.common.web.requireLogin
import com.yong.travel.group.dto.InviteHistoryResponse
import com.yong.travel.group.dto.InviteHistoryRole
import com.yong.travel.group.dto.ReceivedInviteResponse
import com.yong.travel.group.dto.SentInviteResponse
import com.yong.travel.group.dto.toResponse
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
        @AuthenticationPrincipal principal: LoginUser?,
    ): PageResponse<ReceivedInviteResponse> =
        inviteService.listReceived(requireLogin(principal), listPageRequest(page)).map { it.toResponse() }

    /** 내가 보낸 대기 초대 목록. 그룹을 가로질러 모은다 (명세 §4.8). */
    @GetMapping("/sent")
    fun sent(
        @RequestParam(defaultValue = "0") page: Int,
        @AuthenticationPrincipal principal: LoginUser?,
    ): PageResponse<SentInviteResponse> =
        inviteService.listSent(requireLogin(principal), listPageRequest(page)).map { it.toResponse() }

    /**
     * 끝난 초대 이력. `role` 로 받은 관점과 보낸 관점을 고르며 기본값은 받은 쪽이다.
     *
     * 남의 이력을 볼 경로는 없다 — 조회 조건이 곧 본인 필터다 (명세 §4.8).
     */
    @GetMapping("/history")
    fun history(
        @RequestParam(defaultValue = "RECEIVED") role: InviteHistoryRole,
        @RequestParam(defaultValue = "0") page: Int,
        @AuthenticationPrincipal principal: LoginUser?,
    ): PageResponse<InviteHistoryResponse> =
        inviteService.listHistory(requireLogin(principal), role, listPageRequest(page)).map { it.toResponse() }

    /** 초대 수락 → 그룹 멤버가 된다. 정원이 차 있으면 409 이고 초대는 남는다. */
    @PostMapping("/{inviteId}/accept")
    fun accept(
        @PathVariable inviteId: Long,
        @AuthenticationPrincipal principal: LoginUser?,
    ): ResponseEntity<Void> {
        inviteService.accept(inviteId, requireLogin(principal))
        return ResponseEntity.noContent().build()
    }

    /** 초대 거절. 대기 목록에서 지우고 이력에 `REJECTED` 로 남긴다 — 보낸 사람도 본다 (공통 명세 §3.7). */
    @PostMapping("/{inviteId}/reject")
    fun reject(
        @PathVariable inviteId: Long,
        @AuthenticationPrincipal principal: LoginUser?,
    ): ResponseEntity<Void> {
        inviteService.reject(inviteId, requireLogin(principal))
        return ResponseEntity.noContent().build()
    }
}
