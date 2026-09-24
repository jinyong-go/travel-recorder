package com.yong.travel.group.presentation

import com.yong.travel.auth.security.LoginUser
import com.yong.travel.common.presentation.PageResponse
import com.yong.travel.common.web.listPageRequest
import com.yong.travel.common.web.requireLogin
import com.yong.travel.group.service.GroupService
import com.yong.travel.group.service.InviteService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/groups")
class GroupController(
    private val groupService: GroupService,
    private val inviteService: InviteService,
) {

    /** 내가 소유하거나 멤버로 속한 그룹 목록. */
    @GetMapping
    fun list(@AuthenticationPrincipal principal: LoginUser?): List<GroupSummaryResponse> {
        val userId = requireLogin(principal)
        return groupService.list(userId).map { it.toSummaryResponse(userId) }
    }

    /** 그룹 생성. 만든 사람이 소유자이자 첫 멤버가 된다. */
    @PostMapping
    fun create(
        @RequestBody @Valid request: GroupCreateRequest,
        @AuthenticationPrincipal principal: LoginUser?,
    ): GroupResponse {
        val userId = requireLogin(principal)
        return groupService.create(userId, request.name, request.memo).toResponse(userId)
    }

    /** 그룹 상세 (멤버 목록 포함). 멤버가 아니면 403, 없는 그룹이면 404 다 (명세 §2.2.2). */
    @GetMapping("/{groupId}")
    fun get(
        @PathVariable groupId: Long,
        @AuthenticationPrincipal principal: LoginUser?,
    ): GroupResponse {
        val userId = requireLogin(principal)
        return groupService.get(groupId, userId).toResponse(userId)
    }

    /** 그룹 이름 변경. 소유자만 할 수 있다. */
    @PutMapping("/{groupId}")
    fun rename(
        @PathVariable groupId: Long,
        @RequestBody @Valid request: GroupUpdateRequest,
        @AuthenticationPrincipal principal: LoginUser?,
    ): GroupResponse {
        val userId = requireLogin(principal)
        return groupService.rename(groupId, userId, request.name, request.memo).toResponse(userId)
    }

    /** 그룹 삭제. 멤버·대기 초대·공유 관계가 함께 사라진다. */
    @DeleteMapping("/{groupId}")
    fun delete(
        @PathVariable groupId: Long,
        @AuthenticationPrincipal principal: LoginUser?,
    ): ResponseEntity<Void> {
        groupService.delete(groupId, requireLogin(principal))
        return ResponseEntity.noContent().build()
    }

    /**
     * 그룹 탈퇴. 소유자는 탈퇴할 수 없다.
     *
     * 탈퇴는 본인만 하므로 경로에 사용자 id 를 받지 않는다 (`/members/me` 가 먼저 매칭되게 순서 주의).
     */
    @DeleteMapping("/{groupId}/members/me")
    fun leave(
        @PathVariable groupId: Long,
        @AuthenticationPrincipal principal: LoginUser?,
    ): ResponseEntity<Void> {
        groupService.leave(groupId, requireLogin(principal))
        return ResponseEntity.noContent().build()
    }

    /** 멤버 제외. 소유자만 할 수 있고 소유자 자신은 대상이 될 수 없다. */
    @DeleteMapping("/{groupId}/members/{userId}")
    fun removeMember(
        @PathVariable groupId: Long,
        @PathVariable userId: Long,
        @AuthenticationPrincipal principal: LoginUser?,
    ): ResponseEntity<Void> {
        groupService.removeMember(groupId, userId, requireLogin(principal))
        return ResponseEntity.noContent().build()
    }

    /** 이 그룹의 대기 중인 초대 목록. 소유자만 본다. */
    @GetMapping("/{groupId}/invites")
    fun pendingInvites(
        @PathVariable groupId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @AuthenticationPrincipal principal: LoginUser?,
    ): PageResponse<PendingInviteResponse> =
        PageResponse.of(inviteService.listPending(groupId, requireLogin(principal), listPageRequest(page)))
            .map { it.toResponse() }

    /**
     * 이메일로 초대 보내기.
     *
     * 대기 중인 초대가 이미 있으면 새로 만들지 않고 `200` 으로 기존 초대를 돌려준다 (명세 §4.8).
     */
    @PostMapping("/{groupId}/invites")
    fun invite(
        @PathVariable groupId: Long,
        @RequestBody @Valid request: InviteRequest,
        @AuthenticationPrincipal principal: LoginUser?,
    ): ResponseEntity<PendingInviteResponse> {
        val result = inviteService.invite(groupId, requireLogin(principal), request.email)
        val status = if (result.created) HttpStatus.CREATED else HttpStatus.OK
        return ResponseEntity.status(status).body(result.invite.toResponse())
    }

    /** 대기 중인 초대 철회. 초대 행을 지운다. */
    @DeleteMapping("/{groupId}/invites/{inviteId}")
    fun revokeInvite(
        @PathVariable groupId: Long,
        @PathVariable inviteId: Long,
        @AuthenticationPrincipal principal: LoginUser?,
    ): ResponseEntity<Void> {
        inviteService.revoke(groupId, inviteId, requireLogin(principal))
        return ResponseEntity.noContent().build()
    }
}
