package com.yong.travel.group.service

import com.yong.travel.auth.persistence.UserRepository
import com.yong.travel.auth.persistence.toUser
import com.yong.travel.common.domain.PageResult
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import com.yong.travel.common.persistence.listPageRequest
import com.yong.travel.common.persistence.toPageResult
import com.yong.travel.group.domain.GroupInvite
import com.yong.travel.group.domain.GroupMemberEvent
import com.yong.travel.group.domain.GroupRef
import com.yong.travel.group.domain.InviteHistoryEntry
import com.yong.travel.group.domain.InviteHistoryRole
import com.yong.travel.group.domain.InviteOutcome
import com.yong.travel.group.persistence.GroupEntity
import com.yong.travel.group.persistence.GroupInviteEntity
import com.yong.travel.group.persistence.GroupInviteRepository
import com.yong.travel.group.persistence.GroupMemberEntity
import com.yong.travel.group.persistence.GroupMemberHistoryEntity
import com.yong.travel.group.persistence.GroupMemberHistoryRepository
import com.yong.travel.group.persistence.GroupMemberRepository
import com.yong.travel.group.persistence.GroupRepository
import com.yong.travel.group.persistence.InviteHistoryEntity
import com.yong.travel.group.persistence.InviteHistoryRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 초대 보내기·수락·거절·철회와 초대 목록·이력 조회를 맡는다.
 * 초대는 보낸 사람과 받은 사람만 볼 수 있다.
 */
