package com.yong.travel.group.domain

/**
 * 초대에 실리는 최소 그룹 정보. 수락 전에는 멤버 명단도 공유된 여행도 보이지 않으므로
 * 이름까지가 전부다 (공통 명세 §3.7).
 */
data class GroupRef(
    val id: Long,
    val name: String,
)
