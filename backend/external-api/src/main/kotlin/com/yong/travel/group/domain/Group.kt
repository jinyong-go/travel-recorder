package com.yong.travel.group.domain

import com.yong.travel.auth.domain.User
import java.time.Instant

/**
 * 그룹 한 건과 그 멤버 명단. 서비스가 조회를 모두 끝낸 결과를 묶어 컨트롤러에 넘기는 경계다.
 *
 * 목록과 상세가 함께 쓴다. 목록 응답은 건수만 내려주지만, 그룹당 최대 5명이라 명단까지 읽어 두는 비용은 작다.
 *
 * **JPA 엔티티가 아니다.** 담긴 값은 조회 시점의 스냅샷이다.
 */
data class Group(
    val id: Long,
    val name: String,
    val memo: String?,

    /** 소유자. 소유자도 멤버 행을 가지므로 [members] 에도 함께 들어 있다. */
    val owner: Member,

    /** 가입 순 멤버 명단. 소유자를 포함한다. */
    val members: List<Member>,

    val memberLimit: Int,
) {
    val memberCount: Int get() = members.size

    /**
     * 요청자가 소유자인지. 요청자마다 달라지는 값이라 저장하지 않고 물어보게 한다.
     *
     * @return 소유자면 true
     */
    fun isOwnedBy(userId: Long): Boolean = owner.user.id == userId

    /** 멤버 한 명 — 누구인지와 언제 들어왔는지. */
    data class Member(
        val user: User,
        val joinedAt: Instant,
    )
}
