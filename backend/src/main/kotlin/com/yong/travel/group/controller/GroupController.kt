package com.yong.travel.group.controller

import com.yong.travel.auth.security.CustomOAuth2User
import com.yong.travel.common.web.requireLogin
import com.yong.travel.group.dto.GroupRequest
import com.yong.travel.group.dto.GroupResponse
import com.yong.travel.group.dto.GroupSummaryResponse
import com.yong.travel.group.dto.InviteResponse
import com.yong.travel.group.service.GroupService
import com.yong.travel.group.service.InviteService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/groups")
class GroupController(
    private val groupService: GroupService,
    private val inviteService: InviteService,
) {

    @GetMapping
    fun list(@AuthenticationPrincipal principal: CustomOAuth2User?): List<GroupSummaryResponse> =
        groupService.list(requireLogin(principal))

    @PostMapping
    fun create(
        @RequestBody @Valid request: GroupRequest,
        @AuthenticationPrincipal principal: CustomOAuth2User?,
    ): GroupResponse = groupService.create(requireLogin(principal), request)

    @GetMapping("/{groupId}")
    fun get(
        @PathVariable groupId: Long,
        @AuthenticationPrincipal principal: CustomOAuth2User?,
    ): GroupResponse = groupService.get(groupId, requireLogin(principal))

    @PutMapping("/{groupId}")
    fun rename(
        @PathVariable groupId: Long,
        @RequestBody @Valid request: GroupRequest,
        @AuthenticationPrincipal principal: CustomOAuth2User?,
    ): GroupResponse = groupService.rename(groupId, requireLogin(principal), request)

    @DeleteMapping("/{groupId}")
    fun delete(
        @PathVariable groupId: Long,
        @AuthenticationPrincipal principal: CustomOAuth2User?,
    ): ResponseEntity<Void> {
        groupService.delete(groupId, requireLogin(principal))
        return ResponseEntity.noContent().build()
    }

    /** 탈퇴는 본인만 하므로 경로에 사용자 id 를 받지 않는다 (`/members/me` 가 먼저 매칭되게 순서 주의). */
    @DeleteMapping("/{groupId}/members/me")
    fun leave(
        @PathVariable groupId: Long,
        @AuthenticationPrincipal principal: CustomOAuth2User?,
    ): ResponseEntity<Void> {
        groupService.leave(groupId, requireLogin(principal))
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{groupId}/members/{userId}")
    fun removeMember(
        @PathVariable groupId: Long,
        @PathVariable userId: Long,
        @AuthenticationPrincipal principal: CustomOAuth2User?,
    ): ResponseEntity<Void> {
        groupService.removeMember(groupId, userId, requireLogin(principal))
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{groupId}/invite")
    fun issueInvite(
        @PathVariable groupId: Long,
        @AuthenticationPrincipal principal: CustomOAuth2User?,
    ): InviteResponse = inviteService.issue(groupId, requireLogin(principal))

    @GetMapping("/{groupId}/invite")
    fun currentInvite(
        @PathVariable groupId: Long,
        @AuthenticationPrincipal principal: CustomOAuth2User?,
    ): ResponseEntity<InviteResponse> =
        inviteService.current(groupId, requireLogin(principal))
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.noContent().build()

    @DeleteMapping("/{groupId}/invite")
    fun revokeInvite(
        @PathVariable groupId: Long,
        @AuthenticationPrincipal principal: CustomOAuth2User?,
    ): ResponseEntity<Void> {
        inviteService.revoke(groupId, requireLogin(principal))
        return ResponseEntity.noContent().build()
    }
}
