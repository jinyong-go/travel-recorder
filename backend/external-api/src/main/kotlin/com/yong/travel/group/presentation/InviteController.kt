package com.yong.travel.group.presentation

import com.yong.travel.auth.security.LoginUser
import com.yong.travel.common.presentation.PageResponse
import com.yong.travel.common.web.requireLogin
import com.yong.travel.group.domain.InviteHistoryRole
import com.yong.travel.group.service.GroupInviteService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 내 초대 목록, 이력, 수락·거절 API. */
@RestController
@RequestMapping("/api/invites")
class InviteController(
    private val groupInviteService: GroupInviteService,
) {

    /**
     * 받은 초대를 조회한다.
     *
     * @param page 0부터 시작
     */
    @GetMapping
    fun received(
        @RequestParam(defaultValue = "0") page: Int,
        @AuthenticationPrincipal principal: LoginUser?,
    ): PageResponse<ReceivedInviteResponse> =
        PageResponse.of(groupInviteService.listReceived(requireLogin(principal), page))
            .map { ReceivedInviteResponse.from(it) }

    /**
     * 내가 보낸 대기 초대를 모든 그룹에 걸쳐 조회한다.
     *
     * @param page 0부터 시작
     */
    @GetMapping("/sent")
    fun sent(
        @RequestParam(defaultValue = "0") page: Int,
        @AuthenticationPrincipal principal: LoginUser?,
    ): PageResponse<SentInviteResponse> =
        PageResponse.of(groupInviteService.listSent(requireLogin(principal), page))
            .map { SentInviteResponse.from(it) }

    /**
     * 끝난 초대 이력을 조회한다. 기본은 받은 이력이다.
     *
     * @param page 0부터 시작
     */
    @GetMapping("/history")
    fun history(
        @RequestParam(defaultValue = "RECEIVED") role: InviteHistoryRole,
        @RequestParam(defaultValue = "0") page: Int,
        @AuthenticationPrincipal principal: LoginUser?,
    ): PageResponse<InviteHistoryResponse> =
        PageResponse.of(groupInviteService.listHistory(requireLogin(principal), role, page))
            .map { InviteHistoryResponse.from(it) }

    /** 초대를 수락한다. 정원이 차 있으면 거부되고 초대는 남는다. */
    @PostMapping("/{inviteId}/accept")
    fun accept(
        @PathVariable inviteId: Long,
        @AuthenticationPrincipal principal: LoginUser?,
    ): ResponseEntity<Void> {
        groupInviteService.accept(inviteId, requireLogin(principal))
        return ResponseEntity.noContent().build()
    }

    /** 초대를 거절한다. */
    @PostMapping("/{inviteId}/reject")
    fun reject(
        @PathVariable inviteId: Long,
        @AuthenticationPrincipal principal: LoginUser?,
    ): ResponseEntity<Void> {
        groupInviteService.reject(inviteId, requireLogin(principal))
        return ResponseEntity.noContent().build()
    }
}
