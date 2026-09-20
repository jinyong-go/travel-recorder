package com.yong.travel.group.service

import com.yong.travel.auth.dto.toResponse
import com.yong.travel.auth.repository.UserRepository
import com.yong.travel.common.dto.PageResponse
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import com.yong.travel.group.domain.Group
import com.yong.travel.group.domain.GroupInvite
import com.yong.travel.group.domain.GroupMember
import com.yong.travel.group.dto.GroupBriefResponse
import com.yong.travel.group.dto.InviteRequest
import com.yong.travel.group.dto.PendingInviteResponse
import com.yong.travel.group.dto.ReceivedInviteResponse
import com.yong.travel.group.repository.GroupInviteRepository
import com.yong.travel.group.repository.GroupMemberRepository
import com.yong.travel.group.repository.GroupRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 초대 결과. 새로 만들어졌는지(`201`) 기존 대기 초대를 그대로 돌려준 것인지(`200`) 컨트롤러가 구분해야 한다 (명세 §4.8). */
data class InviteResult(
    val invite: PendingInviteResponse,
    val created: Boolean,
)

interface InviteService {
    fun listPending(groupId: Long, ownerId: Long, pageable: Pageable): PageResponse<PendingInviteResponse>
    fun invite(groupId: Long, ownerId: Long, request: InviteRequest): InviteResult
    fun revoke(groupId: Long, inviteId: Long, ownerId: Long)
    fun listReceived(userId: Long, pageable: Pageable): PageResponse<ReceivedInviteResponse>
    fun accept(inviteId: Long, userId: Long)
    fun reject(inviteId: Long, userId: Long)
}

/**
 * 멤버 추가의 유일한 경로. 초대는 받은 사람의 계정에 쌓이고, 수락·거절·철회 어느 쪽으로 끝나든
 * 행을 지운다 (공통 명세 §3.7).
 *
 * 초대는 당사자만 본다. 보낸 소유자와 받은 사람이 아니면 없는 초대와 같은 응답이어야 한다 (명세 §2.2.2).
 */
