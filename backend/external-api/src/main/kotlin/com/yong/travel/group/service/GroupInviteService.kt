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
    private val memberHistoryRepository: GroupMemberHistoryRepository,
    private val userRepository: UserRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 그 그룹의 대기 중인 초대 목록을 조회한다. 소유자만 볼 수 있다.
     *
     * @param ownerId 요청자의 사용자 id. 소유자여야 한다
     * @param page 0부터 시작하는 페이지 번호
     * @return 오래된 순의 대기 초대 한 페이지
     * @throws ApiException `GROUP_NOT_FOUND` — 그룹이 없을 때
     * @throws ApiException `FORBIDDEN` — 요청자가 소유자가 아닐 때
     */
    fun listPending(groupId: Long, ownerId: Long, page: Int): PageResult<GroupInvite> {
        requireOwner(findGroup(groupId), ownerId)
        return inviteRepository.findByGroupIdOrderByCreatedAtAsc(groupId, listPageRequest(page))
            .toPageResult().map { it.toDomain() }
    }

    /**
     * 이메일 완전 일치로 찾은 가입자에게 초대를 보낸다. 이미 대기 중인 초대가 있으면 새로 만들지 않는다.
     *
     * 정원은 여기서 보지 않는다. 대기 중인 초대는 자리를 차지하지 않으므로 정원을 넘겨 보낼 수 있고,
     * 판정은 수락 시점에만 한다.
     *
     * @param ownerId 요청자의 사용자 id. 소유자여야 한다
     * @param email 초대 대상 이메일. 앞뒤 공백을 지우고 대소문자 구분 없이 찾는다
     * @return 초대와 새로 만들었는지 여부. 기존 대기 초대를 돌려줬으면 `created = false`
     * @throws ApiException `GROUP_NOT_FOUND` — 그룹이 없을 때
     * @throws ApiException `FORBIDDEN` — 요청자가 소유자가 아닐 때
     * @throws ApiException `USER_NOT_FOUND` — 그 이메일의 가입자가 없을 때
     * @throws ApiException `ALREADY_MEMBER` — 대상이 이미 그룹 멤버일 때
     */
    @Transactional
    fun invite(groupId: Long, ownerId: Long, email: String): InviteResult {
        val group = findGroup(groupId)
        requireOwner(group, ownerId)

        // 가입 여부를 숨기지 않는다. 소유자가 오타를 알아차릴 유일한 수단이며, 이메일로 가입 여부를 떠볼 수 있다는 한계를 감수한다.
        val invitee = userRepository.findByEmailIgnoreCase(email.trim())
            ?: run {
                // 이메일 자체는 남기지 않는다. 응답에서 가리는 값을 로그가 대신 보관하지 않는다.
                log.debug("초대 실패: 가입되지 않은 이메일 groupId={} ownerId={}", groupId, ownerId)
                throw ApiException(ErrorCode.USER_NOT_FOUND)
            }
        val inviteeId = requireNotNull(invitee.id)

        if (groupMemberRepository.existsByGroupIdAndUserId(groupId, inviteeId)) {
            throw ApiException(ErrorCode.ALREADY_MEMBER)
        }

        // 중복 클릭이 실패처럼 보이지 않도록 기존 초대를 그대로 돌려준다. unique(group_id, invitee_id) 가 이를 보장한다.
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
     * 소유자가 대기 중인 초대를 철회한다.
     *
     * 그룹의 다른 소유자 전용 동작과 같은 관문을 먼저 거친다. 초대는 그 뒤에 찾으므로 소유자가 아닌
     * 사람에게는 `inviteId` 가 무엇이든 같은 403 이라, 초대의 존재가 드러나지 않는다.
     *
     * @param groupId 경로의 그룹 id. 초대의 그룹과 같아야 한다
     * @param ownerId 요청자의 사용자 id. 소유자여야 한다
     * @throws ApiException `GROUP_NOT_FOUND` — 그룹이 없을 때
     * @throws ApiException `FORBIDDEN` — 요청자가 소유자가 아닐 때
     * @throws ApiException `INVITE_NOT_FOUND` — 초대가 없거나 경로의 그룹 초대가 아닐 때
     */
    @Transactional
    fun revoke(groupId: Long, inviteId: Long, ownerId: Long) {
        requireOwner(findGroup(groupId), ownerId)
        val invite = findInvite(inviteId)
        // 다른 그룹의 초대 id 는 이 그룹에 없는 초대와 같은 응답이다.
        if (invite.group.id != groupId) {
            log.debug("초대 철회 차단: 다른 그룹의 초대 inviteId={} groupId={}", inviteId, groupId)
            throw ApiException(ErrorCode.INVITE_NOT_FOUND)
        }
        resolve(invite, InviteOutcome.REVOKED)
    }

    /**
     * 내 앞으로 온 대기 초대를 조회한다.
     *
     * @param page 0부터 시작하는 페이지 번호
     * @return 오래된 순의 받은 초대 한 페이지
     */
    fun listReceived(userId: Long, page: Int): PageResult<GroupInvite> =
        inviteRepository.findByInviteeIdOrderByCreatedAtAsc(userId, listPageRequest(page))
            .toPageResult().map { it.toDomain() }

    /**
     * 내가 보낸 대기 초대를 그룹을 가로질러 조회한다. 조건이 `invited_by = 나` 라 남의 초대가 섞일 수 없다.
     *
     * @param page 0부터 시작하는 페이지 번호
     * @return 오래된 순의 보낸 초대 한 페이지
     */
    fun listSent(userId: Long, page: Int): PageResult<GroupInvite> =
        inviteRepository.findByInvitedByIdOrderByCreatedAtAsc(userId, listPageRequest(page))
            .toPageResult().map { it.toDomain() }

    /**
     * 끝난 초대 이력. `role` 이 관점을 고르며, 어느 쪽이든 본인이 당사자인 것만 조회된다.
     *
     * 그룹의 생존 여부는 **페이지 전체의 그룹 id 를 모아 한 번에** 확인한다. 항목마다 조회하면
     * 페이지 크기만큼 쿼리가 늘고, 이력은 지워지지 않아 목록이 계속 길어지는 자리다.
     *
     * @param page 0부터 시작하는 페이지 번호
     * @return 끝난 시각의 최신순 이력 한 페이지
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
     *
     * 정원이 차 있으면 거부하되 **초대 행은 지우지 않는다.** 자리가 난 뒤 같은 초대로 다시 수락할 수
     * 있어야 하기 때문이다.
     *
     * @param userId 요청자의 사용자 id. 초대받은 본인이어야 한다
     * @throws ApiException `INVITE_NOT_FOUND` — 초대가 없거나 받은 본인이 아닐 때
     * @throws ApiException `GROUP_MEMBER_LIMIT_EXCEEDED` — 그룹 정원이 가득 찼을 때
     * @throws ApiException `GROUP_LIMIT_EXCEEDED` — 요청자가 이미 참여 그룹 상한만큼 속해 있을 때
     */
    @Transactional
    fun accept(inviteId: Long, userId: Long) {
        val invite = requireReceivedInvite(inviteId, userId)
        val groupId = requireNotNull(invite.group.id)

        // 정원 검사와 멤버 입력 사이에 다른 수락이 끼어들지 못하도록 그룹 행을 잠그고 읽는다.
        val group = groupRepository.findByIdForUpdate(groupId) ?: throw ApiException(ErrorCode.INVITE_NOT_FOUND)
        if (groupMemberRepository.countByGroupId(groupId) >= GroupEntity.MEMBER_LIMIT) {
            // 초대 행은 남는다. 자리가 나면 같은 초대로 다시 수락한다.
            log.debug("초대 수락 거부: 정원 초과 inviteId={} groupId={} userId={}", inviteId, groupId, userId)
            throw ApiException(ErrorCode.GROUP_MEMBER_LIMIT_EXCEEDED)
        }
        // 정원과 같이 초대 행은 남고 이력도 생기지 않는다. 다른 그룹에서 나가면 같은 초대로 다시 수락한다.
        // 잠그지 않는 이유는 GroupService.create 의 소유 상한과 같다.
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
     * 초대 거절.
     *
     * 거절은 이력에 남고 **보낸 사람도 본다**. 감춰도 보낸 목록에서 항목이
     * 사라진 것으로 추론되므로, 절반만 가린 이력을 만들지 않는다. 거절한 상대를 다시 초대하는
     * 것은 그대로 허용된다 — 대기 초대 행이 사라져 유니크 제약이 비기 때문이다.
     *
     * @param userId 요청자의 사용자 id. 초대받은 본인이어야 한다
     * @throws ApiException `INVITE_NOT_FOUND` — 초대가 없거나 받은 본인이 아닐 때
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

    /** 그룹 단건 조회. 없으면 GROUP_NOT_FOUND 다. */
    private fun findGroup(groupId: Long): GroupEntity =
        groupRepository.findById(groupId)
            .orElseThrow { ApiException(ErrorCode.GROUP_NOT_FOUND) }

    /** 그룹 소유자만 통과시킨다. 아니면 FORBIDDEN 이다. */
    private fun requireOwner(group: GroupEntity, userId: Long) {
        if (group.owner.id != userId) {
            log.debug("그룹 소유자 아님 groupId={} userId={} ownerId={}", group.id, userId, group.owner.id)
            throw ApiException(ErrorCode.FORBIDDEN)
        }
    }

    /** 초대 단건 조회. 없으면 INVITE_NOT_FOUND 이고, 당사자 판정은 부르는 쪽이 이어서 한다. */
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
        invitee = invitee.toUser(),
        invitedBy = invitedBy.toUser(),
        createdAt = createdAt,
    )

    /** 상대는 관점에 따라 갈린다 — 받은 이력이면 보냈던 사람, 보낸 이력이면 초대받았던 사람이다. */
    private fun InviteHistoryEntity.toDomain(role: InviteHistoryRole, groupAlive: Boolean) = InviteHistoryEntry(
        id = requireNotNull(id),
        // 그룹명은 이력에 저장된 스냅샷이다. 그룹이 지워져도 이름이 남아야 하기 때문이다.
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

    /** 그룹 엔티티 → 초대에 실리는 최소 그룹 정보. */
    private fun GroupEntity.toDomain() = GroupRef(requireNotNull(id), name)
}
