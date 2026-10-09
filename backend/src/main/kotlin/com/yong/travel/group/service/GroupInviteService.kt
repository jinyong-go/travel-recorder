package com.yong.travel.group.service

import com.yong.travel.auth.domain.User
import com.yong.travel.auth.persistence.UserEntity
import com.yong.travel.auth.persistence.UserRepository
import com.yong.travel.common.domain.PageResult
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import com.yong.travel.common.persistence.listPageRequest
import com.yong.travel.common.persistence.toPageResult
import com.yong.travel.group.persistence.GroupEntity
import com.yong.travel.group.persistence.GroupInviteEntity
import com.yong.travel.group.persistence.GroupMemberEntity
import com.yong.travel.group.persistence.InviteHistoryEntity
import com.yong.travel.group.domain.GroupInvite
import com.yong.travel.group.domain.GroupRef
import com.yong.travel.group.domain.InviteHistoryEntry
import com.yong.travel.group.domain.InviteOutcome
import com.yong.travel.group.domain.InviteHistoryRole
import com.yong.travel.group.persistence.GroupInviteRepository
import com.yong.travel.group.persistence.GroupMemberRepository
import com.yong.travel.group.persistence.GroupRepository
import com.yong.travel.group.persistence.InviteHistoryRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 멤버 추가의 유일한 경로. 초대는 받은 사람의 계정에 쌓이고, 수락·거절·철회 어느 쪽으로 끝나든
 * 행을 지우면서 이력을 남긴다.
 *
 * 초대는 당사자만 본다. 보낸 소유자와 받은 사람이 아니면 없는 초대와 같은 응답이어야 한다.
 */
