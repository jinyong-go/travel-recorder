package com.yong.travel.group.domain

import com.yong.travel.auth.domain.User
import java.time.Instant

/** 그룹과 멤버 명단. */
data class Group(
    val id: Long,
    val name: String,
    val memo: String?,

    /** 소유자. [members] 에도 들어 있다. */
    val owner: Member,

    /** 가입 순 멤버 명단. 소유자를 포함한다. */
    val members: List<Member>,

    val memberLimit: Int,
) {
    val memberCount: Int get() = members.size

    /** 요청자가 소유자인지 확인한다. */
    fun isOwnedBy(userId: Long): Boolean = owner.user.id == userId

    /** 멤버와 가입 시각. */
    data class Member(
        val user: User,
        val joinedAt: Instant,
    )
}
