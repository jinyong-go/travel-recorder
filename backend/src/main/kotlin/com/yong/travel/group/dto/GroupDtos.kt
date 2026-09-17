package com.yong.travel.group.dto

import com.yong.travel.auth.dto.UserResponse
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

/** 초대 링크. 토큰만 내려주고 URL 조립은 프론트엔드가 한다. */
data class InviteResponse(
    val token: String,
    val expiresAt: Instant,
)

/**
 * 초대 미리보기. 로그인 전에도 "무슨 그룹 초대인지" 는 보여줘야 해서 비로그인도 조회할 수 있다.
 * 그래서 그룹명·초대자 이름·만료 시각만 담고 멤버 목록이나 기록은 포함하지 않는다.
 */
data class InvitePreviewResponse(
    val groupName: String,
    val invitedBy: String,
    val expiresAt: Instant,
)
