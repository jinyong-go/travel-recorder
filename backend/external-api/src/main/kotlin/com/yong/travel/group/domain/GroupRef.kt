package com.yong.travel.group.domain

/** 초대에 실리는 그룹 정보. 수락 전에는 이름만 보인다. */
data class GroupRef(
    val id: Long,
    val name: String,
)