@Service
@Transactional(readOnly = true)
class GroupInviteService(
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val inviteRepository: GroupInviteRepository,
    private val historyRepository: InviteHistoryRepository,
    private val userRepository: UserRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 그 그룹의 대기 중인 초대 목록. 소유자만 볼 수 있다. */
    fun listPending(groupId: Long, ownerId: Long, page: Int): PageResult<GroupInvite> {
        requireOwner(findGroup(groupId), ownerId)
        return inviteRepository.findByGroupIdOrderByCreatedAtAsc(groupId, listPageRequest(page))
            .toPageResult().map { it.toDomain() }
    }

    /**
     * 이메일 완전 일치로 찾은 가입자에게 초대를 보낸다.
     *
     * 이미 멤버면 `ALREADY_MEMBER`, 대기 중인 초대가 있으면 `ALREADY_INVITED` 로 거부한다.
     * 멤버 여부를 먼저 본다.
     *
     * 정원은 여기서 보지 않는다. 대기 중인 초대는 자리를 차지하지 않으므로 정원을 넘겨 보낼 수 있고,
     * 판정은 수락 시점에만 한다.
     */
    @Transactional
    fun invite(groupId: Long, ownerId: Long, email: String): GroupInvite {
        val group = findGroup(groupId)
        requireOwner(group, ownerId)

        // 가입 여부를 숨기지 않는다. 소유자가 오타를 알아차릴 유일한 수단이며, 이메일로 가입 여부를 떠볼 수 있다는 한계를 감수한다.
        val invitee = userRepository.findByEmailIgnoreCase(email.trim())
            ?: run {
                // 이메일 자체는 남기지 않는다. 응답에서 가리는 값을 로그가 대신 보관하지 않는다.
                log.debug("초대 실패 groupId={} ownerId={} 사유=가입되지_않은_이메일", groupId, ownerId)
                throw ApiException(ErrorCode.USER_NOT_FOUND)
            }
        val inviteeId = requireNotNull(invitee.id)

        if (groupMemberRepository.existsByGroupIdAndUserId(groupId, inviteeId)) {
            throw ApiException(ErrorCode.ALREADY_MEMBER)
        }

        // 동시 요청이 이 검사를 함께 통과해도 unique(group_id, invitee_id) 가 두 번째 입력을 막는다.
        inviteRepository.findByGroupIdAndInviteeId(groupId, inviteeId)?.let {
            log.debug("초대 거부 groupId={} inviteId={} inviteeId={} 사유=대기중인_초대", groupId, it.id, inviteeId)
            throw ApiException(ErrorCode.ALREADY_INVITED)
        }

        val saved = inviteRepository.save(
            GroupInviteEntity(group = group, invitee = invitee, invitedBy = group.owner),
        )
        log.debug("초대 생성 groupId={} inviteId={} ownerId={} inviteeId={}", groupId, saved.id, ownerId, inviteeId)
        return saved.toDomain()
    }

    @Transactional
    fun revoke(groupId: Long, inviteId: Long, ownerId: Long) {
        val invite = findInvite(inviteId)
        // 보낸 소유자가 아니면 그 초대의 존재도 드러내지 않는다. 경로의 그룹과 어긋나는 id 도 같은 응답이다.
        if (invite.group.id != groupId || invite.group.owner.id != ownerId) {
            // 당사자가 아니면 없는 초대와 같은 응답이라, 막힌 이유는 이 로그에만 남는다.
            log.debug("초대 철회 차단 inviteId={} 요청groupId={} ownerId={}", inviteId, groupId, ownerId)
            throw ApiException(ErrorCode.INVITE_NOT_FOUND)
        }
        resolve(invite, InviteOutcome.REVOKED)
    }

    /** 내 앞으로 온 대기 초대. */
    fun listReceived(userId: Long, page: Int): PageResult<GroupInvite> =
        inviteRepository.findByInviteeIdOrderByCreatedAtAsc(userId, listPageRequest(page))
            .toPageResult().map { it.toDomain() }

    /** 내가 보낸 대기 초대. 조건이 `invited_by = 나` 라 남의 초대가 섞일 수 없다. */
    fun listSent(userId: Long, page: Int): PageResult<GroupInvite> =
        inviteRepository.findByInvitedByIdOrderByCreatedAtAsc(userId, listPageRequest(page))
            .toPageResult().map { it.toDomain() }

    /**
     * 끝난 초대 이력. `role` 이 관점을 고르며, 어느 쪽이든 본인이 당사자인 것만 조회된다.
     *
     * 그룹의 생존 여부는 **페이지 전체의 그룹 id 를 모아 한 번에** 확인한다. 항목마다 조회하면
     * 페이지 크기만큼 쿼리가 늘고, 이력은 지워지지 않아 목록이 계속 길어지는 자리다.
     */
    fun listHistory(
        userId: Long,
        role: InviteHistoryRole,
        page: Int,
    ): PageResult<InviteHistoryEntry> {
        val pageRequest = listPageRequest(page)
        val entries = when (role) {
            InviteHistoryRole.RECEIVED -> historyRepository.findByInviteeIdOrderByResolvedAtDesc(userId, pageRequest)
            InviteHistoryRole.SENT -> historyRepository.findByInvitedByIdOrderByResolvedAtDesc(userId, pageRequest)
        }
        val aliveGroupIds = groupRepository.findAllById(entries.content.map { it.groupId })
            .mapNotNull { it.id }
            .toSet()
        log.debug("초대 이력 role={} userId={} 건수={}", role, userId, entries.totalElements)
        return entries.toPageResult().map { it.toDomain(role, it.groupId in aliveGroupIds) }
    }

    /**
     * 초대를 수락해 그룹 멤버가 된다.
     *
     * 정원이 차 있으면 거부하되 **초대 행은 지우지 않는다.** 자리가 난 뒤 같은 초대로 다시 수락할 수
     * 있어야 하기 때문이다.
     */
    @Transactional
    fun accept(inviteId: Long, userId: Long) {
        val invite = requireReceivedInvite(inviteId, userId)
        val groupId = requireNotNull(invite.group.id)

        // 정원 검사와 멤버 입력 사이에 다른 수락이 끼어들지 못하도록 그룹 행을 잠그고 읽는다.
        val group = groupRepository.findByIdForUpdate(groupId) ?: throw ApiException(ErrorCode.INVITE_NOT_FOUND)
        if (groupMemberRepository.countByGroupId(groupId) >= GroupEntity.MEMBER_LIMIT) {
            // 초대 행은 남는다. 자리가 나면 같은 초대로 다시 수락한다.
            log.debug("초대 수락 거부 inviteId={} groupId={} userId={} 사유=정원초과", inviteId, groupId, userId)
            throw ApiException(ErrorCode.GROUP_MEMBER_LIMIT_EXCEEDED)
        }

        groupMemberRepository.save(GroupMemberEntity(group = group, user = invite.invitee))
        resolve(invite, InviteOutcome.ACCEPTED)
    }

    /**
     * 초대 거절.
     *
     * 거절은 이력에 남고 **보낸 사람도 본다**. 감춰도 보낸 목록에서 항목이
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
     * 이력이 조용히 비고, 그 누락은 조회 시점에 드러나지 않는다.
     */
    private fun resolve(invite: GroupInviteEntity, outcome: InviteOutcome) {
        historyRepository.save(InviteHistoryEntity.from(invite, outcome))
        inviteRepository.delete(invite)
        // 네 가지 종료가 모두 이 지점을 지난다. 여기 한 줄이면 끝난 초대를 빠짐없이 따라갈 수 있다.
        log.debug(
            "초대 종료 inviteId={} groupId={} inviteeId={} outcome={}",
            invite.id, invite.group.id, invite.invitee.id, outcome,
        )
    }

    private fun findGroup(groupId: Long): GroupEntity =
        groupRepository.findById(groupId)
            .orElseThrow { ApiException(ErrorCode.GROUP_NOT_FOUND) }

    private fun requireOwner(group: GroupEntity, userId: Long) {
        if (group.owner.id != userId) {
            log.debug("그룹 소유자 아님 groupId={} userId={} ownerId={}", group.id, userId, group.owner.id)
            throw ApiException(ErrorCode.FORBIDDEN)
        }
    }

    private fun findInvite(inviteId: Long): GroupInviteEntity =
        inviteRepository.findById(inviteId)
            .orElseThrow { ApiException(ErrorCode.INVITE_NOT_FOUND) }

    /** 받은 본인이 아니면 없는 초대와 구분되지 않아야 한다. */
    private fun requireReceivedInvite(inviteId: Long, userId: Long): GroupInviteEntity {
        val invite = findInvite(inviteId)
        if (invite.invitee.id != userId) {
            log.debug("초대 접근 차단 inviteId={} userId={} inviteeId={}", inviteId, userId, invite.invitee.id)
            throw ApiException(ErrorCode.INVITE_NOT_FOUND)
        }
        return invite
    }

    /** 대기 초대 → 도메인. 어느 관점의 목록이든 같은 모양이며, 보여 줄 필드는 응답 DTO 가 고른다. */
    private fun GroupInviteEntity.toDomain() = GroupInvite(
        id = requireNotNull(id),
        group = group.toDomain(),
        invitee = invitee.toDomain(),
        invitedBy = invitedBy.toDomain(),
        createdAt = createdAt,
    )

    /** 상대는 관점에 따라 갈린다 — 받은 이력이면 보냈던 사람, 보낸 이력이면 초대받았던 사람이다. */
    private fun InviteHistoryEntity.toDomain(role: InviteHistoryRole, groupAlive: Boolean) = InviteHistoryEntry(
        id = requireNotNull(id),
        // 그룹명은 이력에 저장된 스냅샷이다. 그룹이 지워져도 이름이 남아야 하기 때문이다.
        group = GroupRef(id = groupId, name = groupName),
        groupDeleted = !groupAlive,
        counterpart = when (role) {
            InviteHistoryRole.RECEIVED -> invitedBy.toDomain()
            InviteHistoryRole.SENT -> invitee.toDomain()
        },
        outcome = outcome,
        invitedAt = invitedAt,
        resolvedAt = resolvedAt,
    )

    private fun UserEntity.toDomain() = User(requireNotNull(id), name, profileImageUrl)

    private fun GroupEntity.toDomain() = GroupRef(requireNotNull(id), name)
}
