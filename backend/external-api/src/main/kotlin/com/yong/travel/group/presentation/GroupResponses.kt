package com.yong.travel.group.presentation

import com.yong.travel.auth.presentation.UserResponse
import com.yong.travel.group.domain.Group
import com.yong.travel.group.domain.GroupInvite
import com.yong.travel.group.domain.GroupRef
import com.yong.travel.group.domain.InviteHistoryEntry
import com.yong.travel.group.domain.InviteOutcome
import java.time.Instant

/** 그룹 목록 항목. 명단 대신 인원수만 담는다. */
data class GroupSummaryResponse(
    val id: Long,
    val name: String,
    val memo: String?,
    val memberCount: Long,
    val memberLimit: Int,
    val isOwner: Boolean,
) {
    companion object {
        /** 그룹 → 응답. */
        fun from(group: Group, requesterId: Long) = GroupSummaryResponse(
            id = group.id,
            name = group.name,
            memo = group.memo,
            memberCount = group.memberCount.toLong(),
            memberLimit = group.memberLimit,
            isOwner = group.isOwnedBy(requesterId),
        )
    }
}

/** 그룹 상세에 담기는 멤버 한 명. */
data class GroupMemberResponse(
    val id: Long,
    val name: String,
    val profileImageUrl: String?,
    val joinedAt: Instant,
) {
    companion object {
        /** 멤버 → 응답. */
        fun from(member: Group.Member) = GroupMemberResponse(
            id = member.user.id,
            name = member.user.name,
            profileImageUrl = member.user.profileImageUrl,
            joinedAt = member.joinedAt,
        )
    }
}

/** 그룹 상세. */
data class GroupResponse(
    val id: Long,
    val name: String,
    val memo: String?,
    val owner: UserResponse,
    val members: List<GroupMemberResponse>,
    val memberCount: Long,
    val memberLimit: Int,
    val isOwner: Boolean,
) {
    companion object {
        /** 그룹 → 응답. */
        fun from(group: Group, requesterId: Long) = GroupResponse(
            id = group.id,
            name = group.name,
            memo = group.memo,
            owner = UserResponse.from(group.owner.user),
            members = group.members.map { GroupMemberResponse.from(it) },
            memberCount = group.memberCount.toLong(),
            memberLimit = group.memberLimit,
            isOwner = group.isOwnedBy(requesterId),
        )
    }
}

/** 그룹의 id 와 이름. 초대와 여행 공유에서 쓴다. */
data class GroupRefResponse(
    val id: Long,
    val name: String,
) {
    companion object {
        /** 그룹 참조 → 응답. */
        fun from(group: GroupRef) = GroupRefResponse(group.id, group.name)
    }
}

/** 소유자가 보는 대기 초대. 이메일은 소유자가 입력했어도 돌려주지 않는다. */
data class PendingInviteResponse(
    val id: Long,
    val invitee: UserResponse,
    val createdAt: Instant,
) {
    companion object {
        /** 대기 초대 → 응답. */
        fun from(invite: GroupInvite) = PendingInviteResponse(
            id = invite.id,
            invitee = UserResponse.from(invite.invitee),
            createdAt = invite.createdAt,
        )
    }
}

/** 받은 초대. 수락 전이라 그룹명·초대자·보낸 시각만 담는다. */
data class ReceivedInviteResponse(
    val id: Long,
    val group: GroupRefResponse,
    val invitedBy: UserResponse,
    val createdAt: Instant,
) {
    companion object {
        /** 대기 초대 → 응답. */
        fun from(invite: GroupInvite) = ReceivedInviteResponse(
            id = invite.id,
            group = GroupRefResponse.from(invite.group),
            invitedBy = UserResponse.from(invite.invitedBy),
            createdAt = invite.createdAt,
        )
    }
}

/** 내가 보낸 대기 초대. 여러 그룹에 걸친 목록이라 그룹명을 함께 담는다. */
data class SentInviteResponse(
    val id: Long,
    val group: GroupRefResponse,
    val invitee: UserResponse,
    val createdAt: Instant,
) {
    companion object {
        /** 대기 초대 → 응답. */
        fun from(invite: GroupInvite) = SentInviteResponse(
            id = invite.id,
            group = GroupRefResponse.from(invite.group),
            invitee = UserResponse.from(invite.invitee),
            createdAt = invite.createdAt,
        )
    }
}

/** 초대 이력의 그룹. 삭제됐는지 함께 알린다. */
data class HistoryGroupResponse(
    val id: Long,
    val name: String,
    val deleted: Boolean,
) {
    companion object {
        /** 이력 항목의 그룹 → 응답. */
        fun from(entry: InviteHistoryEntry) =
            HistoryGroupResponse(id = entry.group.id, name = entry.group.name, deleted = entry.groupDeleted)
    }
}

/** 끝난 초대 한 건. 받은 이력과 보낸 이력이 같은 모양을 쓴다. */
data class InviteHistoryResponse(
    val id: Long,
    val group: HistoryGroupResponse,
    /** 상대. 받은 이력이면 보낸 사람, 보낸 이력이면 받은 사람이다. */
    val counterpart: UserResponse,
    val outcome: InviteOutcome,
    val invitedAt: Instant,
    val resolvedAt: Instant,
) {
    companion object {
        /** 끝난 초대 → 응답. */
        fun from(entry: InviteHistoryEntry) = InviteHistoryResponse(
            id = entry.id,
            group = HistoryGroupResponse.from(entry),
            counterpart = UserResponse.from(entry.counterpart),
            outcome = entry.outcome,
            invitedAt = entry.invitedAt,
            resolvedAt = entry.resolvedAt,
        )
    }
}
