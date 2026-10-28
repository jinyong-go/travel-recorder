package com.yong.travel.auth.presentation

import com.yong.travel.auth.domain.MyProfile
import com.yong.travel.auth.domain.User

/** 다른 사용자에게도 보이는 정보. 이메일은 담지 않는다. */
data class UserResponse(
    val id: Long,
    val name: String,
    val profileImageUrl: String?,
) {
    companion object {
        /** 사용자 → 응답. */
        fun from(user: User) = UserResponse(user.id, user.name, user.profileImageUrl)
    }
}

/** 본인 정보. 이메일을 담는 유일한 응답이다. */
data class MeResponse(
    val id: Long,
    val name: String,
    val email: String?,
    val profileImageUrl: String?,
) {
    companion object {
        /** 본인 정보 → 응답. */
        fun from(profile: MyProfile) = MeResponse(profile.id, profile.name, profile.email, profile.profileImageUrl)
    }
}

/** 세션 상태. 사용자 정보는 담지 않는다. */
data class SessionResponse(
    val authenticated: Boolean,
    val csrfToken: String,
)
