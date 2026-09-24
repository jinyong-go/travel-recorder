package com.yong.travel.tag.domain

/**
 * 자동완성 후보로 나가는 태그 하나.
 *
 * 엔티티 `TagEntity` 와 달리 어느 기록에 쓰였는지를 갖지 않는다 — 후보 목록에 필요한
 * 것은 식별자와 이름뿐이다.
 */
data class Tag(
    val id: Long,
    val name: String,
)
