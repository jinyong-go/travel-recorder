package com.yong.travel.auth.domain

/**
 * 로그인한 본인의 정보. 이메일은 본인에게만 보이므로 [User] 와 나눴다.
 * 이메일 제공에 동의하지 않았으면 null 이다.
 */
data class MyProfile(
    val id: Long,
    val name: String,
    val email: String?,
    val profileImageUrl: String?,
)
