package com.yong.travel.group.service

import com.yong.travel.auth.persistence.UserRepository
import com.yong.travel.auth.persistence.toUser
import com.yong.travel.common.domain.PageResult
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import com.yong.travel.common.persistence.listPageRequest
import com.yong.travel.common.persistence.toPageResult
import com.yong.travel.common.util.trimToNull
import com.yong.travel.group.domain.Group
import com.yong.travel.group.domain.GroupMemberEvent
import com.yong.travel.group.domain.InviteOutcome
import com.yong.travel.group.persistence.GroupEntity
import com.yong.travel.group.persistence.GroupInviteRepository
import com.yong.travel.group.persistence.GroupMemberEntity
import com.yong.travel.group.persistence.GroupMemberHistoryEntity
import com.yong.travel.group.persistence.GroupMemberHistoryRepository
import com.yong.travel.group.persistence.GroupMemberRepository
import com.yong.travel.group.persistence.GroupRepository
import com.yong.travel.group.persistence.InviteHistoryEntity
import com.yong.travel.group.persistence.InviteHistoryRepository
import com.yong.travel.trip.domain.TripVisibilityChangeCause
import com.yong.travel.trip.service.TripVisibilityHistoryService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 그룹의 조회·생성·수정·삭제와 탈퇴·제외를 맡는다. */
@Service
@Transactional(readOnly = true)
class GroupService(
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val groupInviteRepository: GroupInviteRepository,
    private val inviteHistoryRepository: InviteHistoryRepository,
    private val memberHistoryRepository: GroupMemberHistoryRepository,
    private val tripVisibilityHistoryService: TripVisibilityHistoryService,
    private val userRepository: UserRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 요청자가 속한 그룹을 이름순으로 조회한다.
     *
     * @param page 0부터 시작
     */
    fun list(userId: Long, page: Int): PageResult<Group> {
        val groups = groupMemberRepository.findGroupsByUserId(userId, listPageRequest(page))
        val members = membersOf(groups.content.mapNotNull { it.id })
        return groups.toPageResult().map { it.toDomain(members[requireNotNull(it.id)].orEmpty()) }
    }

    /**
     * 그룹을 멤버 명단과 함께 조회한다.
     *
     * @throws ApiException `GROUP_NOT_FOUND` — 그룹이 없을 때
     * @throws ApiException `FORBIDDEN` — 멤버가 아닐 때
     */
    fun get(groupId: Long, userId: Long): Group {
        val group = findGroup(groupId)
        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            log.debug("그룹 열람 차단 groupId={} userId={}", groupId, userId)
            throw ApiException(ErrorCode.FORBIDDEN)
        }
        return detailOf(group)
    }

    /**
     * 그룹을 만든다. 만든 사람이 소유자이자 첫 멤버가 된다.
     *
     * @throws ApiException `UNAUTHENTICATED` — 세션의 사용자가 없을 때
     * @throws ApiException `GROUP_LIMIT_EXCEEDED` — 소유 그룹 상한에 도달했을 때
     */
    @Transactional
    fun create(userId: Long, name: String, memo: String?): Group {
        val owner = userRepository.findById(userId)
            .orElseThrow { ApiException(ErrorCode.UNAUTHENTICATED) }
        // 정원과 달리 정확히 지킬 필요가 없어 잠그지 않는다. 동시 요청이면 한 건 넘칠 수 있다.
        if (groupRepository.countByOwnerId(userId) >= GroupEntity.OWNED_GROUP_LIMIT) {
            log.debug("그룹 생성 거부: 소유 상한 초과 userId={}", userId)
            throw ApiException(
                ErrorCode.GROUP_LIMIT_EXCEEDED,
                "만들 수 있는 그룹은 ${GroupEntity.OWNED_GROUP_LIMIT}개까지입니다.",
            )
        }
        val group = groupRepository.save(
            GroupEntity(owner = owner, name = name.trim(), memo = memo.trimToNull()),
        )
        // 인원 계산과 권한 판정을 같은 방식으로 하도록 소유자도 멤버로 넣는다.
        val ownerMember = groupMemberRepository.save(GroupMemberEntity(group = group, user = owner))
        memberHistoryRepository.save(GroupMemberHistoryEntity.of(group, owner, GroupMemberEvent.JOINED))
        log.debug("그룹 생성 groupId={} ownerId={}", group.id, userId)

        return group.toDomain(listOf(ownerMember.toDomain()))
    }

    /**
     * 그룹의 이름과 메모를 바꾼다. 소유자만 할 수 있다.
     *
     * @param memo null 이면 메모를 지운다
     * @throws ApiException `GROUP_NOT_FOUND` — 그룹이 없을 때
     * @throws ApiException `FORBIDDEN` — 소유자가 아닐 때
     */
    @Transactional
    fun update(groupId: Long, userId: Long, name: String, memo: String?): Group {
        val group = findGroup(groupId)
        requireOwner(group, userId)
        group.name = name.trim()
        group.memo = memo.trimToNull()
        val saved = groupRepository.save(group)
        log.debug("그룹 수정 groupId={} ownerId={}", groupId, userId)
        return detailOf(saved)
    }

    /**
     * 그룹을 멤버·대기 초대·여행 공유와 함께 지운다. 소유자만 할 수 있다.
     * 여행은 지우지 않고, 지워지는 멤버십·초대·공유는 이력으로 남긴다.
     *
     * @throws ApiException `GROUP_NOT_FOUND` — 그룹이 없을 때
     * @throws ApiException `FORBIDDEN` — 소유자가 아닐 때
     */
    @Transactional
    fun delete(groupId: Long, userId: Long) {
        val group = findGroup(groupId)
        requireOwner(group, userId)

        val revokedShares = tripVisibilityHistoryService.revokeGroupShares(groupId)
        val members = groupMemberRepository.findWithUserByGroupIdIn(listOf(groupId))
        memberHistoryRepository.saveAll(
            members.map { GroupMemberHistoryEntity.of(group, it.user, GroupMemberEvent.GROUP_DELETED) },
        )
        val pending = groupInviteRepository.findByGroupId(groupId)
        inviteHistoryRepository.saveAll(pending.map { InviteHistoryEntity.from(it, InviteOutcome.GROUP_DELETED) })
        groupInviteRepository.deleteByGroupId(groupId)
        groupMemberRepository.deleteByGroupId(groupId)
        groupRepository.delete(group)
        log.debug(
            "그룹 삭제 groupId={} ownerId={} pendingInvites={} revokedShares={}",
            groupId, userId, pending.size, revokedShares,
        )
    }

    /**
     * 소유자가 멤버를 내보낸다. 그 멤버가 이 그룹에 공유한 여행도 공유를 푼다.
     *
     * @throws ApiException `GROUP_NOT_FOUND` — 그룹이 없을 때
     * @throws ApiException `MEMBER_NOT_FOUND` — 대상이 멤버가 아닐 때
     * @throws ApiException `FORBIDDEN` — 소유자가 아닐 때
     * @throws ApiException `VALIDATION_ERROR` — 소유자 자신을 지목했을 때
     */
    @Transactional
    fun removeMember(groupId: Long, targetUserId: Long, requesterId: Long) {
        val group = findGroup(groupId)
        requireOwner(group, requesterId)
        if (group.owner.id == targetUserId) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "소유자는 그룹에서 제외할 수 없습니다.")
        }
        val member = groupMemberRepository.findByGroupIdAndUserId(groupId, targetUserId)
            ?: throw ApiException(ErrorCode.MEMBER_NOT_FOUND)
        groupMemberRepository.delete(member)
        memberHistoryRepository.save(GroupMemberHistoryEntity.of(group, member.user, GroupMemberEvent.REMOVED))
        val revokedShares = tripVisibilityHistoryService.revokeOwnerShares(
            groupId, targetUserId, TripVisibilityChangeCause.OWNER_REMOVED,
        )
        log.debug(
            "그룹 멤버 제외 groupId={} targetUserId={} requesterId={} revokedShares={}",
            groupId, targetUserId, requesterId, revokedShares,
        )
    }

    /**
     * 그룹에서 탈퇴한다. 내가 이 그룹에 공유한 여행도 공유를 푼다.
     *
     * @throws ApiException `GROUP_NOT_FOUND` — 그룹이 없을 때
     * @throws ApiException `FORBIDDEN` — 소유자이거나 멤버가 아닐 때
     */
    @Transactional
    fun leave(groupId: Long, userId: Long) {
        val group = findGroup(groupId)
        if (group.owner.id == userId) {
            log.debug("그룹 탈퇴 차단: 소유자 groupId={} userId={}", groupId, userId)
            throw ApiException(ErrorCode.FORBIDDEN, "소유자는 탈퇴할 수 없습니다. 그룹을 삭제해주세요.")
        }
        val member = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
            ?: run {
                log.debug("그룹 탈퇴 차단: 비멤버 groupId={} userId={}", groupId, userId)
                throw ApiException(ErrorCode.FORBIDDEN, "그룹 멤버가 아닙니다.")
            }
        groupMemberRepository.delete(member)
        memberHistoryRepository.save(GroupMemberHistoryEntity.of(group, member.user, GroupMemberEvent.LEFT))
        val revokedShares = tripVisibilityHistoryService.revokeOwnerShares(
            groupId, userId, TripVisibilityChangeCause.OWNER_LEFT,
        )
        log.debug("그룹 탈퇴 groupId={} userId={} revokedShares={}", groupId, userId, revokedShares)
    }

    /**
     * 여행을 공유할 그룹에 요청자가 모두 속해 있는지 확인하고 그룹을 돌려준다.
     *
     * @throws ApiException `GROUP_NOT_FOUND` — 속하지 않은 그룹이 섞였을 때. 남의 그룹이 있는지 드러나지 않도록 404 로 답한다
     */
    fun requireAccessibleGroups(groupIds: Collection<Long>, userId: Long): List<GroupEntity> {
        val distinct = groupIds.distinct()
        if (distinct.isEmpty()) return emptyList()

        // 하나라도 빠졌으면 속하지 않은 그룹이 섞인 것이다.
        val groups = groupMemberRepository.findGroupsByIdInAndUserId(distinct, userId)
        if (groups.size != distinct.size) {
            log.debug("공유 대상 그룹 차단 userId={} requested={} accessible={}", userId, distinct, groups.map { it.id })
            throw ApiException(ErrorCode.GROUP_NOT_FOUND)
        }
        return groups
    }

    /** 그룹을 찾는다. 권한은 확인하지 않는다. */
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

    /** 그룹을 명단과 함께 도메인 객체로 만든다. */
    private fun detailOf(group: GroupEntity): Group {
        val groupId = requireNotNull(group.id)
        return group.toDomain(membersOf(listOf(groupId))[groupId].orEmpty())
    }

    /** 여러 그룹의 명단을 가입 순으로 읽어 그룹 id 별로 묶는다. */
    private fun membersOf(groupIds: List<Long>): Map<Long, List<Group.Member>> {
        if (groupIds.isEmpty()) return emptyMap()
        return groupMemberRepository.findWithUserByGroupIdIn(groupIds)
            .groupBy({ requireNotNull(it.group.id) }) { it.toDomain() }
    }

    /** 그룹에 명단을 붙여 도메인 객체로 만든다. */
    private fun GroupEntity.toDomain(members: List<Group.Member>): Group {
        val groupId = requireNotNull(id)
        val ownerId = requireNotNull(owner.id)
        return Group(
            id = groupId,
            name = name,
            memo = memo,
            // 소유자는 반드시 멤버다. 없으면 데이터가 깨진 것이다.
            owner = members.firstOrNull { it.user.id == ownerId }
                ?: error("그룹 $groupId 의 소유자 멤버 행이 없다"),
            members = members,
            memberLimit = GroupEntity.MEMBER_LIMIT,
        )
    }

    private fun GroupMemberEntity.toDomain() = Group.Member(
        user = user.toUser(),
        joinedAt = joinedAt,
    )
}
