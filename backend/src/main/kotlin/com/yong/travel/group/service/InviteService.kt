package com.yong.travel.group.service

import com.yong.travel.auth.repository.UserRepository
import com.yong.travel.common.error.ApiException
import com.yong.travel.common.error.ErrorCode
import com.yong.travel.group.domain.Group
import com.yong.travel.group.domain.GroupInvite
import com.yong.travel.group.domain.GroupMember
import com.yong.travel.group.dto.InvitePreviewResponse
import com.yong.travel.group.dto.InviteResponse
import com.yong.travel.group.repository.GroupInviteRepository
import com.yong.travel.group.repository.GroupMemberRepository
import com.yong.travel.group.repository.GroupRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64

interface InviteService {
    fun issue(groupId: Long, ownerId: Long): InviteResponse
    fun current(groupId: Long, ownerId: Long): InviteResponse?
    fun revoke(groupId: Long, ownerId: Long)
    fun preview(token: String): InvitePreviewResponse
    fun accept(token: String, userId: Long): Long
}

/**
 * 멤버 추가는 초대 링크로만 한다. 이름·이메일로 가입자를 검색해 추가하는 방식은
 * 전체 가입자를 들여다볼 수 있게 되어 쓰지 않는다.
 */
@Service
@Transactional(readOnly = true)
class InviteServiceImpl(
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val inviteRepository: GroupInviteRepository,
    private val userRepository: UserRepository,
) : InviteService {

    private val random = SecureRandom()

    @Transactional
    override fun issue(groupId: Long, ownerId: Long): InviteResponse {
        val group = findGroup(groupId)
        requireOwner(group, ownerId)
        requireRoom(groupId)

        val token = newToken()
        val expiresAt = Instant.now().plus(GroupInvite.TTL)

        // 그룹당 1건이므로 새로 만들지 않고 기존 행을 갈아 끼운다. 이전 토큰은 이 시점에 무효가 된다.
        val invite = inviteRepository.findByGroupId(groupId)
            ?.apply { reissue(token, expiresAt) }
            ?: GroupInvite(group = group, token = token, expiresAt = expiresAt)

        val saved = inviteRepository.save(invite)
        return InviteResponse(saved.token, saved.expiresAt)
    }

    override fun current(groupId: Long, ownerId: Long): InviteResponse? {
        val group = findGroup(groupId)
        requireOwner(group, ownerId)
        return inviteRepository.findByGroupId(groupId)
            ?.takeUnless { it.isExpired() }
            ?.let { InviteResponse(it.token, it.expiresAt) }
    }

    @Transactional
    override fun revoke(groupId: Long, ownerId: Long) {
        val group = findGroup(groupId)
        requireOwner(group, ownerId)
        inviteRepository.deleteByGroupId(groupId)
    }

    override fun preview(token: String): InvitePreviewResponse {
        val invite = findLiveInvite(token)
        return InvitePreviewResponse(
            groupName = invite.group.name,
            invitedBy = invite.group.owner.name,
            expiresAt = invite.expiresAt,
        )
    }

    /** @return 가입한 그룹 id */
    @Transactional
    override fun accept(token: String, userId: Long): Long {
        val invite = findLiveInvite(token)
        val group = invite.group
        val groupId = requireNotNull(group.id)

        // 이미 멤버인 사용자의 재수락은 오류가 아니다. 링크를 두 번 눌렀다고 실패처럼 보일 이유가 없다.
        if (groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) return groupId

        requireRoom(groupId)
        val user = userRepository.findById(userId).orElseThrow { ApiException(ErrorCode.UNAUTHENTICATED) }
        groupMemberRepository.save(GroupMember(group = group, user = user))
        return groupId
    }

    private fun findGroup(groupId: Long): Group =
        groupRepository.findById(groupId).orElseThrow { ApiException(ErrorCode.GROUP_NOT_FOUND) }

    private fun requireOwner(group: Group, userId: Long) {
        if (group.owner.id != userId) throw ApiException(ErrorCode.FORBIDDEN)
    }

    private fun requireRoom(groupId: Long) {
        if (groupMemberRepository.countByGroupId(groupId) >= Group.MEMBER_LIMIT) {
            throw ApiException(ErrorCode.GROUP_MEMBER_LIMIT_EXCEEDED)
        }
    }

    private fun findLiveInvite(token: String): GroupInvite {
        val invite = inviteRepository.findByToken(token) ?: throw ApiException(ErrorCode.INVITE_NOT_FOUND)
        if (invite.isExpired()) throw ApiException(ErrorCode.INVITE_EXPIRED)
        return invite
    }

    /** 순번이나 UUIDv1 처럼 추측 가능한 값을 쓰면 링크가 곧 열쇠라 의미가 없다. */
    private fun newToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private companion object {
        const val TOKEN_BYTES = 24
    }
}
