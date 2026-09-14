package com.yong.travel.auth.dto

import com.yong.travel.auth.domain.User

data class UserResponse(
    val id: Long,
    val name: String,
    val email: String,
    val profileImageUrl: String?,
)

fun User.toResponse(): UserResponse =
    UserResponse(
        id = requireNotNull(id),
        name = name,
        email = email,
        profileImageUrl = profileImageUrl,
    )
