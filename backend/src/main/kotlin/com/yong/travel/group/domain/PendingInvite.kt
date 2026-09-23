package com.yong.travel.group.domain

import com.yong.travel.auth.domain.UserRef
import java.time.Instant

/**
 * 소유자가 보는 대기 중인 초대 한 건.
 *
 * 받는 사람의 이메일은 담지 않는다. 소유자가 직접 입력한 값이라도 되돌려주지 않으며, 누구에게
 * 보냈는지는 이름과 프로필 사진으로 구분한다 (공통 명세 §3.1, §3.7).
 */
data class PendingInvite(
    val id: Long,
    val invitee: UserRef,
    val createdAt: Instant,
)
