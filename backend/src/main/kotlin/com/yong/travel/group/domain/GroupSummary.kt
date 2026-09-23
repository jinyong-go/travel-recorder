package com.yong.travel.group.domain

/**
 * 그룹 목록의 한 줄. [GroupDetail] 과 달리 멤버 명단 대신 건수만 든다 —
 * 목록에서 남의 이름까지 내려줄 이유가 없다.
 */
data class GroupSummary(
    val id: Long,
    val name: String,
    val memo: String?,
    val ownerId: Long,
    val memberCount: Long,
    val memberLimit: Int,
) {
    fun isOwnedBy(userId: Long): Boolean = ownerId == userId
}
