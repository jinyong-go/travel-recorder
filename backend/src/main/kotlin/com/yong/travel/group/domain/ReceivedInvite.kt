package com.yong.travel.group.domain

import com.yong.travel.auth.domain.User
import java.time.Instant

/** 내가 받은 대기 초대. 그룹명·초대자·보낸 시각까지가 수락 전에 보여 줄 수 있는 전부다 (공통 명세 §3.7). */
data class ReceivedInvite(
    val id: Long,
    val group: Group,
    val invitedBy: User,
    val createdAt: Instant,
)
