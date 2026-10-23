package com.yong.travel.group.service

import com.yong.travel.auth.persistence.UserRepository
import com.yong.travel.auth.persistence.toUser
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
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

/**
 * 공유 그룹의 생성·수정·삭제와 멤버십 관리. 멤버 추가만은 초대를 거치므로 [GroupInviteService] 의 몫이다.
 *
 * 다른 도메인이 쓰는 판정 재료(속한 그룹 id, 공유 가능한 그룹)도 여기서 내준다.
 */
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
     * 요청자가 소유하거나 속한 그룹 목록을 조회한다.
     *
     * @return 그룹 목록. 멤버 행에서 출발하므로 소유자도 자기 그룹을 여기서 본다. 없으면 빈 목록
     */
    fun list(userId: Long): List<Group> {
        val groups = groupMemberRepository.findGroupsByUserId(userId)
        // 명단은 그룹마다 묻지 않고 한 번에 읽어 그룹 id 로 묶는다.
        val members = membersOf(groups.mapNotNull { it.id })
        return groups.map { it.toDomain(members[requireNotNull(it.id)].orEmpty()) }
    }

    /**
     * 그룹 상세를 멤버 명단과 함께 조회한다.
     *
     * @return 멤버 명단까지 담긴 그룹 상세
     * @throws ApiException `GROUP_NOT_FOUND` — 그룹이 없을 때
     * @throws ApiException `FORBIDDEN` — 그룹은 있으나 요청자가 멤버가 아닐 때
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
     * 그룹을 만들고 만든 사람을 소유자이자 첫 멤버로 넣는다.
     *
     * @param userId 그룹을 만드는 사용자 id. 그대로 소유자가 된다
     * @param name 그룹 이름. 앞뒤 공백을 지워 저장한다
     * @param memo 메모. 공백만 있으면 `null` 로 저장된다
     * @throws ApiException `UNAUTHENTICATED` — 세션은 살아 있는데 사용자 행이 없을 때.
     *         계정이 지워진 뒤의 요청이라 다시 로그인하는 것 말고 할 수 있는 일이 없다
     */
    @Transactional
    fun create(userId: Long, name: String, memo: String?): Group {
        val owner = userRepository.findById(userId)
            .orElseThrow { ApiException(ErrorCode.UNAUTHENTICATED) }
        val group = groupRepository.save(
            GroupEntity(owner = owner, name = name.trim(), memo = memo.trimToNull()),
        )
        // 소유자도 멤버 행을 가진다. 인원 계산과 조회 권한 판정을 한 경로로 모으기 위해서다.
        val ownerMember = groupMemberRepository.save(GroupMemberEntity(group = group, user = owner))
        memberHistoryRepository.save(GroupMemberHistoryEntity.of(group, owner, GroupMemberEvent.JOINED))
        log.debug("그룹 생성 groupId={} ownerId={}", group.id, userId)

        // 방금 만든 멤버가 전부라 다시 읽지 않는다. detailOf 를 쓰면 flush 순서에 기대게 된다.
        return group.toDomain(listOf(ownerMember.toDomain()))
    }

    /**
     * 그룹의 이름과 메모를 바꾼다. 소유자만 할 수 있다.
     *
     * @param name 새 이름. 앞뒤 공백을 지워 저장한다
     * @param memo 새 메모. **이름과 함께 덮어쓰므로 `null` 이면 기존 메모가 지워진다**
     * @throws ApiException `GROUP_NOT_FOUND` — 그룹이 없을 때
     * @throws ApiException `FORBIDDEN` — 요청자가 소유자가 아닐 때
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
     * 그룹과 그에 딸린 멤버·대기 초대·공유 관계를 모두 지운다. 소유자만 할 수 있다.
     *
     * 이 그룹으로만 공유되던 여행은 결과적으로 비공개가 되며, 여행과 하위 기록 자체는 삭제되지 않는다.
     * 대기 중이던 초대는 받는 쪽에서 이유 없이 사라지는 일이라 `GROUP_DELETED` 이력으로 남긴다.
     * 남아 있던 멤버와 공유도 지우기 전에 권한 변경 이력으로 남긴다.
     *
     * @throws ApiException `GROUP_NOT_FOUND` — 그룹이 없을 때
     * @throws ApiException `FORBIDDEN` — 요청자가 소유자가 아닐 때
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
        // 초대 벌크 삭제가 영속성 컨텍스트를 비우므로 이 뒤로 group 은 준영속이다.
        // groupRepository.delete 가 다시 찾아 지운다 (GroupInviteRepository.deleteByGroupId 참고).
        groupInviteRepository.deleteByGroupId(groupId)
        groupMemberRepository.deleteByGroupId(groupId)
        groupRepository.delete(group)
        log.debug(
            "그룹 삭제 groupId={} ownerId={} pendingInvites={} revokedShares={}",
            groupId, userId, pending.size, revokedShares,
        )
    }

    /**
     * 소유자가 다른 멤버를 그룹에서 내보낸다. 그 멤버가 이 그룹에 걸어 둔 여행 공유도 함께 푼다.
     *
     * @param requesterId 요청자의 사용자 id. 소유자여야 한다
     * @throws ApiException `GROUP_NOT_FOUND` — 그룹이 없을 때
     * @throws ApiException `MEMBER_NOT_FOUND` — 지목한 사용자가 이 그룹의 멤버가 아닐 때
     * @throws ApiException `FORBIDDEN` — 요청자가 소유자가 아닐 때
     * @throws ApiException `VALIDATION_ERROR` — 소유자 자신을 지목했을 때.
     *         주인 없는 그룹이 남으므로 허용하지 않는다 — 없애려면 그룹을 삭제해야 한다
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
     * 멤버 본인이 그룹에서 나간다. 내가 이 그룹에 걸어 둔 여행 공유도 함께 푼다.
     *
     * [removeMember] 와 달리 소유자 확인을 하지 않는다. 요청자가 곧 대상이라 남의 멤버십을
     * 건드릴 경로가 없기 때문이다.
     *
     * @throws ApiException `GROUP_NOT_FOUND` — 그룹이 없을 때
     * @throws ApiException `FORBIDDEN` — 요청자가 소유자이거나(나가면 주인 없는 그룹이 남아 삭제만 가능),
     *         그룹은 있으나 멤버가 아닐 때
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
     * 여행을 이 그룹들에 공유해도 되는지 확인하고 그룹 엔티티를 돌려준다.
     *
     * 여기만 도메인 객체가 아니라 엔티티를 반환한다. 컨트롤러로 나가는 경계가 아니라 `TripService`
     * 가 `TripShareEntity` 행을 만들 때 쓰는 서비스 간 호출이라, 도메인 객체로 감싸면 곧바로 엔티티를
     * 다시 찾아야 한다.
     *
     * @param groupIds 공유 대상 그룹 id 목록. 중복은 무시한다
     * @return 요청한 그룹 엔티티 목록. `groupIds` 가 비었으면 빈 목록
     * @throws ApiException `GROUP_NOT_FOUND` — 요청자가 속하지 않은 그룹이 하나라도 섞였을 때.
     *         `FORBIDDEN` 이 아닌 것은 그룹의 존재를 알리지 않기 위해서다 (존재 은닉)
     */
    fun requireAccessibleGroups(groupIds: Collection<Long>, userId: Long): List<GroupEntity> {
        val distinct = groupIds.distinct()
        if (distinct.isEmpty()) return emptyList()

        // (그룹 id, 사용자) 멤버 행은 하나뿐이라, 하나라도 빠졌으면 속하지 않은 그룹이 섞인 것이다.
        val groups = groupMemberRepository.findGroupsByIdInAndUserId(distinct, userId)
        if (groups.size != distinct.size) {
            log.debug("공유 대상 그룹 차단 userId={} requested={} accessible={}", userId, distinct, groups.map { it.id })
            throw ApiException(ErrorCode.GROUP_NOT_FOUND)
        }
        return groups
    }

    /** 그룹 단건 조회. 없으면 GROUP_NOT_FOUND 이고, 열람·수정 권한 판정은 부르는 쪽이 이어서 한다. */
    private fun findGroup(groupId: Long): GroupEntity =
        groupRepository.findById(groupId)
            .orElseThrow { ApiException(ErrorCode.GROUP_NOT_FOUND) }

    /** 소유자만 통과시킨다. update·delete·removeMember 가 같은 관문을 쓴다. */
    private fun requireOwner(group: GroupEntity, userId: Long) {
        if (group.owner.id != userId) {
            log.debug("그룹 소유자 아님 groupId={} userId={} ownerId={}", group.id, userId, group.owner.id)
            throw ApiException(ErrorCode.FORBIDDEN)
        }
    }

    /** 단건 응답. 이 그룹의 명단만 읽어 [toDomain] 에 넘긴다. */
    private fun detailOf(group: GroupEntity): Group {
        val groupId = requireNotNull(group.id)
        return group.toDomain(membersOf(listOf(groupId))[groupId].orEmpty())
    }

    /** 여러 그룹의 명단을 한 번에 읽어 그룹 id 로 묶는다. 가입 순서는 그대로 지켜진다. */
    private fun membersOf(groupIds: List<Long>): Map<Long, List<Group.Member>> {
        if (groupIds.isEmpty()) return emptyMap()
        return groupMemberRepository.findWithUserByGroupIdIn(groupIds)
            .groupBy({ requireNotNull(it.group.id) }) { it.toDomain() }
    }

    /**
     * 그룹 엔티티에 멤버 명단을 붙여 [Group] 으로 만든다. 목록과 단건이 함께 쓴다.
     *
     * 명단은 호출부가 미리 읽어 온다. 응답을 만들면서 리포지토리를 다시 부르지 않기 위해 도메인
     * 객체를 두는 것이므로, 이 함수를 지나면 조회가 끝나 있어야 한다.
     */
    private fun GroupEntity.toDomain(members: List<Group.Member>): Group {
        val groupId = requireNotNull(id)
        val ownerId = requireNotNull(owner.id)
        return Group(
            id = groupId,
            name = name,
            memo = memo,
            // 소유자는 반드시 멤버 행을 갖는다 (create 참고). 없다면 데이터가 깨진 것이라 500 이 맞다.
            owner = members.firstOrNull { it.user.id == ownerId }
                ?: error("그룹 $groupId 의 소유자 멤버 행이 없다"),
            members = members,
            memberLimit = GroupEntity.MEMBER_LIMIT,
        )
    }

    /** 멤버 행 → 도메인. 이름과 프로필 사진까지만 담는다. */
    private fun GroupMemberEntity.toDomain() = Group.Member(
        user = user.toUser(),
        joinedAt = joinedAt,
    )
}
