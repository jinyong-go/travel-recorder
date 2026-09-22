package com.yong.travel.group.dto

import com.yong.travel.auth.dto.UserResponse
import com.yong.travel.group.domain.InviteOutcome
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class GroupRequest(
    @field:NotBlank
    @field:Size(max = 30)
    val name: String,

    /** 생략하면 "메모 없음". PUT 은 이름과 메모를 함께 덮어쓴다 (명세 §4.7). */
    @field:Size(max = 200)
    val memo: String? = null,
)

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

data class InviteRequest(
    @field:NotBlank
    @field:Email
    val email: String,
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

/** 초대 이력을 어느 관점으로 볼지. 어느 쪽이든 본인이 당사자인 것만 조회된다 (명세 §4.8). */
enum class InviteHistoryRole { RECEIVED, SENT }

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
