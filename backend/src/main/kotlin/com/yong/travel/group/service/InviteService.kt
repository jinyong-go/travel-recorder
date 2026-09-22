package com.yong.travel.group.service

import com.yong.travel.auth.dto.toResponse
import com.yong.travel.auth.repository.UserRepository
import com.yong.travel.common.dto.PageResponse
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import com.yong.travel.group.domain.Group
import com.yong.travel.group.domain.GroupInvite
import com.yong.travel.group.domain.GroupMember
import com.yong.travel.group.domain.InviteHistory
import com.yong.travel.group.domain.InviteOutcome
import com.yong.travel.group.dto.GroupBriefResponse
import com.yong.travel.group.dto.HistoryGroupResponse
import com.yong.travel.group.dto.InviteHistoryResponse
import com.yong.travel.group.dto.InviteHistoryRole
import com.yong.travel.group.dto.InviteRequest
import com.yong.travel.group.dto.PendingInviteResponse
import com.yong.travel.group.dto.ReceivedInviteResponse
import com.yong.travel.group.dto.SentInviteResponse
import com.yong.travel.group.repository.GroupInviteRepository
import com.yong.travel.group.repository.GroupMemberRepository
import com.yong.travel.group.repository.GroupRepository
import com.yong.travel.group.repository.InviteHistoryRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 멤버 추가의 유일한 경로. 초대는 받은 사람의 계정에 쌓이고, 수락·거절·철회 어느 쪽으로 끝나든
 * 행을 지우면서 이력을 남긴다 (공통 명세 §3.7).
 *
 * 초대는 당사자만 본다. 보낸 소유자와 받은 사람이 아니면 없는 초대와 같은 응답이어야 한다 (명세 §2.2.2).
 */
