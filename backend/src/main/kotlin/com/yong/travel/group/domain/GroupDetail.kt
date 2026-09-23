package com.yong.travel.group.domain

import com.yong.travel.auth.domain.UserRef
import java.time.Instant

/**
 * 그룹 한 건과 그 멤버 명단. 서비스가 조회를 모두 끝낸 결과를 묶어 컨트롤러에 넘기는 경계다.
 *
 * **JPA 엔티티가 아니다.** 영속성 컨텍스트에 붙어 있지 않고, 담긴 값은 조회 시점의 스냅샷이다.
 * 이 객체를 만든 뒤에는 추가 조회가 일어나지 않는다 — 필요한 것은 서비스가 미리 다 읽는다.
 *
 * [Member] 를 중첩해 둔 것은 엔티티 `GroupMember`(persistence) 와 단순 이름이 부딪히지 않게
 * 하기 위해서다. `GroupDetail.Member` 로 읽으면 어느 쪽인지도 분명해진다.
 */
data class GroupDetail(
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

    /** 요청자가 소유자인지. 요청자마다 달라지는 값이라 저장하지 않고 물어보게 한다. */
    fun isOwnedBy(userId: Long): Boolean = owner.user.id == userId

    /** 멤버 한 명 — 누구인지와 언제 들어왔는지. */
    data class Member(
        val user: UserRef,
        val joinedAt: Instant,
    )
}
