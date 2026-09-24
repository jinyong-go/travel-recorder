package com.yong.travel.group.domain

import com.yong.travel.auth.domain.User
import java.time.Instant

/**
 * 끝난 초대 한 건. 받은 관점과 보낸 관점이 같은 모양을 쓴다 (명세 §4.8).
 *
 * [group] 은 끝난 시점의 스냅샷이라 그룹이 이미 지워졌을 수 있다. [groupDeleted] 로 알려
 * 화면이 없는 그룹에 링크를 걸지 않게 한다 (공통 명세 §3.7).
 */
data class InviteHistoryEntry(
    val id: Long,
    val group: Group,
    val groupDeleted: Boolean,

    /** 상대. 받은 이력이면 보냈던 사람, 보낸 이력이면 초대받았던 사람이다. */
    val counterpart: User,

    val outcome: InviteOutcome,
    val invitedAt: Instant,
    val resolvedAt: Instant,
)
