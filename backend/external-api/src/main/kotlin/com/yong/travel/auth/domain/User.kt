package com.yong.travel.auth.domain

/** 타인에게 보이는 사용자 정보. 이메일은 담지 않는다. */
data class User(
    val id: Long,
    val name: String,
    val profileImageUrl: String?,
)
