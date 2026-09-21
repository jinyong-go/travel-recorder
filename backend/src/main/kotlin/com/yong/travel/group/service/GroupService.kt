package com.yong.travel.group.service

import com.yong.travel.auth.dto.toResponse
import com.yong.travel.auth.repository.UserRepository
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import com.yong.travel.group.domain.Group
import com.yong.travel.group.domain.GroupMember
import com.yong.travel.group.dto.GroupMemberResponse
import com.yong.travel.group.dto.GroupRequest
import com.yong.travel.group.dto.GroupResponse
import com.yong.travel.group.dto.GroupSummaryResponse
import com.yong.travel.group.repository.GroupInviteRepository
import com.yong.travel.group.repository.GroupMemberRepository
import com.yong.travel.group.repository.GroupRepository
import com.yong.travel.trip.repository.TripShareRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

interface GroupService {
    fun list(userId: Long): List<GroupSummaryResponse>
    fun get(groupId: Long, userId: Long): GroupResponse
    fun create(userId: Long, request: GroupRequest): GroupResponse
    fun rename(groupId: Long, userId: Long, request: GroupRequest): GroupResponse
    fun delete(groupId: Long, userId: Long)
    fun removeMember(groupId: Long, targetUserId: Long, requesterId: Long)
    fun leave(groupId: Long, userId: Long)

    /** 사용자가 속한 그룹 id 목록. 기록 조회 권한 판정의 출발점이라 다른 도메인에서도 쓴다. */
    fun groupIdsOf(userId: Long?): List<Long>

    /** 기록을 이 그룹들에 공유해도 되는지 확인한다. 속하지 않은 그룹이면 404 (존재 은닉). */
    fun requireAccessibleGroups(groupIds: Collection<Long>, userId: Long): List<Group>
}

@Service
@Transactional(readOnly = true)
class GroupServiceImpl(
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val groupInviteRepository: GroupInviteRepository,
    private val tripShareRepository: TripShareRepository,
    private val userRepository: UserRepository,
) : GroupService {

    override fun list(userId: Long): List<GroupSummaryResponse> =
        groupMemberRepository.findGroupsByUserId(userId).map { it.toSummaryResponse(userId) }

    override fun get(groupId: Long, userId: Long): GroupResponse {
        val group = findGroup(groupId)
        // 멤버가 아니면 그룹의 존재도 알리지 않는다.
        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw ApiException(ErrorCode.GROUP_NOT_FOUND)
        }
        return group.toResponse(userId)
    }

    @Transactional
    override fun create(userId: Long, request: GroupRequest): GroupResponse {
        val owner = userRepository.findById(userId).orElseThrow { ApiException(ErrorCode.UNAUTHENTICATED) }
        val group = groupRepository.save(Group(owner = owner, name = request.name.trim()))
        // 소유자도 멤버 행을 가진다. 인원 계산과 조회 권한 판정을 한 경로로 모으기 위해서다.
        groupMemberRepository.save(GroupMember(group = group, user = owner))
        return group.toResponse(userId)
    }

    @Transactional
    override fun rename(groupId: Long, userId: Long, request: GroupRequest): GroupResponse {
        val group = findGroup(groupId)
        requireOwner(group, userId)
        group.name = request.name.trim()
        return groupRepository.save(group).toResponse(userId)
    }

    /**
     * 그룹을 지우면 멤버·초대·공유 관계가 함께 사라진다.
     * 이 그룹으로만 공유되던 여행은 결과적으로 비공개가 되며, 여행과 하위 기록은 삭제되지 않는다.
     */
    @Transactional
    override fun delete(groupId: Long, userId: Long) {
        val group = findGroup(groupId)
        requireOwner(group, userId)

        tripShareRepository.deleteByGroupId(groupId)
        groupInviteRepository.deleteByGroupId(groupId)
        groupMemberRepository.deleteByGroupId(groupId)
        groupRepository.delete(group)
    }

    @Transactional
    override fun removeMember(groupId: Long, targetUserId: Long, requesterId: Long) {
        val group = findGroup(groupId)
        requireOwner(group, requesterId)
        if (group.owner.id == targetUserId) {
            // 소유자를 뺀 그룹은 주인이 없어진다. 없애려면 그룹을 삭제해야 한다.
            throw ApiException(ErrorCode.VALIDATION_ERROR, "소유자는 그룹에서 제외할 수 없습니다.")
        }
        val member = groupMemberRepository.findByGroupIdAndUserId(groupId, targetUserId)
            ?: throw ApiException(ErrorCode.GROUP_NOT_FOUND)
        groupMemberRepository.delete(member)
    }

    @Transactional
    override fun leave(groupId: Long, userId: Long) {
        val group = findGroup(groupId)
        if (group.owner.id == userId) {
            throw ApiException(ErrorCode.FORBIDDEN, "소유자는 탈퇴할 수 없습니다. 그룹을 삭제해주세요.")
        }
        val member = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
            ?: throw ApiException(ErrorCode.GROUP_NOT_FOUND)
        groupMemberRepository.delete(member)
    }

    override fun groupIdsOf(userId: Long?): List<Long> =
        userId?.let { groupMemberRepository.findGroupIdsByUserId(it) } ?: emptyList()

    override fun requireAccessibleGroups(groupIds: Collection<Long>, userId: Long): List<Group> {
        val distinct = groupIds.distinct()
        if (distinct.isEmpty()) return emptyList()

        val accessible = groupIdsOf(userId).toSet()
        if (!accessible.containsAll(distinct)) throw ApiException(ErrorCode.GROUP_NOT_FOUND)
        return groupRepository.findAllById(distinct)
    }

    private fun findGroup(groupId: Long): Group =
        groupRepository.findById(groupId).orElseThrow { ApiException(ErrorCode.GROUP_NOT_FOUND) }

    private fun requireOwner(group: Group, userId: Long) {
        if (group.owner.id != userId) throw ApiException(ErrorCode.FORBIDDEN)
    }

    private fun Group.toSummaryResponse(requesterId: Long) = GroupSummaryResponse(
        id = requireNotNull(id),
        name = name,
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
            owner = owner.toResponse(),
            members = members,
            memberCount = members.size.toLong(),
            memberLimit = Group.MEMBER_LIMIT,
            isOwner = owner.id == requesterId,
        )
    }
}