@Service
@Transactional(readOnly = true)
class InviteService(
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val inviteRepository: GroupInviteRepository,
    private val historyRepository: InviteHistoryRepository,
    private val userRepository: UserRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun listPending(groupId: Long, ownerId: Long, pageable: Pageable): PageResponse<PendingInviteResponse> {
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
    fun invite(groupId: Long, ownerId: Long, request: InviteRequest): InviteResult {
        val group = findGroup(groupId)
        requireOwner(group, ownerId)

        // 가입 여부를 숨기지 않는다. 소유자가 오타를 알아차릴 유일한 수단이며, 그 대가는 공통 명세 §7.2에 한계로 적혀 있다.
        val invitee = userRepository.findByEmailIgnoreCase(request.email.trim())
            ?: run {
                // 이메일 자체는 남기지 않는다. 응답에서 가리는 값을 로그가 대신 보관하지 않는다 (공통 명세 §3.1).
                log.debug("초대 실패 groupId={} ownerId={} 사유=가입되지_않은_이메일", groupId, ownerId)
                throw ApiException(ErrorCode.USER_NOT_FOUND)
            }
        val inviteeId = requireNotNull(invitee.id)

        if (groupMemberRepository.existsByGroupIdAndUserId(groupId, inviteeId)) {
            throw ApiException(ErrorCode.ALREADY_MEMBER)
        }

        // 중복 클릭이 실패처럼 보이지 않도록 기존 초대를 그대로 돌려준다. unique(group_id, invitee_id) 가 이를 보장한다.
        inviteRepository.findByGroupIdAndInviteeId(groupId, inviteeId)?.let {
            log.debug("초대 재사용 groupId={} inviteId={} inviteeId={}", groupId, it.id, inviteeId)
            return InviteResult(it.toPendingResponse(), created = false)
        }

        val saved = inviteRepository.save(
            GroupInvite(group = group, invitee = invitee, invitedBy = group.owner),
        )
        log.debug("초대 생성 groupId={} inviteId={} ownerId={} inviteeId={}", groupId, saved.id, ownerId, inviteeId)
        return InviteResult(saved.toPendingResponse(), created = true)
    }

    @Transactional
    fun revoke(groupId: Long, inviteId: Long, ownerId: Long) {
        val invite = findInvite(inviteId)
        // 보낸 소유자가 아니면 그 초대의 존재도 드러내지 않는다. 경로의 그룹과 어긋나는 id 도 같은 응답이다.
        if (invite.group.id != groupId || invite.group.owner.id != ownerId) {
            // 당사자가 아니면 없는 초대와 같은 응답이라(명세 §2.2.2), 막힌 이유는 이 로그에만 남는다.
            log.debug("초대 철회 차단 inviteId={} 요청groupId={} ownerId={}", inviteId, groupId, ownerId)
            throw ApiException(ErrorCode.INVITE_NOT_FOUND)
        }
        resolve(invite, InviteOutcome.REVOKED)
    }

    fun listReceived(userId: Long, pageable: Pageable): PageResponse<ReceivedInviteResponse> =
        PageResponse.of(
            inviteRepository.findByInviteeIdOrderByCreatedAtAsc(userId, pageable).map { it.toReceivedResponse() },
        )

    /** 내가 보낸 대기 초대. 조건이 `invited_by = 나` 라 남의 초대가 섞일 수 없다 (명세 §4.8). */
    fun listSent(userId: Long, pageable: Pageable): PageResponse<SentInviteResponse> =
        PageResponse.of(
            inviteRepository.findByInvitedByIdOrderByCreatedAtAsc(userId, pageable).map { it.toSentResponse() },
        )

    /**
     * 끝난 초대 이력. `role` 이 관점을 고르며, 어느 쪽이든 본인이 당사자인 것만 조회된다.
     *
     * 그룹의 생존 여부는 **페이지 전체의 그룹 id 를 모아 한 번에** 확인한다. 항목마다 조회하면
     * 페이지 크기만큼 쿼리가 늘고, 이력은 지워지지 않아 목록이 계속 길어지는 자리다.
     */
    fun listHistory(
        userId: Long,
        role: InviteHistoryRole,
        pageable: Pageable,
    ): PageResponse<InviteHistoryResponse> {
        val page = when (role) {
            InviteHistoryRole.RECEIVED -> historyRepository.findByInviteeIdOrderByResolvedAtDesc(userId, pageable)
            InviteHistoryRole.SENT -> historyRepository.findByInvitedByIdOrderByResolvedAtDesc(userId, pageable)
        }
        val aliveGroupIds = groupRepository.findAllById(page.content.map { it.groupId })
            .mapNotNull { it.id }
            .toSet()
        log.debug("초대 이력 role={} userId={} 건수={}", role, userId, page.totalElements)
        return PageResponse.of(page.map { it.toResponse(role, it.groupId in aliveGroupIds) })
    }

    /**
     * 초대를 수락해 그룹 멤버가 된다.
     *
     * 정원이 차 있으면 거부하되 **초대 행은 지우지 않는다.** 자리가 난 뒤 같은 초대로 다시 수락할 수
     * 있어야 하기 때문이다 (공통 명세 §3.7).
     */
    @Transactional
    fun accept(inviteId: Long, userId: Long) {
        val invite = requireReceivedInvite(inviteId, userId)
        val groupId = requireNotNull(invite.group.id)

        // 정원 검사와 멤버 입력 사이에 다른 수락이 끼어들지 못하도록 그룹 행을 잠그고 읽는다 (명세 §4.8).
        val group = groupRepository.findByIdForUpdate(groupId) ?: throw ApiException(ErrorCode.INVITE_NOT_FOUND)
        if (groupMemberRepository.countByGroupId(groupId) >= Group.MEMBER_LIMIT) {
            // 초대 행은 남는다. 자리가 나면 같은 초대로 다시 수락한다 (공통 명세 §3.7).
            log.debug("초대 수락 거부 inviteId={} groupId={} userId={} 사유=정원초과", inviteId, groupId, userId)
            throw ApiException(ErrorCode.GROUP_MEMBER_LIMIT_EXCEEDED)
        }

        groupMemberRepository.save(GroupMember(group = group, user = invite.invitee))
        resolve(invite, InviteOutcome.ACCEPTED)
    }

    /**
     * 초대 거절.
     *
     * 거절은 이력에 남고 **보낸 사람도 본다** (공통 명세 §3.7). 감춰도 보낸 목록에서 항목이
     * 사라진 것으로 추론되므로, 절반만 가린 이력을 만들지 않는다. 거절한 상대를 다시 초대하는
     * 것은 그대로 허용된다 — 대기 초대 행이 사라져 유니크 제약이 비기 때문이다.
     */
    @Transactional
    fun reject(inviteId: Long, userId: Long) {
        resolve(requireReceivedInvite(inviteId, userId), InviteOutcome.REJECTED)
    }

    /**
     * 초대를 끝낸다 — 이력을 남기고 대기 행을 지운다.
     *
     * 초대 행을 지우는 경로를 이 하나로 모은다. 지우기만 하고 이력을 빠뜨린 경로가 생기면
     * 이력이 조용히 비고, 그 누락은 조회 시점에 드러나지 않는다 (명세 §3.1).
     */
    private fun resolve(invite: GroupInvite, outcome: InviteOutcome) {
        historyRepository.save(InviteHistory.from(invite, outcome))
        inviteRepository.delete(invite)
        // 네 가지 종료가 모두 이 지점을 지난다. 여기 한 줄이면 끝난 초대를 빠짐없이 따라갈 수 있다.
        log.debug(
            "초대 종료 inviteId={} groupId={} inviteeId={} outcome={}",
            invite.id, invite.group.id, invite.invitee.id, outcome,
        )
    }

    private fun findGroup(groupId: Long): Group =
        groupRepository.findById(groupId).orElseThrow { ApiException(ErrorCode.GROUP_NOT_FOUND) }

    private fun requireOwner(group: Group, userId: Long) {
        if (group.owner.id != userId) {
            log.debug("그룹 소유자 아님 groupId={} userId={} ownerId={}", group.id, userId, group.owner.id)
            throw ApiException(ErrorCode.FORBIDDEN)
        }
    }

    private fun findInvite(inviteId: Long): GroupInvite =
        inviteRepository.findById(inviteId).orElseThrow { ApiException(ErrorCode.INVITE_NOT_FOUND) }

    /** 받은 본인이 아니면 없는 초대와 구분되지 않아야 한다 (명세 §2.2.2). */
    private fun requireReceivedInvite(inviteId: Long, userId: Long): GroupInvite {
        val invite = findInvite(inviteId)
        if (invite.invitee.id != userId) {
            log.debug("초대 접근 차단 inviteId={} userId={} inviteeId={}", inviteId, userId, invite.invitee.id)
            throw ApiException(ErrorCode.INVITE_NOT_FOUND)
        }
        return invite
    }

    private fun GroupInvite.toPendingResponse() = PendingInviteResponse(
        id = requireNotNull(id),
        invitee = invitee.toResponse(),
        createdAt = createdAt,
    )

    private fun GroupInvite.toSentResponse() = SentInviteResponse(
        id = requireNotNull(id),
        group = GroupBriefResponse(id = requireNotNull(group.id), name = group.name),
        invitee = invitee.toResponse(),
        createdAt = createdAt,
    )

    /** 상대는 관점에 따라 갈린다 — 받은 이력이면 보냈던 사람, 보낸 이력이면 초대받았던 사람이다. */
    private fun InviteHistory.toResponse(role: InviteHistoryRole, groupAlive: Boolean) = InviteHistoryResponse(
        id = requireNotNull(id),
        group = HistoryGroupResponse(id = groupId, name = groupName, deleted = !groupAlive),
        counterpart = when (role) {
            InviteHistoryRole.RECEIVED -> invitedBy.toResponse()
            InviteHistoryRole.SENT -> invitee.toResponse()
        },
        outcome = outcome,
        invitedAt = invitedAt,
        resolvedAt = resolvedAt,
    )

    private fun GroupInvite.toReceivedResponse() = ReceivedInviteResponse(
        id = requireNotNull(id),
        group = GroupBriefResponse(id = requireNotNull(group.id), name = group.name),
        invitedBy = invitedBy.toResponse(),
        createdAt = createdAt,
    )
}
