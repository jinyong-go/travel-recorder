package com.yong.travel.group.presentation

import com.yong.travel.auth.security.LoginUser
import com.yong.travel.common.presentation.PageResponse
import com.yong.travel.common.web.requireLogin
import com.yong.travel.group.service.GroupInviteService
import com.yong.travel.group.service.GroupService
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

/** 그룹과 멤버, 그룹에서 보내는 초대 API (`/api/groups`). 받은 초대는 [InviteController] 가 다룬다. */
@RestController
@RequestMapping("/api/groups")
class GroupController(
    private val groupService: GroupService,
    private val groupInviteService: GroupInviteService,
) {

    /** 내가 소유하거나 멤버로 속한 그룹 목록을 조회한다. */
    @GetMapping
    fun list(@AuthenticationPrincipal principal: LoginUser?): List<GroupSummaryResponse> {
        val userId = requireLogin(principal)
        return groupService.list(userId).map { GroupSummaryResponse.from(it, userId) }
    }

    /**
     * 그룹을 만든다. 만든 사람이 소유자이자 첫 멤버가 된다.
     *
     * @return 만들어진 그룹 상세
     */
    @PostMapping
    fun create(
        @RequestBody @Valid request: GroupCreateRequest,
        @AuthenticationPrincipal principal: LoginUser?,
    ): GroupResponse {
        val userId = requireLogin(principal)
        return GroupResponse.from(groupService.create(userId, request.name, request.memo), userId)
    }

    /** 그룹 상세를 멤버 목록과 함께 조회한다. */
    @GetMapping("/{groupId}")
    fun get(
        @PathVariable groupId: Long,
        @AuthenticationPrincipal principal: LoginUser?,
    ): GroupResponse {
        val userId = requireLogin(principal)
        return GroupResponse.from(groupService.get(groupId, userId), userId)
    }

    /** 그룹 이름·메모를 수정한다. 메모는 이름과 함께 덮어쓴다. 소유자만 할 수 있다. */
    @PutMapping("/{groupId}")
    fun update(
        @PathVariable groupId: Long,
        @RequestBody @Valid request: GroupUpdateRequest,
        @AuthenticationPrincipal principal: LoginUser?,
    ): GroupResponse {
        val userId = requireLogin(principal)
        return GroupResponse.from(groupService.update(groupId, userId, request.name, request.memo), userId)
    }

    /** 그룹을 삭제한다. 멤버·대기 초대·공유 관계가 함께 사라진다. */
    @DeleteMapping("/{groupId}")
    fun delete(
        @PathVariable groupId: Long,
        @AuthenticationPrincipal principal: LoginUser?,
    ): ResponseEntity<Void> {
        groupService.delete(groupId, requireLogin(principal))
        return ResponseEntity.noContent().build()
    }

    /**
     * 그룹에서 탈퇴한다. 소유자는 탈퇴할 수 없다.
     *
     * 탈퇴는 본인만 하므로 경로에 사용자 id 를 받지 않는다.
     */
    @DeleteMapping("/{groupId}/members/me")
    fun leave(
        @PathVariable groupId: Long,
        @AuthenticationPrincipal principal: LoginUser?,
    ): ResponseEntity<Void> {
        groupService.leave(groupId, requireLogin(principal))
        return ResponseEntity.noContent().build()
    }

    /** 멤버를 그룹에서 제외한다. 소유자만 할 수 있고 소유자 자신은 대상이 될 수 없다. */
    @DeleteMapping("/{groupId}/members/{userId}")
    fun removeMember(
        @PathVariable groupId: Long,
        @PathVariable userId: Long,
        @AuthenticationPrincipal principal: LoginUser?,
    ): ResponseEntity<Void> {
        groupService.removeMember(groupId, userId, requireLogin(principal))
        return ResponseEntity.noContent().build()
    }

    /**
     * 이 그룹의 대기 중인 초대 목록을 조회한다. 소유자만 본다.
     *
     * @param page 0부터 시작하는 페이지 번호
     */
    @GetMapping("/{groupId}/invites")
    fun pendingInvites(
        @PathVariable groupId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @AuthenticationPrincipal principal: LoginUser?,
    ): PageResponse<PendingInviteResponse> =
        PageResponse.of(groupInviteService.listPending(groupId, requireLogin(principal), page))
            .map { PendingInviteResponse.from(it) }

    /**
     * 이메일로 초대 보내기.
     *
     * 대기 중인 초대가 이미 있으면 새로 만들지 않고 `200` 으로 기존 초대를 돌려준다.
     *
     * @return 새 초대면 `201`, 기존 대기 초대면 `200` 과 그 초대
     */
    @PostMapping("/{groupId}/invites")
    fun invite(
        @PathVariable groupId: Long,
        @RequestBody @Valid request: InviteRequest,
        @AuthenticationPrincipal principal: LoginUser?,
    ): ResponseEntity<PendingInviteResponse> {
        val result = groupInviteService.invite(groupId, requireLogin(principal), request.email)
        val status = if (result.created) HttpStatus.CREATED else HttpStatus.OK
        return ResponseEntity.status(status).body(PendingInviteResponse.from(result.invite))
    }

    /** 대기 중인 초대를 철회한다. 초대 행을 지우고 이력에 남긴다. */
    @DeleteMapping("/{groupId}/invites/{inviteId}")
    fun revokeInvite(
        @PathVariable groupId: Long,
        @PathVariable inviteId: Long,
        @AuthenticationPrincipal principal: LoginUser?,
    ): ResponseEntity<Void> {
        groupInviteService.revoke(groupId, inviteId, requireLogin(principal))
        return ResponseEntity.noContent().build()
    }
}