@Service
@Transactional(readOnly = true)
class InviteServiceImpl(
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val inviteRepository: GroupInviteRepository,
    private val userRepository: UserRepository,
) : InviteService {

    override fun listPending(groupId: Long, ownerId: Long, pageable: Pageable): PageResponse<PendingInviteResponse> {
        requireOwner(findGroup(groupId), ownerId)
        return PageResponse.of(
            inviteRepository.findByGroupIdOrderByCreatedAtAsc(groupId, pageable).map { it.toPendingResponse() },
        )
    }

    /**
     * 이메일 완전 일치로 찾은 가입자에게 초대를 보낸다. 이미 대기 중인 초대가 있으면 새로 만들지 않는다.
     *
     * 정원(§3.6)은 여기서 보지 않는다. 대기 중인 초대는 자리를 차지하지 않으므로 정원을 넘겨 보낼 수 있고,
     * 판정은 수락 시점에만 한다 (공통 명세 §3.7).
     */
    @Transactional
    override fun invite(groupId: Long, ownerId: Long, request: InviteRequest): InviteResult {
        val group = findGroup(groupId)
        requireOwner(group, ownerId)

        // 가입 여부를 숨기지 않는다. 소유자가 오타를 알아차릴 유일한 수단이며, 그 대가는 공통 명세 §7.2에 한계로 적혀 있다.
        val invitee = userRepository.findByEmailIgnoreCase(request.email.trim())
            ?: throw ApiException(ErrorCode.USER_NOT_FOUND)
        val inviteeId = requireNotNull(invitee.id)

        if (groupMemberRepository.existsByGroupIdAndUserId(groupId, inviteeId)) {
            throw ApiException(ErrorCode.ALREADY_MEMBER)
        }

        // 중복 클릭이 실패처럼 보이지 않도록 기존 초대를 그대로 돌려준다. unique(group_id, invitee_id) 가 이를 보장한다.
        inviteRepository.findByGroupIdAndInviteeId(groupId, inviteeId)?.let {
            return InviteResult(it.toPendingResponse(), created = false)
        }

        val saved = inviteRepository.save(
            GroupInvite(group = group, invitee = invitee, invitedBy = group.owner),
        )
        return InviteResult(saved.toPendingResponse(), created = true)
    }

    @Transactional
    override fun revoke(groupId: Long, inviteId: Long, ownerId: Long) {
        val invite = findInvite(inviteId)
        // 보낸 소유자가 아니면 그 초대의 존재도 드러내지 않는다. 경로의 그룹과 어긋나는 id 도 같은 응답이다.
        if (invite.group.id != groupId || invite.group.owner.id != ownerId) {
            throw ApiException(ErrorCode.INVITE_NOT_FOUND)
        }
        inviteRepository.delete(invite)
    }

    override fun listReceived(userId: Long, pageable: Pageable): PageResponse<ReceivedInviteResponse> =
        PageResponse.of(
            inviteRepository.findByInviteeIdOrderByCreatedAtAsc(userId, pageable).map { it.toReceivedResponse() },
        )

    /**
     * 초대를 수락해 그룹 멤버가 된다.
     *
     * 정원이 차 있으면 거부하되 **초대 행은 지우지 않는다.** 자리가 난 뒤 같은 초대로 다시 수락할 수
     * 있어야 하기 때문이다 (공통 명세 §3.7).
     */
    @Transactional
    override fun accept(inviteId: Long, userId: Long) {
        val invite = requireReceivedInvite(inviteId, userId)
        val groupId = requireNotNull(invite.group.id)

        // 정원 검사와 멤버 입력 사이에 다른 수락이 끼어들지 못하도록 그룹 행을 잠그고 읽는다 (명세 §4.8).
        val group = groupRepository.findByIdForUpdate(groupId) ?: throw ApiException(ErrorCode.INVITE_NOT_FOUND)
        if (groupMemberRepository.countByGroupId(groupId) >= Group.MEMBER_LIMIT) {
            throw ApiException(ErrorCode.GROUP_MEMBER_LIMIT_EXCEEDED)
        }

        groupMemberRepository.save(GroupMember(group = group, user = invite.invitee))
        inviteRepository.delete(invite)
    }

    /** 거절은 흔적을 남기지 않는다. 그래서 소유자는 거절 사실을 알 수 없고, 거절한 상대를 다시 초대할 수 있다 (공통 명세 §3.9). */
    @Transactional
    override fun reject(inviteId: Long, userId: Long) {
        inviteRepository.delete(requireReceivedInvite(inviteId, userId))
    }

    private fun findGroup(groupId: Long): Group =
        groupRepository.findById(groupId).orElseThrow { ApiException(ErrorCode.GROUP_NOT_FOUND) }

    private fun requireOwner(group: Group, userId: Long) {
        if (group.owner.id != userId) throw ApiException(ErrorCode.FORBIDDEN)
    }

    private fun findInvite(inviteId: Long): GroupInvite =
        inviteRepository.findById(inviteId).orElseThrow { ApiException(ErrorCode.INVITE_NOT_FOUND) }

    /** 받은 본인이 아니면 없는 초대와 구분되지 않아야 한다 (명세 §2.2.2). */
    private fun requireReceivedInvite(inviteId: Long, userId: Long): GroupInvite {
        val invite = findInvite(inviteId)
        if (invite.invitee.id != userId) throw ApiException(ErrorCode.INVITE_NOT_FOUND)
        return invite
    }

    private fun GroupInvite.toPendingResponse() = PendingInviteResponse(
        id = requireNotNull(id),
        invitee = invitee.toResponse(),
        createdAt = createdAt,
    )

    private fun GroupInvite.toReceivedResponse() = ReceivedInviteResponse(
        id = requireNotNull(id),
        group = GroupBriefResponse(id = requireNotNull(group.id), name = group.name),
        invitedBy = invitedBy.toResponse(),
        createdAt = createdAt,
    )
}
