package com.yong.travel.auth.dto

import com.yong.travel.auth.domain.User

/**
 * 다른 사용자에게도 노출되는 최소 정보. 이메일은 담지 않는다 —
 * 기록의 작성자나 그룹 멤버로 이름·프로필 사진이 보이는 것과, 연락처가 드러나는 것은 다른 이야기다.
 */
data class UserResponse(
    val id: Long,
    val name: String,
    val profileImageUrl: String?,
)

/** 로그인 사용자 본인 정보. 이메일이 포함되는 유일한 응답이다. */
data class MeResponse(
    val id: Long,
    val name: String,
    val email: String,
    val profileImageUrl: String?,
)

fun User.toResponse(): UserResponse =
    UserResponse(
        id = requireNotNull(id),
        name = name,
        profileImageUrl = profileImageUrl,
    )

fun User.toMeResponse(): MeResponse =
    MeResponse(
        id = requireNotNull(id),
        name = name,
        email = email,
        profileImageUrl = profileImageUrl,
    )
