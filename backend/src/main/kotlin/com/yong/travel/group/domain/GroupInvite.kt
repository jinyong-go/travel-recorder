package com.yong.travel.group.domain

import com.yong.travel.auth.domain.User
import java.time.Instant

/**
 * 대기 중인 초대 한 건. 소유자의 그룹별 목록·받은 초대·보낸 초대가 함께 쓴다.
 *
 * 관점마다 보여 줄 필드를 고르는 것은 응답 DTO 의 몫이다 — 받은 초대에는 그룹명·초대자·보낸
 * 시각까지만 나간다 (공통 명세 §3.7). [User] 는 이메일을 담지 않으므로 소유자가 입력한 이메일이
 * 어느 관점에서도 되돌아가지 않는다 (공통 명세 §3.1).
 */
data class GroupInvite(
    val id: Long,
    val group: GroupRef,

    /** 초대받은 사람. 가입자만 지정할 수 있다. */
    val invitee: User,

    /** 보낸 사람 = 초대 시점의 그룹 소유자. */
    val invitedBy: User,

    val createdAt: Instant,
)
