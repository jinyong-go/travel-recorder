package com.yong.travel.group.service

import com.yong.travel.auth.domain.UserRef
import com.yong.travel.auth.persistence.UserRepository
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import com.yong.travel.group.persistence.Group
import com.yong.travel.group.persistence.GroupMember
import com.yong.travel.group.persistence.InviteHistory
import com.yong.travel.group.domain.GroupDetail
import com.yong.travel.group.domain.GroupSummary
import com.yong.travel.group.domain.InviteOutcome
import com.yong.travel.group.dto.GroupCreateRequest
import com.yong.travel.group.dto.GroupUpdateRequest
import com.yong.travel.group.persistence.GroupInviteRepository
import com.yong.travel.group.persistence.InviteHistoryRepository
import com.yong.travel.group.persistence.GroupMemberRepository
import com.yong.travel.group.persistence.GroupRepository
import com.yong.travel.trip.persistence.TripShareRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class GroupService(
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val groupInviteRepository: GroupInviteRepository,
    private val inviteHistoryRepository: InviteHistoryRepository,
    private val tripShareRepository: TripShareRepository,
    private val userRepository: UserRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 요청자가 소유하거나 속한 그룹 목록을 조회한다.
     *
     * @param userId 조회를 요청한 사용자 id
     * @return 그룹 요약 목록. 멤버 행에서 출발하므로 소유자도 자기 그룹을 여기서 본다. 없으면 빈 목록
     */
    fun list(userId: Long): List<GroupSummary> =
        groupMemberRepository.findGroupsByUserId(userId).map { it.toSummary() }

    /**
     * 그룹 상세를 멤버 목록과 함께 조회한다.
     *
     * @param groupId 조회할 그룹 id
     * @param userId 조회를 요청한 사용자 id
     * @return 멤버 명단까지 담긴 그룹 상세
     * @throws ApiException `GROUP_NOT_FOUND` — 그룹이 없을 때
     * @throws ApiException `FORBIDDEN` — 그룹은 있으나 요청자가 멤버가 아닐 때 (명세 §2.2.2)
     */
    fun get(groupId: Long, userId: Long): GroupDetail {
        val group = findGroup(groupId)
        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            log.debug("그룹 열람 차단 groupId={} userId={}", groupId, userId)
            throw ApiException(ErrorCode.FORBIDDEN)
        }
        return detailOf(group)
    }

    /**
     * 그룹을 만들고 소유자를 첫 멤버로 함께 넣는다.
     *
     * @param userId 그룹을 만드는 사용자 id. 그대로 소유자가 된다
     * @param request 이름과 메모. 메모는 공백만 있으면 `null` 로 저장된다
     * @return 소유자 한 명이 멤버로 들어간 그룹 상세
     * @throws ApiException `UNAUTHENTICATED` — 세션은 살아 있는데 사용자 행이 없을 때.
     *         계정이 지워진 뒤의 요청이라 다시 로그인하는 것 말고 할 수 있는 일이 없다
     */
    @Transactional
    fun create(userId: Long, request: GroupCreateRequest): GroupDetail {
        val owner = userRepository.findById(userId)
            .orElseThrow { ApiException(ErrorCode.UNAUTHENTICATED) }
        val group = groupRepository.save(
            Group(owner = owner, name = request.name.trim(), memo = request.memo.blankToNull()),
        )
        // 소유자도 멤버 행을 가진다. 인원 계산과 조회 권한 판정을 한 경로로 모으기 위해서다.
        val ownerMember = groupMemberRepository.save(GroupMember(group = group, user = owner))
        log.debug("그룹 생성 groupId={} ownerId={}", group.id, userId)

        // 방금 만든 멤버가 전부라 다시 읽지 않는다. detailOf 를 쓰면 flush 순서에 기대게 된다.
        val member = ownerMember.toDomain()
        return GroupDetail(
            id = requireNotNull(group.id),
            name = group.name,
            memo = group.memo,
            owner = member,
            members = listOf(member),
            memberLimit = Group.MEMBER_LIMIT,
        )
    }

    /**
     * 그룹의 이름과 메모를 바꾼다.
     *
     * @param groupId 수정할 그룹 id
     * @param userId 수정을 요청한 사용자 id
     * @param request 이름과 메모. **둘을 함께 덮어쓰므로 메모를 빼고 보내면 기존 메모가 지워진다** (명세 §4.7)
     * @return 수정된 그룹 상세
     * @throws ApiException `GROUP_NOT_FOUND` — 그룹이 없을 때
     * @throws ApiException `FORBIDDEN` — 요청자가 소유자가 아닐 때
     */
    @Transactional
    fun rename(groupId: Long, userId: Long, request: GroupUpdateRequest): GroupDetail {
        val group = findGroup(groupId)
        requireOwner(group, userId)
        group.name = request.name.trim()
        group.memo = request.memo.blankToNull()
        log.debug("그룹 수정 groupId={} ownerId={}", groupId, userId)
        return detailOf(groupRepository.save(group))
    }

    /**
     * 그룹과 그에 딸린 멤버·대기 초대·공유 관계를 모두 지운다.
     *
     * 이 그룹으로만 공유되던 여행은 결과적으로 비공개가 되며, 여행과 하위 기록 자체는 삭제되지 않는다.
     * 대기 중이던 초대는 받는 쪽에서 이유 없이 사라지는 일이라 `GROUP_DELETED` 이력으로 남긴다 (명세 §3.2).
     *
     * @param groupId 삭제할 그룹 id
     * @param userId 삭제를 요청한 사용자 id
     * @throws ApiException `GROUP_NOT_FOUND` — 그룹이 없을 때
     * @throws ApiException `FORBIDDEN` — 요청자가 소유자가 아닐 때
     */
    @Transactional
    fun delete(groupId: Long, userId: Long) {
        val group = findGroup(groupId)
        requireOwner(group, userId)

        tripShareRepository.deleteByGroupId(groupId)
        val pending = groupInviteRepository.findByGroupId(groupId)
        inviteHistoryRepository.saveAll(pending.map { InviteHistory.from(it, InviteOutcome.GROUP_DELETED) })
        log.debug("그룹 삭제 groupId={} ownerId={} 이력으로_넘긴_대기초대={}건", groupId, userId, pending.size)
        groupInviteRepository.deleteByGroupId(groupId)
        groupMemberRepository.deleteByGroupId(groupId)
        groupRepository.delete(group)
    }

    /**
     * 소유자가 다른 멤버를 그룹에서 내보낸다.
     *
     * @param groupId 대상 그룹 id
     * @param targetUserId 내보낼 멤버의 사용자 id
     * @param requesterId 요청자의 사용자 id. 소유자여야 한다
     * @throws ApiException `GROUP_NOT_FOUND` — 그룹이 없거나, 지목한 사용자가 이 그룹의 멤버가 아닐 때
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
            ?: throw ApiException(ErrorCode.GROUP_NOT_FOUND)
        groupMemberRepository.delete(member)
        log.debug("그룹 멤버 제외 groupId={} targetUserId={} requesterId={}", groupId, targetUserId, requesterId)
    }

    /**
     * 멤버 본인이 그룹에서 나간다.
     *
     * [removeMember] 와 달리 소유자 확인을 하지 않는다. 요청자가 곧 대상이라 남의 멤버십을
     * 건드릴 경로가 없기 때문이다.
     *
     * @param groupId 나갈 그룹 id
     * @param userId 나가려는 사용자 id
     * @throws ApiException `GROUP_NOT_FOUND` — 그룹이 없을 때
     * @throws ApiException `FORBIDDEN` — 요청자가 소유자이거나(나가면 주인 없는 그룹이 남아 삭제만 가능),
     *         그룹은 있으나 멤버가 아닐 때 (명세 §2.2.2)
     */
    @Transactional
    fun leave(groupId: Long, userId: Long) {
        val group = findGroup(groupId)
        if (group.owner.id == userId) {
            throw ApiException(ErrorCode.FORBIDDEN, "소유자는 탈퇴할 수 없습니다. 그룹을 삭제해주세요.")
        }
        val member = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
            ?: throw ApiException(ErrorCode.FORBIDDEN, "그룹 멤버가 아닙니다.")
        groupMemberRepository.delete(member)
        log.debug("그룹 탈퇴 groupId={} userId={}", groupId, userId)
    }

    /**
     * 사용자가 속한 그룹 id 목록을 조회한다. 기록·여행 조회 권한 판정의 출발점이라 다른 도메인에서도 쓴다.
     *
     * @param userId 사용자 id. 비로그인 요청을 그대로 넘길 수 있도록 `null` 을 받는다
     * @return 속한 그룹 id 목록. `userId` 가 `null` 이면 빈 목록
     */
    fun groupIdsOf(userId: Long?): List<Long> =
        userId?.let { groupMemberRepository.findGroupIdsByUserId(it) } ?: emptyList()

    /**
     * 여행을 이 그룹들에 공유해도 되는지 확인하고 그룹 엔티티를 돌려준다.
     *
     * 여기만 도메인 객체가 아니라 엔티티를 반환한다. 컨트롤러로 나가는 경계가 아니라 `TripService`
     * 가 `TripShare` 행을 만들 때 쓰는 서비스 간 호출이라, 도메인 객체로 감싸면 곧바로 엔티티를
     * 다시 찾아야 한다.
     *
     * @param groupIds 공유 대상 그룹 id 목록. 중복은 무시한다
     * @param userId 공유를 요청한 사용자 id
     * @return 요청한 그룹 엔티티 목록. `groupIds` 가 비었으면 빈 목록
     * @throws ApiException `GROUP_NOT_FOUND` — 요청자가 속하지 않은 그룹이 하나라도 섞였을 때.
     *         `FORBIDDEN` 이 아닌 것은 그룹의 존재를 알리지 않기 위해서다 (존재 은닉)
     */
    fun requireAccessibleGroups(groupIds: Collection<Long>, userId: Long): List<Group> {
        val distinct = groupIds.distinct()
        if (distinct.isEmpty()) return emptyList()

        val accessible = groupIdsOf(userId).toSet()
        if (!accessible.containsAll(distinct)) {
            log.debug("공유 대상 그룹 차단 userId={} 요청={} 접근가능={}", userId, distinct, accessible)
            throw ApiException(ErrorCode.GROUP_NOT_FOUND)
        }
        return groupRepository.findAllById(distinct)
    }

    // 그룹 단건 조회. 없으면 GROUP_NOT_FOUND 이고, 열람·수정 권한 판정은 부르는 쪽이 이어서 한다.
    private fun findGroup(groupId: Long): Group =
        groupRepository.findById(groupId)
            .orElseThrow { ApiException(ErrorCode.GROUP_NOT_FOUND) }

    // 소유자만 통과시킨다. rename·delete·removeMember 가 같은 관문을 쓴다.
    private fun requireOwner(group: Group, userId: Long) {
        if (group.owner.id != userId) {
            log.debug("그룹 소유자 아님 groupId={} userId={} ownerId={}", group.id, userId, group.owner.id)
            throw ApiException(ErrorCode.FORBIDDEN)
        }
    }

    // 공백만 남은 메모는 "메모 없음" 과 같다. 카드에 빈 줄만 그려지지 않도록 null 로 모은다.
    private fun String?.blankToNull(): String? = this?.trim()?.takeIf { it.isNotBlank() }

    // 목록용. 명단 대신 건수만 센다.
    private fun Group.toSummary() = GroupSummary(
        id = requireNotNull(id),
        name = name,
        memo = memo,
        ownerId = requireNotNull(owner.id),
        memberCount = groupMemberRepository.countByGroupId(requireNotNull(id)),
        memberLimit = Group.MEMBER_LIMIT,
    )

    /**
     * 그룹 엔티티에 멤버 명단을 붙여 [GroupDetail] 로 만든다.
     *
     * 명단 조회가 여기 한 번뿐이라 이후로는 추가 조회가 없다 — 응답을 만들면서 리포지토리를 다시
     * 부르지 않기 위해 도메인 객체를 두는 것이므로, 이 함수를 지나면 조회가 끝나 있어야 한다.
     */
    private fun detailOf(group: Group): GroupDetail {
        val groupId = requireNotNull(group.id)
        val ownerId = requireNotNull(group.owner.id)
        val members = groupMemberRepository.findByGroupIdOrderByJoinedAtAsc(groupId).map { it.toDomain() }
        return GroupDetail(
            id = groupId,
            name = group.name,
            memo = group.memo,
            // 소유자는 반드시 멤버 행을 갖는다 (create 참고). 없다면 데이터가 깨진 것이라 500 이 맞다.
            owner = members.firstOrNull { it.user.id == ownerId }
                ?: error("그룹 ${'$'}groupId 의 소유자 멤버 행이 없다"),
            members = members,
            memberLimit = Group.MEMBER_LIMIT,
        )
    }

    // 멤버 행 → 도메인. 이름과 프로필 사진까지만 담는다 (공통 명세 §3.1).
    private fun GroupMember.toDomain() = GroupDetail.Member(
        user = UserRef(requireNotNull(user.id), user.name, user.profileImageUrl),
        joinedAt = joinedAt,
    )
}
