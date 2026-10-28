package com.yong.travel.group.domain

import com.yong.travel.auth.domain.User
import java.time.Instant

/** 끝난 초대 한 건. 그룹이 이미 지워졌으면 [groupDeleted] 가 true 다. */
data class InviteHistoryEntry(
    val id: Long,
    val group: GroupRef,
    val groupDeleted: Boolean,

    /** 상대. 받은 이력이면 보낸 사람, 보낸 이력이면 받은 사람이다. */
    val counterpart: User,

    val outcome: InviteOutcome,
    val invitedAt: Instant,
    val resolvedAt: Instant,
)
