package com.yong.travel.group.presentation

import com.yong.travel.auth.presentation.UserResponse
import com.yong.travel.auth.presentation.toResponse
import com.yong.travel.group.domain.GroupDetail
import com.yong.travel.group.domain.Group
import com.yong.travel.group.domain.InviteHistoryEntry
import com.yong.travel.group.domain.PendingInvite
import com.yong.travel.group.domain.ReceivedInvite
import com.yong.travel.group.domain.SentInvite
import com.yong.travel.group.domain.GroupSummary
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
)

data class GroupMemberResponse(
    val id: Long,
    val name: String,
    val profileImageUrl: String?,
    val joinedAt: Instant,
)

data class GroupResponse(
    val id: Long,
    val name: String,
    val memo: String?,
    val owner: UserResponse,
    val members: List<GroupMemberResponse>,
    val memberCount: Long,
    val memberLimit: Int,
    val isOwner: Boolean,
)

/** 받은 초대에 담기는 그룹 정보. 수락 전에는 멤버 목록도 공유된 기록도 보이지 않는다 (공통 명세 §3.7). */
data class GroupBriefResponse(
    val id: Long,
    val name: String,
)

/**
 * 소유자가 보는 대기 중인 초대.
 *
 * 소유자가 직접 입력한 이메일이라도 응답으로 되돌려주지 않는다. 누구에게 보냈는지는 이름과
 * 프로필 사진으로 구분되며, 이메일은 본인 조회 외의 어떤 응답에도 담지 않는다 (공통 명세 §3.1, §3.7).
 */
data class PendingInviteResponse(
    val id: Long,
    val invitee: UserResponse,
    val createdAt: Instant,
)

/** 받은 초대. 그룹명·초대자·보낸 시각까지가 수락 전에 보여 줄 수 있는 전부다 (공통 명세 §3.7). */
data class ReceivedInviteResponse(
    val id: Long,
    val group: GroupBriefResponse,
    val invitedBy: UserResponse,
    val createdAt: Instant,
)

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
)

/** 끝난 시점의 그룹. 삭제되었으면 화면이 링크를 걸지 않도록 `deleted` 로 알린다 (공통 명세 §3.7). */
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
     * 관점마다 필드 이름을 달리하면 화면이 같은 목록을 두 가지 모양으로 다뤄야 한다 (명세 §4.8).
     */
    val counterpart: UserResponse,
    val outcome: InviteOutcome,
    val invitedAt: Instant,
    val resolvedAt: Instant,
)

/**
 * 그룹 상세 → 응답.
 *
 * `isOwner` 는 요청자마다 달라지므로 도메인 객체가 아니라 여기서 정한다. 그래서 `requesterId`
 * 를 받는다 — 서비스가 아니라 컨트롤러가 요청자를 알고 있는 계층이다.
 */
fun GroupDetail.toResponse(requesterId: Long): GroupResponse =
    GroupResponse(
        id = id,
        name = name,
        memo = memo,
        owner = owner.user.toResponse(),
        members = members.map {
            GroupMemberResponse(
                id = it.user.id,
                name = it.user.name,
                profileImageUrl = it.user.profileImageUrl,
                joinedAt = it.joinedAt,
            )
        },
        memberCount = memberCount.toLong(),
        memberLimit = memberLimit,
        isOwner = isOwnedBy(requesterId),
    )

/** 그룹 요약 → 응답. `isOwner` 를 여기서 정하는 이유는 [toResponse] 와 같다. */
fun GroupSummary.toSummaryResponse(requesterId: Long): GroupSummaryResponse =
    GroupSummaryResponse(
        id = id,
        name = name,
        memo = memo,
        memberCount = memberCount,
        memberLimit = memberLimit,
        isOwner = isOwnedBy(requesterId),
    )

fun Group.toBriefResponse(): GroupBriefResponse = GroupBriefResponse(id, name)

fun PendingInvite.toResponse(): PendingInviteResponse =
    PendingInviteResponse(id = id, invitee = invitee.toResponse(), createdAt = createdAt)

fun ReceivedInvite.toResponse(): ReceivedInviteResponse =
    ReceivedInviteResponse(
        id = id,
        group = group.toBriefResponse(),
        invitedBy = invitedBy.toResponse(),
        createdAt = createdAt,
    )

fun SentInvite.toResponse(): SentInviteResponse =
    SentInviteResponse(
        id = id,
        group = group.toBriefResponse(),
        invitee = invitee.toResponse(),
        createdAt = createdAt,
    )

fun InviteHistoryEntry.toResponse(): InviteHistoryResponse =
    InviteHistoryResponse(
        id = id,
        group = HistoryGroupResponse(id = group.id, name = group.name, deleted = groupDeleted),
        counterpart = counterpart.toResponse(),
        outcome = outcome,
        invitedAt = invitedAt,
        resolvedAt = resolvedAt,
    )
