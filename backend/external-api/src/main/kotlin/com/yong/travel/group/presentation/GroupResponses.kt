package com.yong.travel.group.presentation

import com.yong.travel.auth.presentation.UserResponse
import com.yong.travel.group.domain.Group
import com.yong.travel.group.domain.GroupInvite
import com.yong.travel.group.domain.GroupRef
import com.yong.travel.group.domain.InviteHistoryEntry
import com.yong.travel.group.domain.InviteOutcome
import java.time.Instant

/** 목록·기록 응답에 붙는 최소 정보. 멤버 목록은 그룹 상세에서만 내려준다. */
data class GroupSummaryResponse(
    val id: Long,
    val name: String,
    val memo: String?,
    val memberCount: Long,
    val memberLimit: Int,
    val isOwner: Boolean,
) {
    companion object {
        /** 그룹 요약 → 응답. `isOwner` 를 여기서 정하는 이유는 [GroupResponse.from] 과 같다. */
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

data class GroupMemberResponse(
    val id: Long,
    val name: String,
    val profileImageUrl: String?,
    val joinedAt: Instant,
) {
    companion object {
        fun from(member: Group.Member) = GroupMemberResponse(
            id = member.user.id,
            name = member.user.name,
            profileImageUrl = member.user.profileImageUrl,
            joinedAt = member.joinedAt,
        )
    }
}

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
        /**
         * 그룹 상세 → 응답.
         *
         * `isOwner` 는 요청자마다 달라지므로 도메인 객체가 아니라 여기서 정한다. 그래서 `requesterId`
         * 를 받는다 — 서비스가 아니라 컨트롤러가 요청자를 알고 있는 계층이다.
         */
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

/** 받은 초대에 담기는 그룹 정보. 수락 전에는 멤버 목록도 공유된 기록도 보이지 않는다. */
data class GroupBriefResponse(
    val id: Long,
    val name: String,
) {
    companion object {
        fun from(group: GroupRef) = GroupBriefResponse(group.id, group.name)
    }
}

/**
 * 소유자가 보는 대기 중인 초대.
 *
 * 소유자가 직접 입력한 이메일이라도 응답으로 되돌려주지 않는다. 누구에게 보냈는지는 이름과
 * 프로필 사진으로 구분되며, 이메일은 본인 조회 외의 어떤 응답에도 담지 않는다.
 */
data class PendingInviteResponse(
    val id: Long,
    val invitee: UserResponse,
    val createdAt: Instant,
) {
    companion object {
        fun from(invite: GroupInvite) = PendingInviteResponse(
            id = invite.id,
            invitee = UserResponse.from(invite.invitee),
            createdAt = invite.createdAt,
        )
    }
}

/** 받은 초대. 그룹명·초대자·보낸 시각까지가 수락 전에 보여 줄 수 있는 전부다. */
data class ReceivedInviteResponse(
    val id: Long,
    val group: GroupBriefResponse,
    val invitedBy: UserResponse,
    val createdAt: Instant,
) {
    companion object {
        fun from(invite: GroupInvite) = ReceivedInviteResponse(
            id = invite.id,
            group = GroupBriefResponse.from(invite.group),
            invitedBy = UserResponse.from(invite.invitedBy),
            createdAt = invite.createdAt,
        )
    }
}

/**
 * 내가 보낸 대기 초대. 그룹을 가로지르는 목록이라 그룹명이 함께 필요하다.
 *
 * 그룹 상세에서 쓰는 `PendingInviteResponse` 와 나누어 둔다 — 그쪽은 어느 그룹인지가 화면에
 * 이미 드러나 있어 같은 값을 다시 실어 보낼 이유가 없다.
 */
data class SentInviteResponse(
    val id: Long,
    val group: GroupBriefResponse,
    val invitee: UserResponse,
    val createdAt: Instant,
) {
    companion object {
        fun from(invite: GroupInvite) = SentInviteResponse(
            id = invite.id,
            group = GroupBriefResponse.from(invite.group),
            invitee = UserResponse.from(invite.invitee),
            createdAt = invite.createdAt,
        )
    }
}

/** 끝난 시점의 그룹. 삭제되었으면 화면이 링크를 걸지 않도록 `deleted` 로 알린다. */
data class HistoryGroupResponse(
    val id: Long,
    val name: String,
    val deleted: Boolean,
)

/** 끝난 초대 한 건. 받은 관점과 보낸 관점이 같은 모양을 쓴다. */
data class InviteHistoryResponse(
    val id: Long,
    val group: HistoryGroupResponse,
    /**
     * 상대. `role` 에 따라 초대받았던 사람이 되기도, 보냈던 사람이 되기도 한다.
     * 관점마다 필드 이름을 달리하면 화면이 같은 목록을 두 가지 모양으로 다뤄야 한다.
     */
    val counterpart: UserResponse,
    val outcome: InviteOutcome,
    val invitedAt: Instant,
    val resolvedAt: Instant,
) {
    companion object {
        fun from(entry: InviteHistoryEntry) = InviteHistoryResponse(
            id = entry.id,
            group = HistoryGroupResponse(id = entry.group.id, name = entry.group.name, deleted = entry.groupDeleted),
            counterpart = UserResponse.from(entry.counterpart),
            outcome = entry.outcome,
            invitedAt = entry.invitedAt,
            resolvedAt = entry.resolvedAt,
        )
    }
}
