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

/**
 * 받은 초대를 다루는 경로. 초대는 받은 사람의 계정에 쌓이므로 **비로그인으로 열 수 있는 경로가 없다**.
 * 보내는 쪽은 `GroupController` 의 `/api/groups/{groupId}/invites` 다.
 */
@RestController
@RequestMapping("/api/invites")
class InviteController(
    private val groupInviteService: GroupInviteService,
) {

    /** 내 앞으로 온 초대 목록. */
    @GetMapping
    fun received(
        @RequestParam(defaultValue = "0") page: Int,
        @AuthenticationPrincipal principal: LoginUser?,
    ): PageResponse<ReceivedInviteResponse> =
        PageResponse.of(groupInviteService.listReceived(requireLogin(principal), page))
            .map { ReceivedInviteResponse.from(it) }

    /** 내가 보낸 대기 초대 목록. 그룹을 가로질러 모은다. */
    @GetMapping("/sent")
    fun sent(
        @RequestParam(defaultValue = "0") page: Int,
        @AuthenticationPrincipal principal: LoginUser?,
    ): PageResponse<SentInviteResponse> =
        PageResponse.of(groupInviteService.listSent(requireLogin(principal), page))
            .map { SentInviteResponse.from(it) }

    /**
     * 끝난 초대 이력. `role` 로 받은 관점과 보낸 관점을 고르며 기본값은 받은 쪽이다.
     *
     * 남의 이력을 볼 경로는 없다 — 조회 조건이 곧 본인 필터다.
     */
    @GetMapping("/history")
    fun history(
        @RequestParam(defaultValue = "RECEIVED") role: InviteHistoryRole,
        @RequestParam(defaultValue = "0") page: Int,
        @AuthenticationPrincipal principal: LoginUser?,
    ): PageResponse<InviteHistoryResponse> =
        PageResponse.of(groupInviteService.listHistory(requireLogin(principal), role, page))
            .map { InviteHistoryResponse.from(it) }

    /** 초대 수락 → 그룹 멤버가 된다. 정원이 차 있으면 409 이고 초대는 남는다. */
    @PostMapping("/{inviteId}/accept")
    fun accept(
        @PathVariable inviteId: Long,
        @AuthenticationPrincipal principal: LoginUser?,
    ): ResponseEntity<Void> {
        groupInviteService.accept(inviteId, requireLogin(principal))
        return ResponseEntity.noContent().build()
    }

    /** 초대 거절. 대기 목록에서 지우고 이력에 `REJECTED` 로 남긴다 — 보낸 사람도 본다. */
    @PostMapping("/{inviteId}/reject")
    fun reject(
        @PathVariable inviteId: Long,
        @AuthenticationPrincipal principal: LoginUser?,
    ): ResponseEntity<Void> {
        groupInviteService.reject(inviteId, requireLogin(principal))
        return ResponseEntity.noContent().build()
    }
}
