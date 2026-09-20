package com.yong.travel.group.dto

import com.yong.travel.auth.dto.UserResponse
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class GroupRequest(
    @field:NotBlank
    @field:Size(max = 30)
    val name: String,
)

/** 목록·기록 응답에 붙는 최소 정보. 멤버 목록은 그룹 상세에서만 내려준다. */
data class GroupSummaryResponse(
    val id: Long,
    val name: String,
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
