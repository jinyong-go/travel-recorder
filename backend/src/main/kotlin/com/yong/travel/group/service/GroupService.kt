package com.yong.travel.group.service

import com.yong.travel.auth.dto.toResponse
import com.yong.travel.auth.repository.UserRepository
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import com.yong.travel.group.domain.Group
import com.yong.travel.group.domain.GroupMember
import com.yong.travel.group.domain.InviteHistory
import com.yong.travel.group.domain.InviteOutcome
import com.yong.travel.group.dto.GroupMemberResponse
import com.yong.travel.group.dto.GroupRequest
import com.yong.travel.group.dto.GroupResponse
import com.yong.travel.group.dto.GroupSummaryResponse
import com.yong.travel.group.repository.GroupInviteRepository
import com.yong.travel.group.repository.InviteHistoryRepository
import com.yong.travel.group.repository.GroupMemberRepository
import com.yong.travel.group.repository.GroupRepository
import com.yong.travel.trip.repository.TripShareRepository
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

    fun list(userId: Long): List<GroupSummaryResponse> =
        groupMemberRepository.findGroupsByUserId(userId).map { it.toSummaryResponse(userId) }

    fun get(groupId: Long, userId: Long): GroupResponse {
        val group = findGroup(groupId)
        // 멤버가 아니면 그룹의 존재도 알리지 않는다.
        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            // 존재 은닉 때문에 응답이 "없음"과 같다. 멤버가 아니라 막혔다는 사실은 이 로그에만 남는다.
            log.debug("그룹 열람 차단 groupId={} userId={}", groupId, userId)
            throw ApiException(ErrorCode.GROUP_NOT_FOUND)
        }
        return group.toResponse(userId)
    }

    @Transactional
    fun create(userId: Long, request: GroupRequest): GroupResponse {
        val owner = userRepository.findById(userId).orElseThrow { ApiException(ErrorCode.UNAUTHENTICATED) }
        val group = groupRepository.save(
            Group(owner = owner, name = request.name.trim(), memo = request.memo.blankToNull()),
        )
        // 소유자도 멤버 행을 가진다. 인원 계산과 조회 권한 판정을 한 경로로 모으기 위해서다.
        groupMemberRepository.save(GroupMember(group = group, user = owner))
        log.debug("그룹 생성 groupId={} ownerId={}", group.id, userId)
        return group.toResponse(userId)
    }

    @Transactional
    fun rename(groupId: Long, userId: Long, request: GroupRequest): GroupResponse {
        val group = findGroup(groupId)
        requireOwner(group, userId)
        group.name = request.name.trim()
        group.memo = request.memo.blankToNull()
        log.debug("그룹 수정 groupId={} ownerId={}", groupId, userId)
        return groupRepository.save(group).toResponse(userId)
    }

    /**
     * 그룹을 지우면 멤버·초대·공유 관계가 함께 사라진다.
     * 이 그룹으로만 공유되던 여행은 결과적으로 비공개가 되며, 여행과 하위 기록은 삭제되지 않는다.
     */
    @Transactional
    fun delete(groupId: Long, userId: Long) {
        val group = findGroup(groupId)
        requireOwner(group, userId)

        tripShareRepository.deleteByGroupId(groupId)
        // 받는 쪽에서는 초대가 이유 없이 사라지는 일이라 그 이유를 이력에 남긴다 (명세 §3.2).
        val pending = groupInviteRepository.findByGroupId(groupId)
        inviteHistoryRepository.saveAll(pending.map { InviteHistory.from(it, InviteOutcome.GROUP_DELETED) })
        log.debug("그룹 삭제 groupId={} ownerId={} 이력으로_넘긴_대기초대={}건", groupId, userId, pending.size)
        groupInviteRepository.deleteByGroupId(groupId)
        groupMemberRepository.deleteByGroupId(groupId)
        groupRepository.delete(group)
    }

    @Transactional
    fun removeMember(groupId: Long, targetUserId: Long, requesterId: Long) {
        val group = findGroup(groupId)
        requireOwner(group, requesterId)
        if (group.owner.id == targetUserId) {
            // 소유자를 뺀 그룹은 주인이 없어진다. 없애려면 그룹을 삭제해야 한다.
            throw ApiException(ErrorCode.VALIDATION_ERROR, "소유자는 그룹에서 제외할 수 없습니다.")
        }
        val member = groupMemberRepository.findByGroupIdAndUserId(groupId, targetUserId)
            ?: throw ApiException(ErrorCode.GROUP_NOT_FOUND)
        groupMemberRepository.delete(member)
        log.debug("그룹 멤버 제외 groupId={} targetUserId={} requesterId={}", groupId, targetUserId, requesterId)
    }

    @Transactional
    fun leave(groupId: Long, userId: Long) {
        val group = findGroup(groupId)
        if (group.owner.id == userId) {
            throw ApiException(ErrorCode.FORBIDDEN, "소유자는 탈퇴할 수 없습니다. 그룹을 삭제해주세요.")
        }
        val member = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
            ?: throw ApiException(ErrorCode.GROUP_NOT_FOUND)
        groupMemberRepository.delete(member)
        log.debug("그룹 탈퇴 groupId={} userId={}", groupId, userId)
    }

    /** 사용자가 속한 그룹 id 목록. 기록 조회 권한 판정의 출발점이라 다른 도메인에서도 쓴다. */
    fun groupIdsOf(userId: Long?): List<Long> =
        userId?.let { groupMemberRepository.findGroupIdsByUserId(it) } ?: emptyList()

    /** 기록을 이 그룹들에 공유해도 되는지 확인한다. 속하지 않은 그룹이면 404 (존재 은닉). */
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

    private fun findGroup(groupId: Long): Group =
        groupRepository.findById(groupId).orElseThrow { ApiException(ErrorCode.GROUP_NOT_FOUND) }

    private fun requireOwner(group: Group, userId: Long) {
        if (group.owner.id != userId) {
            log.debug("그룹 소유자 아님 groupId={} userId={} ownerId={}", group.id, userId, group.owner.id)
            throw ApiException(ErrorCode.FORBIDDEN)
        }
    }

    /** 공백만 남은 메모는 "메모 없음" 과 같다. 카드에 빈 줄만 그려지지 않도록 null 로 모은다. */
    private fun String?.blankToNull(): String? = this?.trim()?.takeIf { it.isNotBlank() }

    private fun Group.toSummaryResponse(requesterId: Long) = GroupSummaryResponse(
        id = requireNotNull(id),
        name = name,
        memo = memo,
        memberCount = groupMemberRepository.countByGroupId(requireNotNull(id)),
        memberLimit = Group.MEMBER_LIMIT,
        isOwner = owner.id == requesterId,
    )

    private fun Group.toResponse(requesterId: Long): GroupResponse {
        val groupId = requireNotNull(id)
        val members = groupMemberRepository.findByGroupIdOrderByJoinedAtAsc(groupId).map {
            GroupMemberResponse(
                id = requireNotNull(it.user.id),
                name = it.user.name,
                profileImageUrl = it.user.profileImageUrl,
                joinedAt = it.joinedAt,
            )
        }
        return GroupResponse(
            id = groupId,
            name = name,
            memo = memo,
            owner = owner.toResponse(),
            members = members,
            memberCount = members.size.toLong(),
            memberLimit = Group.MEMBER_LIMIT,
            isOwner = owner.id == requesterId,
        )
    }
}