@Service
@Transactional(readOnly = true)
class GroupInviteService(
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val inviteRepository: GroupInviteRepository,
    private val historyRepository: InviteHistoryRepository,
    private val memberHistoryRepository: GroupMemberHistoryRepository,
    private val userRepository: UserRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 그룹의 대기 초대를 오래된 순으로 조회한다. 소유자만 볼 수 있다.
     *
     * @param page 0부터 시작
     * @throws ApiException `GROUP_NOT_FOUND` — 그룹이 없을 때
     * @throws ApiException `FORBIDDEN` — 소유자가 아닐 때
     */
    fun listPending(groupId: Long, ownerId: Long, page: Int): PageResult<GroupInvite> {
        requireOwner(findGroup(groupId), ownerId)
        return inviteRepository.findByGroupIdOrderByCreatedAtAsc(groupId, listPageRequest(page))
            .toPageResult().map { it.toDomain() }
    }

    /**
     * 이메일로 찾은 가입자에게 초대를 보낸다. 대기 초대가 이미 있으면 그 초대를 돌려준다.
     * 정원은 수락할 때 확인하므로 정원을 넘겨 초대할 수 있다.
     *
     * @throws ApiException `GROUP_NOT_FOUND` — 그룹이 없을 때
     * @throws ApiException `FORBIDDEN` — 소유자가 아닐 때
     * @throws ApiException `USER_NOT_FOUND` — 가입자가 없을 때
     * @throws ApiException `ALREADY_MEMBER` — 이미 멤버일 때
     */
    @Transactional
    fun invite(groupId: Long, ownerId: Long, email: String): InviteResult {
        val group = findGroup(groupId)
        requireOwner(group, ownerId)

        // 소유자가 오타를 알아차릴 수 있도록 가입 여부를 숨기지 않는다.
        val invitee = userRepository.findByEmailIgnoreCase(email.trim())
            ?: run {
                // 이메일은 로그에 남기지 않는다.
                log.debug("초대 실패: 가입되지 않은 이메일 groupId={} ownerId={}", groupId, ownerId)
                throw ApiException(ErrorCode.USER_NOT_FOUND)
            }
        val inviteeId = requireNotNull(invitee.id)

        if (groupMemberRepository.existsByGroupIdAndUserId(groupId, inviteeId)) {
            throw ApiException(ErrorCode.ALREADY_MEMBER)
        }

        // 중복 클릭이 실패처럼 보이지 않도록 기존 초대를 돌려준다.
        inviteRepository.findByGroupIdAndInviteeId(groupId, inviteeId)?.let {
            log.debug("초대 재사용 groupId={} inviteId={} inviteeId={}", groupId, it.id, inviteeId)
            return InviteResult(it.toDomain(), created = false)
        }

        val saved = inviteRepository.save(
            GroupInviteEntity(group = group, invitee = invitee, invitedBy = group.owner),
        )
        log.debug("초대 생성 groupId={} inviteId={} ownerId={} inviteeId={}", groupId, saved.id, ownerId, inviteeId)
        return InviteResult(saved.toDomain(), created = true)
    }

    /**
     * 소유자가 대기 초대를 철회한다.
     * 소유자 확인을 먼저 해서, 소유자가 아니면 초대가 있는지 드러나지 않게 한다.
     *
     * @throws ApiException `GROUP_NOT_FOUND` — 그룹이 없을 때
     * @throws ApiException `FORBIDDEN` — 소유자가 아닐 때
     * @throws ApiException `INVITE_NOT_FOUND` — 초대가 없거나 이 그룹의 초대가 아닐 때
     */
    @Transactional
    fun revoke(groupId: Long, inviteId: Long, ownerId: Long) {
        requireOwner(findGroup(groupId), ownerId)
        val invite = findInvite(inviteId)
        if (invite.group.id != groupId) {
            log.debug("초대 철회 차단: 다른 그룹의 초대 inviteId={} groupId={}", inviteId, groupId)
            throw ApiException(ErrorCode.INVITE_NOT_FOUND)
        }
        resolve(invite, InviteOutcome.REVOKED)
    }

    /**
     * 받은 대기 초대를 오래된 순으로 조회한다.
     *
     * @param page 0부터 시작
     */
    fun listReceived(userId: Long, page: Int): PageResult<GroupInvite> =
        inviteRepository.findByInviteeIdOrderByCreatedAtAsc(userId, listPageRequest(page))
            .toPageResult().map { it.toDomain() }

    /**
     * 내가 보낸 대기 초대를 모든 그룹에 걸쳐 오래된 순으로 조회한다.
     *
     * @param page 0부터 시작
     */
    fun listSent(userId: Long, page: Int): PageResult<GroupInvite> =
        inviteRepository.findByInvitedByIdOrderByCreatedAtAsc(userId, listPageRequest(page))
            .toPageResult().map { it.toDomain() }

    /**
     * 끝난 초대 이력을 최신순으로 조회한다. 그룹이 지워졌는지도 함께 알려 준다.
     *
     * @param page 0부터 시작
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
        return entries.toPageResult().map { it.toDomain(role, it.groupId in aliveGroupIds) }
    }

    /**
     * 초대를 수락해 그룹 멤버가 된다.
     * 상한에 걸려 거부되면 초대는 남겨 두어 나중에 다시 수락할 수 있게 한다.
     *
     * @throws ApiException `INVITE_NOT_FOUND` — 초대가 없거나 받은 사람이 아닐 때
     * @throws ApiException `GROUP_MEMBER_LIMIT_EXCEEDED` — 그룹 정원이 찼을 때
     * @throws ApiException `GROUP_LIMIT_EXCEEDED` — 참여 그룹 상한에 도달했을 때
     */
    @Transactional
    fun accept(inviteId: Long, userId: Long) {
        val invite = requireReceivedInvite(inviteId, userId)
        val groupId = requireNotNull(invite.group.id)

        // 동시에 수락해 정원을 넘지 않도록 그룹을 잠근다.
        val group = groupRepository.findByIdForUpdate(groupId) ?: throw ApiException(ErrorCode.INVITE_NOT_FOUND)
        if (groupMemberRepository.countByGroupId(groupId) >= GroupEntity.MEMBER_LIMIT) {
            log.debug("초대 수락 거부: 정원 초과 inviteId={} groupId={} userId={}", inviteId, groupId, userId)
            throw ApiException(ErrorCode.GROUP_MEMBER_LIMIT_EXCEEDED)
        }
        // 소유 상한처럼 정확히 지킬 필요가 없어 잠그지 않는다.
        if (groupMemberRepository.countJoinedByUserId(userId) >= GroupEntity.JOINED_GROUP_LIMIT) {
            log.debug("초대 수락 거부: 참여 상한 초과 inviteId={} userId={}", inviteId, userId)
            throw ApiException(
                ErrorCode.GROUP_LIMIT_EXCEEDED,
                "참여할 수 있는 그룹은 ${GroupEntity.JOINED_GROUP_LIMIT}개까지입니다.",
            )
        }

        groupMemberRepository.save(GroupMemberEntity(group = group, user = invite.invitee))
        memberHistoryRepository.save(GroupMemberHistoryEntity.of(group, invite.invitee, GroupMemberEvent.JOINED))
        resolve(invite, InviteOutcome.ACCEPTED)
    }

    /**
     * 초대를 거절한다. 숨겨도 목록에서 사라져 알 수 있으므로 보낸 사람도 거절 이력을 본다.
     *
     * @throws ApiException `INVITE_NOT_FOUND` — 초대가 없거나 받은 사람이 아닐 때
     */
    @Transactional
    fun reject(inviteId: Long, userId: Long) {
        resolve(requireReceivedInvite(inviteId, userId), InviteOutcome.REJECTED)
    }

    /**
     * 초대를 끝낸다. 이력을 빠뜨리지 않도록 초대는 늘 이 함수로 지운다.
     */
    private fun resolve(invite: GroupInviteEntity, outcome: InviteOutcome) {
        historyRepository.save(InviteHistoryEntity.from(invite, outcome))
        inviteRepository.delete(invite)
        log.debug(
            "초대 종료 inviteId={} groupId={} inviteeId={} outcome={}",
            invite.id, invite.group.id, invite.invitee.id, outcome,
        )
    }

    /** 그룹을 찾는다. 없으면 `GROUP_NOT_FOUND`. */
    private fun findGroup(groupId: Long): GroupEntity =
        groupRepository.findById(groupId)
            .orElseThrow { ApiException(ErrorCode.GROUP_NOT_FOUND) }

    /** 소유자가 아니면 `FORBIDDEN`. */
    private fun requireOwner(group: GroupEntity, userId: Long) {
        if (group.owner.id != userId) {
            log.debug("그룹 소유자 아님 groupId={} userId={} ownerId={}", group.id, userId, group.owner.id)
            throw ApiException(ErrorCode.FORBIDDEN)
        }
    }

    /** 초대를 찾는다. 당사자인지는 확인하지 않는다. */
    private fun findInvite(inviteId: Long): GroupInviteEntity =
        inviteRepository.findById(inviteId)
            .orElseThrow { ApiException(ErrorCode.INVITE_NOT_FOUND) }

    /** 받은 사람의 초대를 찾는다. 받은 사람이 아니면 없는 초대와 같게 답한다. */
    private fun requireReceivedInvite(inviteId: Long, userId: Long): GroupInviteEntity {
        val invite = findInvite(inviteId)
        if (invite.invitee.id != userId) {
            log.debug("초대 접근 차단 inviteId={} userId={} inviteeId={}", inviteId, userId, invite.invitee.id)
            throw ApiException(ErrorCode.INVITE_NOT_FOUND)
        }
        return invite
    }

    private fun GroupInviteEntity.toDomain() = GroupInvite(
        id = requireNotNull(id),
        group = group.toDomain(),
        invitee = invitee.toUser(),
        invitedBy = invitedBy.toUser(),
        createdAt = createdAt,
    )

    /** 상대는 받은 이력이면 보낸 사람, 보낸 이력이면 받은 사람이다. */
    private fun InviteHistoryEntity.toDomain(role: InviteHistoryRole, groupAlive: Boolean) = InviteHistoryEntry(
        id = requireNotNull(id),
        group = GroupRef(id = groupId, name = groupName),
        groupDeleted = !groupAlive,
        counterpart = when (role) {
            InviteHistoryRole.RECEIVED -> invitedBy.toUser()
            InviteHistoryRole.SENT -> invitee.toUser()
        },
        outcome = outcome,
        invitedAt = invitedAt,
        resolvedAt = resolvedAt,
    )

    private fun GroupEntity.toDomain() = GroupRef(requireNotNull(id), name)
}
