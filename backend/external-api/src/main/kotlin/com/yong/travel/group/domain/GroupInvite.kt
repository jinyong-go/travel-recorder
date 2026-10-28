package com.yong.travel.group.domain

import com.yong.travel.auth.domain.User
import java.time.Instant

/** 대기 중인 초대 한 건. */
data class GroupInvite(
    val id: Long,
    val group: GroupRef,

    /** 초대받은 사람. */
    val invitee: User,

    /** 보낸 사람(그룹 소유자). */
    val invitedBy: User,

    val createdAt: Instant,
)
