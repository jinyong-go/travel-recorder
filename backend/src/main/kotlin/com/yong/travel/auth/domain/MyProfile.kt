package com.yong.travel.auth.domain

/**
 * 로그인한 본인의 정보.
 *
 * **이메일이 담기는 유일한 자리다** — 타인에게 나가는 [UserRef] 와 나누어 둔 이유가 이것이다.
 * 제공 동의를 받지 못한 계정은 이메일이 null 이다 (공통 명세 §3.1).
 */
data class MyProfile(
    val id: Long,
    val name: String,
    val email: String?,
    val profileImageUrl: String?,
)
