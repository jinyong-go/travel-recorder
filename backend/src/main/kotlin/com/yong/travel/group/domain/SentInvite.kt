package com.yong.travel.group.domain

import com.yong.travel.auth.domain.User
import java.time.Instant

/**
 * 내가 보낸 대기 초대. 그룹을 가로지르는 목록이라 어느 그룹인지가 함께 필요하다 —
 * 그룹 상세에서 쓰는 [PendingInvite] 는 화면에 이미 드러나 있어 그 값을 싣지 않는다.
 */
data class SentInvite(
    val id: Long,
    val group: Group,
    val invitee: User,
    val createdAt: Instant,
)
